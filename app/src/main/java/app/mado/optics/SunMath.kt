package app.mado.optics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Posición del sol y horas clave del día, sin internet: solo latitud, longitud y hora. Algoritmo de la hoja de cálculo de la NOAA
 * (Meeus, «Astronomical Algorithms»), con error de menos de 0,02° entre 1900 y 2100 fuera de las zonas polares.
 *
 * El azimut es el **verdadero** (respecto del norte geográfico, en sentido horario). El rumbo del sensor del teléfono es magnético:
 * antes de compararlos se corrige con la declinación magnética del lugar (ver [trueToMagnetic]).
 */
object SunMath {
    /** [azimuthDeg]: 0° norte, 90° este (verdadero). [elevationDeg]: sobre el horizonte, con refracción de la atmósfera. */
    data class Position(val azimuthDeg: Double, val elevationDeg: Double, val geometricElevationDeg: Double)

    private data class Astro(val declinationRad: Double, val eqTimeMin: Double)

    private fun julianDay(t: Instant) = t.epochSecond / 86400.0 + t.nano / 86400e9 + 2440587.5

    private fun astro(t: Instant): Astro {
        val jc = (julianDay(t) - 2451545.0) / 36525.0
        val l0 = (280.46646 + jc * (36000.76983 + jc * 0.0003032)).mod(360.0)
        val m = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val mRad = Math.toRadians(m)
        val e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val c = sin(mRad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * jc) + sin(3 * mRad) * 0.000289
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * jc
        val appLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val seconds = 21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))
        val meanObliq = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val obliq = Math.toRadians(meanObliq + 0.00256 * cos(Math.toRadians(omega)))
        val decl = asin(sin(obliq) * sin(Math.toRadians(appLong)))
        val y = tan(obliq / 2).pow(2)
        val l0Rad = Math.toRadians(l0)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(2 * l0Rad) - 2 * e * sin(mRad) + 4 * e * y * sin(mRad) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * mRad)
        )
        return Astro(decl, eqTime)
    }

    /** Refracción atmosférica (grados) para una elevación geométrica, fórmula de la NOAA. */
    fun refractionDeg(elevDeg: Double): Double {
        if (elevDeg > 85.0) return 0.0
        val te = tan(Math.toRadians(elevDeg))
        val arcSec = when {
            elevDeg > 5.0 -> 58.1 / te - 0.07 / te.pow(3) + 0.000086 / te.pow(5)
            elevDeg > -0.575 -> 1735.0 + elevDeg * (-518.2 + elevDeg * (103.4 + elevDeg * (-12.79 + elevDeg * 0.711)))
            else -> -20.774 / te
        }
        return arcSec / 3600.0
    }

    fun position(latDeg: Double, lonDeg: Double, at: Instant): Position {
        val a = astro(at)
        val secOfDay = (at.epochSecond % 86400 + 86400) % 86400 + at.nano / 1e9
        val tst = (secOfDay / 60.0 + a.eqTimeMin + 4.0 * lonDeg).mod(1440.0)          // hora solar verdadera, en minutos
        val hourAngle = Math.toRadians(tst / 4.0 - 180.0)                               // oeste positivo
        val lat = Math.toRadians(latDeg)
        val cosZ = sin(lat) * sin(a.declinationRad) + cos(lat) * cos(a.declinationRad) * cos(hourAngle)
        val elev = 90.0 - Math.toDegrees(acos(cosZ.coerceIn(-1.0, 1.0)))
        val az = Math.toDegrees(atan2(sin(hourAngle), cos(hourAngle) * sin(lat) - tan(a.declinationRad) * cos(lat))) + 180.0
        return Position((az + 360.0) % 360.0, elev + refractionDeg(elev), elev)
    }

    /** Del norte verdadero al magnético: con declinación positiva (este), el norte magnético queda al este del verdadero. */
    fun trueToMagnetic(trueAzimuthDeg: Double, declinationDeg: Double) = ((trueAzimuthDeg - declinationDeg) % 360.0 + 360.0) % 360.0

    /** Un cruce del sol por una altura dada: subiendo (amanecer) y bajando (atardecer); null si ese día no ocurre (sol de medianoche, noche polar). */
    data class Crossing(val rising: Instant?, val setting: Instant?)

    /** Horas de un día local (de las 00:00 a las 24:00 de [zone]) en que la elevación geométrica del sol cruza [elevationDeg]. */
    fun crossings(latDeg: Double, lonDeg: Double, date: LocalDate, zone: ZoneId, elevationDeg: Double): Crossing {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val stepSec = 60L
        var rising: Instant? = null
        var setting: Instant? = null
        var t = start
        var prev = position(latDeg, lonDeg, t).geometricElevationDeg - elevationDeg
        while (t < end) {
            val nt = t.plusSeconds(stepSec)
            val cur = position(latDeg, lonDeg, nt).geometricElevationDeg - elevationDeg
            if (prev < 0 && cur >= 0 && rising == null) rising = lerp(t, stepSec, prev, cur)
            if (prev >= 0 && cur < 0 && setting == null) setting = lerp(t, stepSec, prev, cur)
            prev = cur; t = nt
        }
        return Crossing(rising, setting)
    }

    private fun lerp(t: Instant, stepSec: Long, a: Double, b: Double): Instant =
        t.plusMillis((stepSec * 1000.0 * (abs(a) / (abs(a) + abs(b)))).toLong())

    /** Horas clave de un día, en el huso del lugar. Las que no ocurren ese día quedan null. */
    data class Day(
        val sunrise: Instant?, val sunset: Instant?,
        val goldenMorningEnd: Instant?, val goldenEveningStart: Instant?,
        val blueMorningStart: Instant?, val blueMorningEnd: Instant?,
        val blueEveningStart: Instant?, val blueEveningEnd: Instant?,
        val solarNoon: Instant,
    )

    fun day(latDeg: Double, lonDeg: Double, date: LocalDate, zone: ZoneId): Day {
        val horizon = crossings(latDeg, lonDeg, date, zone, SUNRISE_ELEVATION)
        val golden = crossings(latDeg, lonDeg, date, zone, GOLDEN_ELEVATION)
        val blueLow = crossings(latDeg, lonDeg, date, zone, BLUE_LOW_ELEVATION)
        val blueHigh = crossings(latDeg, lonDeg, date, zone, BLUE_HIGH_ELEVATION)
        return Day(
            sunrise = horizon.rising, sunset = horizon.setting,
            goldenMorningEnd = golden.rising, goldenEveningStart = golden.setting,
            blueMorningStart = blueLow.rising, blueMorningEnd = blueHigh.rising,
            blueEveningStart = blueHigh.setting, blueEveningEnd = blueLow.setting,
            solarNoon = solarNoon(latDeg, lonDeg, date, zone),
        )
    }

    /** Instante de mayor elevación del día local, a un minuto. */
    fun solarNoon(latDeg: Double, lonDeg: Double, date: LocalDate, zone: ZoneId): Instant {
        val start = date.atStartOfDay(zone).toInstant()
        var best = start
        var bestElev = -91.0
        var t = start
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        while (t < end) {
            val e = position(latDeg, lonDeg, t).geometricElevationDeg
            if (e > bestElev) { bestElev = e; best = t }
            t = t.plusSeconds(60)
        }
        return best
    }

    /** Altura del sol al amanecer y al atardecer (centro del disco, con la refracción media). */
    const val SUNRISE_ELEVATION = -0.833
    /** Hora dorada: el sol entre el horizonte y +6°. */
    const val GOLDEN_ELEVATION = 6.0
    /** Hora azul: el sol entre −8° y −4°. */
    const val BLUE_LOW_ELEVATION = -8.0
    const val BLUE_HIGH_ELEVATION = -4.0

    /**
     * Dónde cae el sol en la imagen: [x] hacia la derecha y [y] hacia arriba, en unidades de «mitad del campo visible»
     * (1 = borde derecho/superior de la imagen). [inFront] es falso si el sol queda detrás de la cámara.
     * [camHeadingDeg] y [camElevationDeg] son el rumbo (mismo norte que [sunAzimuthDeg]) y la elevación del eje de la cámara.
     * [rollDeg] es el balanceo de la imagen respecto del horizonte (positivo: el «arriba» del mundo se ve inclinado hacia la derecha, como
     * [OrientationMath.Pose.levelDeg]); con 0 se proyecta como si la cámara estuviera a nivel.
     */
    data class Projection(val x: Double, val y: Double, val inFront: Boolean) {
        val inside get() = inFront && abs(x) <= 1.0 && abs(y) <= 1.0
    }

    fun project(
        sunAzimuthDeg: Double, sunElevationDeg: Double, camHeadingDeg: Double, camElevationDeg: Double,
        halfWidthTan: Double, halfHeightTan: Double, rollDeg: Double = 0.0,
    ): Projection {
        fun vec(az: Double, el: Double): DoubleArray {
            val a = Math.toRadians(az); val e = Math.toRadians(el)
            return doubleArrayOf(sin(a) * cos(e), cos(a) * cos(e), sin(e))        // este, norte, arriba
        }
        val f = vec(camHeadingDeg, camElevationDeg)
        val s = vec(sunAzimuthDeg, sunElevationDeg)
        // Derecha de la cámara: horizontal, perpendicular al eje. Arriba: perpendicular a ambas.
        var rx = f[1]; var ry = -f[0]; var rz = 0.0
        val rn = sqrt(rx * rx + ry * ry)
        if (rn < 1e-9) { rx = 1.0; ry = 0.0; rz = 0.0 } else { rx /= rn; ry /= rn }
        val ux = ry * f[2] - rz * f[1]; val uy = rz * f[0] - rx * f[2]; val uz = rx * f[1] - ry * f[0]
        val sx = s[0] * rx + s[1] * ry + s[2] * rz
        val sy = s[0] * ux + s[1] * uy + s[2] * uz
        val sz = s[0] * f[0] + s[1] * f[1] + s[2] * f[2]
        if (sz <= 1e-6) return Projection(sx, sy, false)
        // Con la cámara a nivel el punto cae en (x0, y0); si la imagen está inclinada, todo gira alrededor del centro.
        val x0 = sx / sz; val y0 = sy / sz
        val phi = Math.toRadians(rollDeg)
        val x = x0 * cos(phi) + y0 * sin(phi)
        val y = -x0 * sin(phi) + y0 * cos(phi)
        return Projection(x / halfWidthTan, y / halfHeightTan, true)
    }

    /** Campo visible de la imagen en cada eje a partir del horizontal del teléfono y la proporción ancho/alto. */
    fun halfTans(hFovDeg: Double, aspect: Double): Pair<Double, Double> {
        val hw = tan(Math.toRadians(hFovDeg) / 2.0)
        return hw to hw / aspect
    }

    /**
     * Calibración tocando el sol: dado dónde debería estar el sol ([sunAzimuthDeg], [sunElevationDeg], con el mismo norte que el sensor) y el
     * punto de la imagen donde el usuario lo vio ([tapX], [tapY] en unidades de «mitad del campo», y hacia arriba), devuelve el rumbo y la
     * elevación del eje de la cámara con los que el sol caería justo ahí. Parte de lo que midió el sensor y corrige por aproximaciones.
     */
    fun solveCamera(
        sunAzimuthDeg: Double, sunElevationDeg: Double, tapX: Double, tapY: Double,
        halfWidthTan: Double, halfHeightTan: Double, startHeadingDeg: Double, startElevationDeg: Double, rollDeg: Double = 0.0,
    ): Pair<Double, Double> {
        var h = startHeadingDeg
        var e = startElevationDeg
        for (i in 0 until 60) {
            val p = project(sunAzimuthDeg, sunElevationDeg, h, e, halfWidthTan, halfHeightTan, rollDeg)
            if (!p.inFront) { h += 30.0; continue }
            val dx = tapX - p.x
            val dy = tapY - p.y
            if (abs(dx) < 1e-10 && abs(dy) < 1e-10) break
            h -= Math.toDegrees(kotlin.math.atan(dx * halfWidthTan)) / cos(Math.toRadians(e)).coerceAtLeast(0.2)
            e -= Math.toDegrees(kotlin.math.atan(dy * halfHeightTan))
        }
        return ((h % 360.0) + 360.0) % 360.0 to e
    }

    /** Una posición del recorrido del sol durante el día (azimut verdadero y elevación con refracción). */
    data class Sample(val at: Instant, val azimuthDeg: Double, val elevationDeg: Double)

    /** Posiciones del sol cada [stepMin] minutos del día local (de las 00:00 a las 24:00), solo mientras está sobre −3°. */
    fun path(latDeg: Double, lonDeg: Double, date: LocalDate, zone: ZoneId, stepMin: Int = 10): List<Sample> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val out = ArrayList<Sample>()
        var t = start
        while (t < end) {
            val p = position(latDeg, lonDeg, t)
            if (p.elevationDeg > -3.0) out.add(Sample(t, p.azimuthDeg, p.elevationDeg))
            t = t.plusSeconds(stepMin * 60L)
        }
        return out
    }

    /**
     * Primer y último minuto del día en que el sol, ya sobre el horizonte, cae dentro de un encuadre: [frameW] y [frameH] son la
     * mitad del ancho y del alto del encuadre en unidades de «mitad del campo visible» (1 = el borde de la imagen).
     * [declinationDeg] pasa el azimut verdadero a la brújula del teléfono; null si el sol no cruza ese encuadre ese día.
     */
    fun frameWindow(
        latDeg: Double, lonDeg: Double, date: LocalDate, zone: ZoneId, declinationDeg: Double,
        camHeadingMagDeg: Double, camElevationDeg: Double, halfWidthTan: Double, halfHeightTan: Double,
        frameW: Double = 1.0, frameH: Double = 1.0,
    ): Pair<Instant, Instant>? {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        var first: Instant? = null
        var last: Instant? = null
        var t = start
        while (t < end) {
            val p = position(latDeg, lonDeg, t)
            if (p.elevationDeg > 0.0) {
                val pr = project(trueToMagnetic(p.azimuthDeg, declinationDeg), p.elevationDeg, camHeadingMagDeg, camElevationDeg, halfWidthTan, halfHeightTan)
                if (pr.inFront && abs(pr.x) <= frameW && abs(pr.y) <= frameH) { if (first == null) first = t; last = t }
            }
            t = t.plusSeconds(60)
        }
        return if (first != null && last != null) first to last else null
    }

    /** Ángulo (grados) entre el eje de la cámara y el sol. */
    fun angularDistanceDeg(sunAzimuthDeg: Double, sunElevationDeg: Double, camHeadingDeg: Double, camElevationDeg: Double): Double {
        val s1 = Math.toRadians(sunElevationDeg); val c1 = Math.toRadians(camElevationDeg)
        val dAz = Math.toRadians(sunAzimuthDeg - camHeadingDeg)
        val cosD = sin(s1) * sin(c1) + cos(s1) * cos(c1) * cos(dAz)
        return Math.toDegrees(acos(cosD.coerceIn(-1.0, 1.0)))
    }
}
