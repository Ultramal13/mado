package app.mado.ui

import android.content.SharedPreferences
import android.hardware.GeomagneticField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mado.R
import app.mado.optics.OrientationMath
import app.mado.optics.SunMath
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Estado del Sol: si está activo, dónde se calcula (una lectura de GPS o coordenadas escritas) y para qué momento
 * (ahora o una fecha y hora elegidas para planificar). Solo se guarda lo que conviene recordar: activado y último lugar.
 */
class SunState(private val prefs: SharedPreferences) {
    var on by mutableStateOf(prefs.getBoolean("sun_on", false))
        private set
    var lat by mutableStateOf(prefs.getString("sun_lat", null)?.toDoubleOrNull())
        private set
    var lon by mutableStateOf(prefs.getString("sun_lon", null)?.toDoubleOrNull())
        private set
    var fromGps by mutableStateOf(prefs.getBoolean("sun_gps", false))
        private set
    /** Sube cada vez que el lugar viene del GPS: los campos de coordenadas se reescriben con ese valor. */
    var gpsStamp by mutableIntStateOf(0)
        private set
    /** null = «ahora»; si no, el momento elegido en la hora local del teléfono. */
    var picked by mutableStateOf<LocalDateTime?>(null)
        private set
    var locating by mutableStateOf(false)
    var locError by mutableStateOf(false)
    var showCoords by mutableStateOf(false)

    /** Ajuste del rumbo (grados) medido apuntando al sol real; vale solo para esta sesión y este lugar. */
    var headingOffset by mutableStateOf(0.0)
        private set

    val located get() = lat != null && lon != null
    val zone: ZoneId get() = ZoneId.systemDefault()

    /** Rumbo del sensor con el ajuste de la calibración aplicado (0..360). */
    fun corrected(headingDeg: Double) = ((headingDeg + headingOffset) % 360.0 + 360.0) % 360.0
    /** El sol real está en el centro de la imagen: el rumbo esperado ([sunAzimuthMagDeg]) menos el que midió el sensor es el ajuste. */
    fun calibrate(sunAzimuthMagDeg: Double, sensorHeadingDeg: Double) {
        headingOffset = ((sunAzimuthMagDeg - sensorHeadingDeg + 540.0) % 360.0) - 180.0
    }
    fun clearCalibration() { headingOffset = 0.0 }

    fun enable(v: Boolean) { on = v; prefs.edit().putBoolean("sun_on", v).apply() }

    fun setLocation(latitude: Double, longitude: Double, gps: Boolean) {
        lat = latitude; lon = longitude; fromGps = gps; locError = false
        if (gps) gpsStamp++
        prefs.edit().putString("sun_lat", latitude.toString()).putString("sun_lon", longitude.toString()).putBoolean("sun_gps", gps).apply()
    }

    fun typeLat(v: Double) = setLocation(v, lon ?: 0.0, false)
    fun typeLon(v: Double) = setLocation(lat ?: 0.0, v, false)

    fun localTime(): LocalDateTime = picked ?: LocalDateTime.now(zone)
    fun instant(): Instant = picked?.atZone(zone)?.toInstant() ?: Instant.now()
    fun shiftMinutes(m: Long) { picked = localTime().plusMinutes(m) }
    fun shiftDays(d: Long) { picked = localTime().plusDays(d) }
    fun backToNow() { picked = null }
    fun backToToday() { picked = picked?.let { LocalDateTime.of(java.time.LocalDate.now(zone), it.toLocalTime()) } }

    fun position(): SunMath.Position? {
        val la = lat ?: return null; val lo = lon ?: return null
        return SunMath.position(la, lo, instant())
    }

    /** Declinación magnética del lugar (grados, este positivo): lleva el azimut verdadero del sol a la brújula del teléfono. */
    fun declination(): Double {
        val la = lat ?: return 0.0; val lo = lon ?: return 0.0
        return GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
    }
}

private val sunYellow = Color(0xFFFFC83D)

/** Lo que el visor sabe de la cámara para dibujar y calcular el sol: orientación, campo visible en cada eje y mitad del encuadre actual. */
internal data class SunView(val pose: OrientationMath.Pose?, val halfW: Double, val halfH: Double, val frameW: Double = 1.0, val frameH: Double = 1.0)

/** Intervalo del día (local) en que el sol cruza el encuadre actual, según hacia dónde apunta la cámara; se recalcula al girar un grado. */
@Composable
private fun rememberSunWindow(sun: SunState, v: SunView): Pair<Instant, Instant>? {
    val lat = sun.lat; val lon = sun.lon; val pose = v.pose
    if (lat == null || lon == null || pose == null) return null
    val date = sun.localTime().toLocalDate()
    val h = sun.corrected(pose.headingDeg).roundToInt()
    val e = pose.elevationDeg.roundToInt()
    val decl = remember(lat, lon) { sun.declination() }
    return remember(lat, lon, date, h, e, v.halfW, v.halfH, v.frameW, v.frameH, decl) {
        SunMath.frameWindow(lat, lon, date, sun.zone, decl, h.toDouble(), e.toDouble(), v.halfW, v.halfH, v.frameW, v.frameH)
    }
}

/** Sección del panel de opciones: activar el sol, elegir lugar, fecha y hora, y ver posición y horas clave. */
@Composable
internal fun SunSection(sun: SunState, onLocate: () -> Unit, view: SunView? = null) {
    val p = LocalPalette.current
    Label(stringResource(R.string.sun_label))
    Seg(listOf(stringResource(R.string.opt_no), stringResource(R.string.opt_yes)), if (sun.on) 1 else 0) { v ->
        sun.enable(v == 1)
        if (v == 1 && !sun.located && !sun.locating) onLocate()
    }
    if (!sun.on) return
    if (sun.locating) Text(stringResource(R.string.sun_locating), style = mono(11, p.text))
    if (sun.locError) Text(stringResource(R.string.sun_loc_error), style = mono(10, p.accent))
    Seg(listOf(stringResource(R.string.sun_use_gps), stringResource(R.string.sun_coords)), if (sun.showCoords) 1 else -1) {
        if (it == 0) onLocate() else sun.showCoords = !sun.showCoords
    }
    if (sun.showCoords || !sun.located) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Label(stringResource(R.string.sun_lat))
                CoordField(sun.lat, sun.gpsStamp, -90.0..90.0) { sun.typeLat(it) }
            }
            Column {
                Label(stringResource(R.string.sun_lon))
                CoordField(sun.lon, sun.gpsStamp, -180.0..180.0) { sun.typeLon(it) }
            }
        }
    }
    val lat = sun.lat; val lon = sun.lon
    if (lat == null || lon == null) return
    val source = stringResource(if (sun.fromGps) R.string.sun_src_gps else R.string.sun_src_manual)
    Text(String.format(Locale.ROOT, "%.4f, %.4f · %s", lat, lon, source), style = mono(10, p.text.copy(alpha = 0.7f)))

    val local = sun.localTime()
    val zone = sun.zone
    val hm = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Label(stringResource(R.string.sun_date))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepKey("−1d", true) { sun.shiftDays(-1) }
        Text(local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = mono(13, p.text, true))
        StepKey("+1d", true) { sun.shiftDays(1) }
        StepKey(stringResource(R.string.sun_today), sun.picked != null) { sun.backToToday() }
    }
    Label(stringResource(R.string.sun_time))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepKey("−1h", true) { sun.shiftMinutes(-60) }
        StepKey("−15", true) { sun.shiftMinutes(-15) }
        Text(local.format(hm), style = mono(15, p.text, true), modifier = Modifier.width(54.dp))
        StepKey("+15", true) { sun.shiftMinutes(15) }
        StepKey("+1h", true) { sun.shiftMinutes(60) }
        StepKey(stringResource(R.string.sun_now), sun.picked != null) { sun.backToNow() }
    }

    val compass = stringArrayResource(R.array.compass_points)
    val pos = SunMath.position(lat, lon, sun.instant())
    val az = pos.azimuthDeg
    val decl = remember(lat, lon) { sun.declination() }
    Text(
        stringResource(R.string.sun_pos, az.roundToInt() % 360, compass[OrientationMath.compassIndex(az)], pos.elevationDeg.roundToInt()),
        style = mono(12, p.text, true), modifier = Modifier.padding(top = 4.dp),
    )
    Text(stringResource(R.string.sun_pos_mag, SunMath.trueToMagnetic(az, decl).roundToInt() % 360), style = mono(10, p.text.copy(alpha = 0.7f)))
    if (pos.elevationDeg < 0) Text(stringResource(R.string.sun_below), style = mono(10, p.accent))

    val date = local.toLocalDate()
    val day = remember(lat, lon, date) { SunMath.day(lat, lon, date, zone) }
    fun t(i: Instant?) = i?.atZone(zone)?.format(hm)
    val none = stringResource(R.string.sun_none)
    fun range(a: Instant?, b: Instant?) = if (a == null || b == null) none else "${t(a)}–${t(b)}"
    Label(stringResource(R.string.sun_times))
    @Composable fun line(label: String, value: String) = Text("$label  $value", style = mono(11, p.text))
    line(stringResource(R.string.sun_sunrise), t(day.sunrise) ?: none)
    line(stringResource(R.string.sun_golden_am), range(day.sunrise, day.goldenMorningEnd))
    line(stringResource(R.string.sun_noon), t(day.solarNoon).orEmpty())
    line(stringResource(R.string.sun_golden_pm), range(day.goldenEveningStart, day.sunset))
    line(stringResource(R.string.sun_sunset), t(day.sunset) ?: none)
    line(stringResource(R.string.sun_blue_am), range(day.blueMorningStart, day.blueMorningEnd))
    line(stringResource(R.string.sun_blue_pm), range(day.blueEveningStart, day.blueEveningEnd))
    // ¿A qué hora llega el sol al encuadre? Depende de hacia dónde apunta la cámara ahora.
    if (view != null && view.pose != null) {
        val w = rememberSunWindow(sun, view)
        Text(
            if (w == null) stringResource(R.string.sun_not_in_frame) else stringResource(R.string.sun_in_frame, t(w.first).orEmpty(), t(w.second).orEmpty()),
            style = mono(12, if (w == null) p.text else p.accent, true), modifier = Modifier.padding(top = 6.dp),
        )
        // Calibrar la brújula con el sol real: solo con el sol alto y la hora en «ahora».
        val canCalibrate = sun.picked == null && pos.elevationDeg > 3.0
        Label(stringResource(R.string.sun_calibrate_title))
        Text(stringResource(R.string.sun_calibrate_help), style = mono(10, p.text.copy(alpha = 0.7f)))
        if (!canCalibrate) Text(stringResource(R.string.sun_calibrate_need), style = mono(10, p.accent))
        Seg(
            listOfNotNull(stringResource(R.string.sun_calibrate), if (sun.headingOffset != 0.0) stringResource(R.string.sun_calibrate_clear) else null), -1,
        ) { i ->
            if (i == 0 && canCalibrate) sun.calibrate(SunMath.trueToMagnetic(az, decl), view.pose.headingDeg) else if (i == 1) sun.clearCalibration()
        }
        if (sun.headingOffset != 0.0) Text(stringResource(R.string.sun_calibrated, sun.headingOffset), style = mono(10, p.text))
    }
    Text(stringResource(R.string.sun_accuracy), style = mono(10, p.text.copy(alpha = 0.6f)), modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CoordField(current: Double?, stamp: Int, range: ClosedFloatingPointRange<Double>, onValue: (Double) -> Unit) {
    val p = LocalPalette.current
    // El texto se reescribe solo cuando el valor llega del GPS; al escribir a mano no se pisa lo que va tecleando el usuario.
    var text by remember(stamp) { mutableStateOf(current?.let { String.format(Locale.ROOT, "%.5f", it) }.orEmpty()) }
    BasicTextField(
        text,
        { raw ->
            val f = raw.filter { it.isDigit() || it == '.' || it == '-' }.take(10)
            text = f
            f.toDoubleOrNull()?.takeIf { it in range }?.let(onValue)
        },
        textStyle = mono(15, p.keyText, true), cursorBrush = SolidColor(p.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        modifier = Modifier.width(130.dp).clip(RoundedCornerShape(7.dp)).background(p.keyBg).padding(10.dp),
    )
}

/**
 * El sol sobre la imagen: el arco de su recorrido del día con una marca por hora, un círculo con rayos si ahora cae dentro del campo
 * del teléfono (o una flecha en el borde hacia donde girar), y arriba la ventana horaria en que cruza el encuadre actual.
 * Usa el rumbo magnético del sensor, con el ajuste de calibración y la declinación del lugar; supone el teléfono derecho.
 */
@Composable
internal fun SunOverlay(sun: SunState, view: SunView, imageScale: Float) {
    val lat = sun.lat; val lon = sun.lon; val pose = view.pose
    if (!sun.on || lat == null || lon == null || pose == null) return
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val decl = remember(lat, lon) { sun.declination() }
    val heading = sun.corrected(pose.headingDeg)
    val instant = sun.instant()
    val sp = SunMath.position(lat, lon, instant)
    val azMag = SunMath.trueToMagnetic(sp.azimuthDeg, decl)
    val proj = SunMath.project(azMag, sp.elevationDeg, heading, pose.elevationDeg, view.halfW, view.halfH)
    val dist = SunMath.angularDistanceDeg(azMag, sp.elevationDeg, heading, pose.elevationDeg)
    val col = if (p.red) p.screenText else sunYellow
    val behindText = stringResource(R.string.sun_behind)
    val belowText = stringResource(R.string.sun_below)
    val below = sp.elevationDeg < 0
    // Recorrido del día: se calcula una vez por lugar y fecha; las marcas por hora salen de los puntos en hora redonda.
    val date = sun.localTime().toLocalDate()
    val zone = sun.zone
    val arc = remember(lat, lon, date) {
        SunMath.path(lat, lon, date, zone, 10).map { smp ->
            val lt = smp.at.atZone(zone)
            ArcPoint(smp.at, smp.azimuthDeg, smp.elevationDeg, if (lt.minute == 0) lt.hour else null)
        }
    }
    val window = rememberSunWindow(sun, view)
    val hm = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val windowText = if (window == null) stringResource(R.string.sun_not_in_frame)
        else stringResource(R.string.sun_in_frame, window.first.atZone(zone).format(hm), window.second.atZone(zone).format(hm))
    val hourFmt = stringResource(R.string.sun_hour_tick)
    val past = sun.picked == null
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val iw = size.width * imageScale
        val ih = size.height * imageScale
        fun at(x: Double, y: Double) = Offset(c.x + (x * iw / 2).toFloat(), c.y - (y * ih / 2).toFloat())
        val style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
        val tickStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = col)
        fun label(text: String, pos: Offset) {
            val l = measurer.measure(text, style)
            val pad = 4.dp.toPx()
            val tl = Offset(pos.x.coerceIn(0f, maxOf(0f, size.width - l.size.width - 2 * pad)), pos.y.coerceIn(0f, maxOf(0f, size.height - l.size.height - pad)))
            drawRect(col, tl, Size(l.size.width + 2 * pad, l.size.height + pad))
            drawText(l, topLeft = Offset(tl.x + pad, tl.y + pad / 2))
        }
        // Arco del recorrido: tramos entre puntos consecutivos que están delante de la cámara.
        var prev: Offset? = null
        for (pt in arc) {
            val pr = SunMath.project(SunMath.trueToMagnetic(pt.az, decl), pt.el, heading, pose.elevationDeg, view.halfW, view.halfH)
            val ok = pr.inFront && abs(pr.x) < 8.0 && abs(pr.y) < 8.0
            val o = if (ok) at(pr.x, pr.y) else null
            val faded = past && pt.at < instant
            val a = if (faded) 0.4f else 0.95f
            if (o != null && prev != null) drawLine(col.copy(alpha = a), prev, o, 2.5.dp.toPx())
            if (o != null && pt.hour != null && abs(pr.x) <= 1.05 && abs(pr.y) <= 1.05) {
                drawCircle(col.copy(alpha = a), 4.dp.toPx(), o)
                val l = measurer.measure(String.format(Locale.ROOT, hourFmt, pt.hour), tickStyle)
                drawText(l, topLeft = Offset(o.x + 7.dp.toPx(), o.y - l.size.height / 2f))
            }
            prev = o
        }
        // Dónde está el sol ahora.
        if (proj.inside) {
            val pos = at(proj.x, proj.y)
            val r = 12.dp.toPx()
            drawCircle(col, r, pos, style = Stroke(3.dp.toPx()))
            drawCircle(col, 3.5.dp.toPx(), pos)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(col, pos + d * (r + 3.dp.toPx()), pos + d * (r + 9.dp.toPx()), 2.5.dp.toPx())
            }
            label(String.format(Locale.ROOT, "%d° · %+d°", sp.azimuthDeg.roundToInt() % 360, sp.elevationDeg.roundToInt()) + if (below) " · $belowText" else "",
                Offset(pos.x + r + 12.dp.toPx(), pos.y - 8.dp.toPx()))
        } else {
            var dx = proj.x; var dy = proj.y
            if (abs(dx) < 1e-6 && abs(dy) < 1e-6) dx = 1.0
            val m = max(abs(dx), abs(dy))
            val ex = dx / m; val ey = dy / m
            val pos = at(ex * 0.9, ey * 0.9)
            val ang = atan2(-ey, ex).toFloat()                       // ángulo en pantalla (y hacia abajo)
            val dir = Offset(cos(ang), sin(ang)); val side = Offset(-dir.y, dir.x)
            val s = 14.dp.toPx()
            val path = Path().apply {
                moveTo((pos + dir * s).x, (pos + dir * s).y)
                lineTo((pos - dir * s + side * s).x, (pos - dir * s + side * s).y)
                lineTo((pos - dir * s * 0.4f).x, (pos - dir * s * 0.4f).y)
                lineTo((pos - dir * s - side * s).x, (pos - dir * s - side * s).y)
                close()
            }
            drawPath(path, col)
            val txt = String.format(Locale.ROOT, "☀ %d°", dist.roundToInt()) + (if (!proj.inFront) " · $behindText" else "") + (if (below) " · $belowText" else "")
            label(txt, Offset(pos.x - 40.dp.toPx() - dir.x * 30.dp.toPx(), pos.y + 18.dp.toPx() * (if (dir.y >= 0) -1.6f else 1f)))
        }
        // Ventana horaria en el encuadre actual, arriba a la izquierda.
        label("☀ $windowText", Offset(8.dp.toPx(), 8.dp.toPx()))
    }
}

private data class ArcPoint(val at: Instant, val az: Double, val el: Double, val hour: Int?)
