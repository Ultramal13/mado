package app.mado.ui

import android.content.SharedPreferences
import android.hardware.GeomagneticField
import app.mado.optics.CalibrationMemory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import app.mado.data.SunArc
import app.mado.data.SunNote
import app.mado.data.SunPoint
import app.mado.optics.OrientationMath
import app.mado.optics.SunMath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    /** Dibuja también los recorridos de los solsticios y del equinoccio. */
    var seasons by mutableStateOf(prefs.getBoolean("sun_seasons", false))
        private set
    fun enableSeasons(v: Boolean) { seasons = v; prefs.edit().putBoolean("sun_seasons", v).apply() }

    /** Ajustes de rumbo y elevación (grados) medidos tocando el sol real. Se recuerdan hasta 24 h y en el mismo lugar (el sesgo de la brújula es del teléfono). */
    var headingOffset by mutableStateOf(0.0)
        private set
    var elevationOffset by mutableStateOf(0.0)
        private set
    init {
        val savedAt = prefs.getLong("sun_cal_at", 0L)
        if (CalibrationMemory.shouldRestore(savedAt, System.currentTimeMillis(), prefs.getString("sun_cal_lat", null)?.toDoubleOrNull(), prefs.getString("sun_cal_lon", null)?.toDoubleOrNull(), lat, lon)) {
            headingOffset = prefs.getFloat("sun_cal_h", 0f).toDouble(); elevationOffset = prefs.getFloat("sun_cal_e", 0f).toDouble()
        } else if (savedAt != 0L) prefs.edit().remove("sun_cal_at").apply()
    }
    /** Esperando que el usuario toque el sol real en la imagen. */
    var calibrating by mutableStateOf(false)

    val located get() = lat != null && lon != null
    val zone: ZoneId get() = ZoneId.systemDefault()

    /** Rumbo del sensor con el ajuste de la calibración aplicado (0..360). */
    fun corrected(headingDeg: Double) = ((headingDeg + headingOffset) % 360.0 + 360.0) % 360.0
    fun correctedElev(elevationDeg: Double) = elevationDeg + elevationOffset
    /** Tras tocar el sol real: el eje de la cámara era ([newHeading], [newElev]) y el sensor leyó ([sensorHeading], [sensorElev]). */
    fun calibrateTo(sensorHeading: Double, sensorElev: Double, newHeading: Double, newElev: Double, remember: Boolean = true) {
        headingOffset = ((newHeading - sensorHeading + 540.0) % 360.0) - 180.0
        elevationOffset = newElev - sensorElev
        // Con la brújula alterada el ajuste sirve para esta toma (hacia donde apuntas) pero no se recuerda: el error cambia con la dirección.
        if (remember) prefs.edit().putFloat("sun_cal_h", headingOffset.toFloat()).putFloat("sun_cal_e", elevationOffset.toFloat()).putLong("sun_cal_at", System.currentTimeMillis())
            .putString("sun_cal_lat", lat?.toString()).putString("sun_cal_lon", lon?.toString()).apply()
    }
    fun clearCalibration() {
        headingOffset = 0.0; elevationOffset = 0.0; calibrating = false
        prefs.edit().remove("sun_cal_at").remove("sun_cal_h").remove("sun_cal_e").apply()
    }

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
    /** Fija la hora del día [minuteOfDay] en la fecha [date] (la línea de tiempo del visor). */
    fun setTime(date: LocalDate, minuteOfDay: Int) { picked = LocalDateTime.of(date, LocalTime.of(minuteOfDay.coerceIn(0, 1439) / 60, minuteOfDay.coerceIn(0, 1439) % 60)) }
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
internal data class SunView(
    val pose: OrientationMath.Pose?, val halfW: Double, val halfH: Double, val frameW: Double = 1.0, val frameH: Double = 1.0,
    /** Calidad del sensor magnético según Android (0..3) y error de rumbo estimado, si el teléfono lo informa. */
    val compassAccuracy: Int = 3, val headingErrorDeg: Double? = null,
    /** El campo medido no es el de la Tierra en este lugar, o el rumbo saltó sin que el teléfono girara (cable, imán, metal). */
    val compassDisturbed: Boolean = false,
    /** El campo se aparta de lo normal (acero, autos, hormigón): el rumbo puede estar 10–15° corrido. */
    val compassImprecise: Boolean = false,
    /** Para la calibración propia de la brújula (girar el teléfono): estado, progreso y botones. */
    val tracker: app.mado.camera.OrientationTracker? = null,
) {
    val compassUnreliable get() = compassDisturbed || compassImprecise || compassAccuracy <= 1 || (headingErrorDeg ?: 0.0) > 15.0
}

/** Intervalo del día (local) en que el sol cruza el encuadre actual, según hacia dónde apunta la cámara; se recalcula al girar un grado. */
@Composable
private fun rememberSunWindow(sun: SunState, v: SunView): Pair<Instant, Instant>? {
    val lat = sun.lat; val lon = sun.lon; val pose = v.pose
    if (lat == null || lon == null || pose == null) return null
    val date = sun.localTime().toLocalDate()
    val h = sun.corrected(pose.headingDeg).roundToInt()
    val e = sun.correctedElev(pose.elevationDeg).roundToInt()
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

    Label(stringResource(R.string.sun_seasons))
    Seg(listOf(stringResource(R.string.opt_no), stringResource(R.string.opt_yes)), if (sun.seasons) 1 else 0) { sun.enableSeasons(it == 1) }

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
        // Calibrar la brújula con el sol real: solo con el sol alto y la hora en «ahora». La tecla ◎ del visor inicia el toque.
        val canCalibrate = sun.picked == null && pos.elevationDeg > 3.0
        Label(stringResource(R.string.sun_calibrate_title))
        Text(stringResource(R.string.sun_calibrate_help), style = mono(10, p.text.copy(alpha = 0.7f)))
        if (!canCalibrate) Text(stringResource(R.string.sun_calibrate_need), style = mono(10, p.accent))
        if (view.compassUnreliable) Text(stringResource(if (view.compassDisturbed) R.string.sun_compass_disturbed else if (view.compassImprecise) R.string.sun_compass_imprecise else R.string.sun_compass_warn), style = mono(10, p.accent))
        view.tracker?.let { MagCalibrationControls(it) }
        if (sun.headingOffset != 0.0 || sun.elevationOffset != 0.0) {
            Text(stringResource(R.string.sun_calibrated, sun.headingOffset, sun.elevationOffset), style = mono(10, p.text))
            Seg(listOf(stringResource(R.string.sun_calibrate_clear)), -1) { sun.clearCalibration() }
        }
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

/** Un punto del recorrido del sol sin proyectar (azimut verdadero y elevación) y el recorrido al que pertenece. */
internal data class RawPt(val at: Instant, val az: Double, val el: Double, val hour: Int?)
internal data class RawPath(val color: Int, val label: String?, val hours: Boolean, val samples: List<RawPt>)

/** El recorrido de hoy (con horas) y, si se pide, los de los solsticios (21 jun, 21 dic) y el equinoccio (20 mar) del mismo año, en tonos apagados. */
internal fun computeRawPaths(lat: Double, lon: Double, date: LocalDate, zone: ZoneId, seasons: Boolean): List<RawPath> {
    fun raw(d: LocalDate) = SunMath.path(lat, lon, d, zone, 10).map { smp ->
        val lt = smp.at.atZone(zone)
        RawPt(smp.at, smp.azimuthDeg, smp.elevationDeg, if (lt.minute == 0) lt.hour else null)
    }
    val out = ArrayList<RawPath>()
    if (seasons) {
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        for ((m, d, c) in listOf(Triple(6, 21, 0xFFFF6B6B.toInt()), Triple(3, 20, 0xFF69C779.toInt()), Triple(12, 21, 0xFF3DD0E0.toInt()))) {
            val day = LocalDate.of(date.year, m, d)
            out.add(RawPath(c, day.format(fmt), false, raw(day)))
        }
    }
    out.add(RawPath(0xFFFFC83D.toInt(), null, true, raw(date)))          // el de hoy al final: queda encima
    return out
}

/** Proyecta los recorridos a la imagen: null donde el punto queda detrás de la cámara o demasiado lejos para dibujarse. */
internal fun projectPaths(raw: List<RawPath>, decl: Double, heading: Double, elev: Double, halfW: Double, halfH: Double, fadeBefore: Instant?, rollDeg: Double = 0.0): List<SunArc> =
    raw.map { rp ->
        SunArc(rp.color, rp.samples.map { smp ->
            val pr = SunMath.project(SunMath.trueToMagnetic(smp.az, decl), smp.el, heading, elev, halfW, halfH, rollDeg)
            if (pr.inFront && abs(pr.x) < 8.0 && abs(pr.y) < 8.0) SunPoint(pr.x, pr.y, smp.hour, fadeBefore != null && rp.hours && smp.at < fadeBefore) else null
        }, rp.label, rp.hours)
    }

/** Lo que va a las fotos guardadas: el sol dibujado ([note]) y sus datos para el registro ([meta]). */
internal class SunShot(val note: SunNote, val meta: org.json.JSONObject)

/**
 * El sol en el instante de la toma, con la orientación de la cámara de ese momento ([pose]). Las plantillas de texto ya vienen traducidas:
 * [windowTemplate] lleva \u0001 y \u0002 donde van la hora de entrada y la de salida.
 */
internal fun buildSunShot(sun: SunState, pose: OrientationMath.Pose, view: SunView, windowTemplate: String, notInFrame: String, hourFmt: String, compassWarn: String? = null): SunShot? {
    val lat = sun.lat ?: return null; val lon = sun.lon ?: return null
    if (!sun.on) return null
    val decl = sun.declination()
    val heading = sun.corrected(pose.headingDeg); val elev = sun.correctedElev(pose.elevationDeg)
    val instant = sun.instant(); val zone = sun.zone; val date = sun.localTime().toLocalDate()
    val sp = SunMath.position(lat, lon, instant)
    val pr = SunMath.project(SunMath.trueToMagnetic(sp.azimuthDeg, decl), sp.elevationDeg, heading, elev, view.halfW, view.halfH, pose.levelDeg)
    val marker = if (pr.inFront && abs(pr.x) <= 1.0 && abs(pr.y) <= 1.0) SunPoint(pr.x, pr.y) else null
    val arcs = projectPaths(computeRawPaths(lat, lon, date, zone, sun.seasons), decl, heading, elev, view.halfW, view.halfH, if (sun.picked == null) instant else null, pose.levelDeg)
    val w = SunMath.frameWindow(lat, lon, date, zone, decl, heading, elev, view.halfW, view.halfH, view.frameW, view.frameH)
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    val window = if (w == null) notInFrame
        else windowTemplate.replace("\u0001", w.first.atZone(zone).format(hm)).replace("\u0002", w.second.atZone(zone).format(hm))
    val badge = String.format(Locale.ROOT, "☀ %d° · %+d°", sp.azimuthDeg.roundToInt() % 360, sp.elevationDeg.roundToInt())
    val meta = org.json.JSONObject()
        .put("time", instant.toString()).put("azimuth_deg_true", sp.azimuthDeg).put("elevation_deg", sp.elevationDeg)
        .put("declination_deg", decl).put("compass_offset_deg", sun.headingOffset).put("elevation_offset_deg", sun.elevationOffset)
        .put("planned", sun.picked != null).put("compass_warning", compassWarn != null)
        .put("frame_window_start", w?.first?.toString() ?: org.json.JSONObject.NULL).put("frame_window_end", w?.second?.toString() ?: org.json.JSONObject.NULL)
    return SunShot(SunNote(badge, "☀ $window", marker, arcs, hourFmt, warn = compassWarn), meta)
}

/**
 * El sol sobre la imagen: el arco de su recorrido del día con una marca por hora, un círculo con rayos si ahora cae dentro del campo
 * del teléfono (o una flecha en el borde hacia donde girar), y arriba la ventana horaria en que cruza el encuadre actual.
 * Usa el rumbo magnético del sensor, con el ajuste de calibración y la declinación del lugar; supone el teléfono derecho.
 */
@Composable
internal fun SunOverlay(sun: SunState, view: SunView, scaleX: Float, scaleY: Float, liftBottom: androidx.compose.ui.unit.Dp = 0.dp) {
    val lat = sun.lat; val lon = sun.lon; val pose = view.pose
    if (!sun.on || lat == null || lon == null || pose == null) return
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val decl = remember(lat, lon) { sun.declination() }
    val heading = sun.corrected(pose.headingDeg)
    val elev = sun.correctedElev(pose.elevationDeg)
    val instant = sun.instant()
    val sp = SunMath.position(lat, lon, instant)
    val azMag = SunMath.trueToMagnetic(sp.azimuthDeg, decl)
    val proj = SunMath.project(azMag, sp.elevationDeg, heading, elev, view.halfW, view.halfH, pose.levelDeg)
    val dist = SunMath.angularDistanceDeg(azMag, sp.elevationDeg, heading, elev)
    val col = if (p.red) p.screenText else sunYellow
    val behindText = stringResource(R.string.sun_behind)
    val belowText = stringResource(R.string.sun_below)
    val below = sp.elevationDeg < 0
    // Recorridos (hoy y, si se piden, las estaciones): las posiciones se calculan una vez por lugar y fecha; la proyección, en cada cuadro.
    val date = sun.localTime().toLocalDate()
    val zone = sun.zone
    val raw = remember(lat, lon, date, sun.seasons) { computeRawPaths(lat, lon, date, zone, sun.seasons) }
    val arcs = projectPaths(raw, decl, heading, elev, view.halfW, view.halfH, if (sun.picked == null) instant else null, pose.levelDeg)
    val window = rememberSunWindow(sun, view)
    val hm = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val windowText = if (window == null) stringResource(R.string.sun_not_in_frame)
        else stringResource(R.string.sun_in_frame, window.first.atZone(zone).format(hm), window.second.atZone(zone).format(hm))
    val hourFmt = stringResource(R.string.sun_hour_tick)
    val compassWarn = stringResource(if (view.compassDisturbed) R.string.sun_compass_disturbed_short else if (view.compassImprecise) R.string.sun_compass_imprecise_short else R.string.sun_compass_warn)
    val calibrateHint = stringResource(R.string.sun_calibrate_hint)
    val magCalText = view.tracker?.takeIf { it.calState == app.mado.camera.MagCalState.COLLECTING }?.let { stringResource(R.string.sun_magcal_progress, (it.calProgress * 100).toInt()) }
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        // Ancho y alto de la imagen completa en la pantalla: en la vista recortada solo se ve el encuadre, así que la imagen es más grande que el visor.
        val iw = size.width * scaleX
        val ih = size.height * scaleY
        fun at(x: Double, y: Double) = Offset(c.x + (x * iw / 2).toFloat(), c.y - (y * ih / 2).toFloat())
        val style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.Black)
        val tickStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = col)
        fun label(text: String, pos: Offset, bg: Color = col, fg: Color = Color.Black) {
            val l = measurer.measure(text, style.copy(color = fg))
            val pad = 4.dp.toPx()
            val tl = Offset(pos.x.coerceIn(0f, maxOf(0f, size.width - l.size.width - 2 * pad)), pos.y.coerceIn(0f, maxOf(0f, size.height - l.size.height - pad)))
            drawRect(bg, tl, Size(l.size.width + 2 * pad, l.size.height + pad))
            drawText(l, topLeft = Offset(tl.x + pad, tl.y + pad / 2))
        }
        var markerPos: Offset? = null
        if (proj.inside) markerPos = at(proj.x, proj.y)
        // Recorridos: tramos entre puntos consecutivos visibles; el de hoy lleva una marca por hora y los de las estaciones, su fecha.
        for (arc in arcs) {
            val ac = if (arc.hours || p.red) col else Color(arc.color)
            var labelled = false
            val pts = arc.points
            for (i in pts.indices) {
                val pt = pts[i]
                val o = pt?.let { at(it.x, it.y) }
                val prevO = pts.getOrNull(i - 1)?.let { at(it.x, it.y) }
                val nextO = pts.getOrNull(i + 1)?.let { at(it.x, it.y) }
                val a = (if (arc.hours) 0.95f else 0.7f) * (if (pt?.past == true) 0.45f else 1f)
                if (o != null && prevO != null) drawLine(ac.copy(alpha = a), prevO, o, (if (arc.hours) 2.5f else 1.5f).dp.toPx())
                if (pt != null && o != null && abs(pt.x) <= 1.05 && abs(pt.y) <= 1.05) {
                    if (arc.hours && pt.hour != null) {
                        // Si la marca de la hora cae encima del sol, se omite para no tapar su etiqueta.
                        val nearSun = markerPos?.let { m -> (o - m).getDistance() < 40.dp.toPx() } == true
                        if (!nearSun) {
                            drawCircle(ac.copy(alpha = a), 4.dp.toPx(), o)
                            val l = measurer.measure(String.format(Locale.ROOT, hourFmt, pt.hour), tickStyle)
                            // La etiqueta va al costado de la línea (perpendicular, hacia abajo), no encima de ella.
                            val tx = (nextO ?: o).x - (prevO ?: o).x; val ty = (nextO ?: o).y - (prevO ?: o).y
                            val len = kotlin.math.sqrt(tx * tx + ty * ty).takeIf { it > 1e-3f } ?: 1f
                            var nx = -ty / len; var ny = tx / len
                            if (ny < 0f || (abs(ny) < 0.3f && nx < 0f)) { nx = -nx; ny = -ny }
                            val reach = 7.dp.toPx() + abs(nx) * l.size.width / 2f + abs(ny) * l.size.height / 2f
                            drawText(l, topLeft = Offset(o.x + nx * reach - l.size.width / 2f, o.y + ny * reach - l.size.height / 2f))
                        }
                    } else if (!arc.hours && !labelled && arc.label != null) {
                        labelled = true
                        val l = measurer.measure(arc.label, tickStyle.copy(color = ac, fontSize = 10.sp))
                        drawText(l, topLeft = Offset(o.x + 5.dp.toPx(), o.y - l.size.height - 3.dp.toPx()))
                    }
                }
            }
        }
        // Dónde está el sol ahora.
        if (proj.inside) {
            val pos = at(proj.x, proj.y)
            markerPos = pos
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
        // Ventana horaria en el encuadre actual, abajo a la izquierda (arriba chocaría con el reloj y la cabecera del visor).
        // La línea de tiempo ocupa el borde de abajo: los rótulos suben por encima de ella ([liftBottom]).
        val lift = liftBottom.toPx()
        label("☀ $windowText", Offset(8.dp.toPx(), size.height - 34.dp.toPx() - lift))
        if (view.compassUnreliable) label("⚠ $compassWarn", Offset(8.dp.toPx(), size.height - 64.dp.toPx() - lift), bg = Color(0xFFFF5A1F), fg = Color.White)
        magCalText?.let { label(it, Offset(8.dp.toPx(), size.height * 0.45f), bg = Color(0xFFFF5A1F), fg = Color.White) }
        if (sun.calibrating) {
            val l = measurer.measure(calibrateHint, style.copy(fontSize = 15.sp, color = Color.Black))
            val pad = 8.dp.toPx()
            val tl = Offset(c.x - l.size.width / 2f - pad, c.y - l.size.height - 2 * pad - 40.dp.toPx())
            drawRect(col, tl, Size(l.size.width + 2 * pad, l.size.height + 2 * pad))
            drawText(l, topLeft = Offset(tl.x + pad, tl.y + pad))
        }
    }
}


/**
 * Línea de tiempo del visor: se arrastra (o se toca) para mover la hora del día mostrado, de una hora antes del amanecer a una después
 * del atardecer, y una tecla «ahora» vuelve a la hora real. Pinta las horas de luz y la hora dorada.
 */
@Composable
internal fun SunTimeline(sun: SunState, modifier: Modifier = Modifier) {
    val lat = sun.lat; val lon = sun.lon
    if (!sun.on || lat == null || lon == null) return
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val zone = sun.zone
    val local = sun.localTime()
    val date = local.toLocalDate()
    val day = remember(lat, lon, date) { SunMath.day(lat, lon, date, zone) }
    fun minute(i: Instant?) = i?.atZone(zone)?.let { it.hour * 60 + it.minute }
    val rise = minute(day.sunrise); val set = minute(day.sunset)
    val startMin = if (rise != null) ((rise - 60) / 60 * 60).coerceAtLeast(0) else 0
    val endMin = if (set != null) (((set + 60 + 59) / 60) * 60).coerceAtMost(1440) else 1440
    val span = (endMin - startMin).coerceAtLeast(60)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val setFromX: (Float) -> Unit = { x -> sun.setTime(date, (startMin + (x / widthPx).coerceIn(0f, 1f) * span).roundToInt() / 5 * 5) }
    val nowMin = local.hour * 60 + local.minute
    val col = if (p.red) p.screenText else sunYellow
    val golden = Color(0xFFFF9A3D)
    Row(modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.62f)), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).fillMaxSize().onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(date, startMin, endMin) { detectTapGestures { setFromX(it.x) } }
                .pointerInput(date, startMin, endMin) { detectHorizontalDragGestures(onDragStart = { setFromX(it.x) }) { change, _ -> change.consume(); setFromX(change.position.x) } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                fun xOf(min: Int) = ((min - startMin).toFloat() / span * size.width).coerceIn(0f, size.width)
                val y = size.height * 0.62f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
                if (rise != null && set != null) drawLine(col.copy(alpha = 0.45f), Offset(xOf(rise), y), Offset(xOf(set), y), 4.dp.toPx())
                minute(day.goldenMorningEnd)?.let { g -> if (rise != null) drawLine(golden, Offset(xOf(rise), y), Offset(xOf(g), y), 4.dp.toPx()) }
                minute(day.goldenEveningStart)?.let { g -> if (set != null) drawLine(golden, Offset(xOf(g), y), Offset(xOf(set), y), 4.dp.toPx()) }
                var h = startMin
                while (h <= endMin) {
                    val x = xOf(h)
                    drawLine(Color.White.copy(alpha = 0.55f), Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), 1.dp.toPx())
                    if ((h / 60) % 2 == 0 || span <= 360) {
                        val l = measurer.measure(String.format(Locale.ROOT, "%d", h / 60), TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.75f)))
                        drawText(l, topLeft = Offset((x - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), y + 8.dp.toPx()))
                    }
                    h += 60
                }
                val hx = xOf(nowMin.coerceIn(startMin, endMin))
                drawCircle(Color.Black.copy(alpha = 0.7f), 9.dp.toPx(), Offset(hx, y))
                drawCircle(col, 7.dp.toPx(), Offset(hx, y))
                val tl = measurer.measure(local.format(DateTimeFormatter.ofPattern("HH:mm")), TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = col))
                drawText(tl, topLeft = Offset((hx - tl.size.width / 2f).coerceIn(0f, size.width - tl.size.width), 2.dp.toPx()))
            }
        }
        Text(
            stringResource(R.string.sun_now), style = mono(12, if (sun.picked != null) Color.Black else p.text.copy(alpha = 0.5f), true),
            modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).background(if (sun.picked != null) col else Color.Transparent)
                .clickable(enabled = sun.picked != null) { sun.backToNow() }.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}


/** Calibración propia de la brújula: el usuario gira el teléfono en todas direcciones y Mado mide y resta el sesgo magnético del propio aparato. */
@Composable
private fun MagCalibrationControls(t: app.mado.camera.OrientationTracker) {
    val p = LocalPalette.current
    Label(stringResource(R.string.sun_magcal_title))
    Text(stringResource(R.string.sun_magcal_help), style = mono(10, p.text.copy(alpha = 0.7f)))
    when (t.calState) {
        app.mado.camera.MagCalState.COLLECTING -> {
            Text(stringResource(R.string.sun_magcal_progress, (t.calProgress * 100).toInt()), style = mono(12, p.accent, true), modifier = Modifier.padding(top = 4.dp))
            Seg(listOf(stringResource(R.string.action_cancel)), -1) { t.cancelMagCal() }
        }
        else -> {
            when (t.calState) {
                app.mado.camera.MagCalState.DONE -> Text(stringResource(R.string.sun_magcal_done, t.calBiasUt ?: 0.0), style = mono(10, p.text))
                app.mado.camera.MagCalState.FAILED -> Text(stringResource(R.string.sun_magcal_fail), style = mono(10, p.accent))
                app.mado.camera.MagCalState.NEED_LOCATION -> Text(stringResource(R.string.sun_magcal_need_location), style = mono(10, p.accent))
                app.mado.camera.MagCalState.UNSUPPORTED -> Text(stringResource(R.string.sun_magcal_unsupported), style = mono(10, p.accent))
                else -> if (t.hasBias) Text(stringResource(R.string.sun_magcal_active), style = mono(10, p.text))
            }
            Seg(listOf(stringResource(R.string.sun_magcal_start)) + (if (t.hasBias) listOf(stringResource(R.string.sun_magcal_clear)) else emptyList()), -1) { i ->
                if (i == 0) t.startMagCal() else t.clearMagBias()
            }
        }
    }
}


/** Pantalla completa mientras se calibra la brújula: porcentaje grande y barra, para verlo aunque el teléfono gire y el menú cambie de lugar. */
@Composable
internal fun MagCalOverlay(t: app.mado.camera.OrientationTracker) {
    val frac = t.calProgress.coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).pointerInput(Unit) { detectTapGestures { t.cancelMagCal() } },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text("${(frac * 100).toInt()}%", style = mono(72, Color.White, true))
            Box(Modifier.padding(top = 14.dp).fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(frac).height(12.dp).background(Accent))
            }
            Text(stringResource(R.string.sun_magcal_overlay_help), style = mono(15, Color.White), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
            Text(stringResource(R.string.sun_magcal_tap_cancel), style = mono(12, Color.White.copy(alpha = 0.6f)), modifier = Modifier.padding(top = 12.dp))
        }
    }
}
