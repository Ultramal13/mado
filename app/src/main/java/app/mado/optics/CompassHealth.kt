package app.mado.optics

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * ¿Se puede confiar en la brújula? Dos pruebas independientes, ambas sin tocar la cámara:
 *
 * 1. **Campo magnético:** la fuerza y la inclinación que mide el teléfono deben parecerse a las que le tocan a ese lugar de la Tierra
 *    (modelo geomagnético). Un cable de carga, un imán o una estructura de metal cerca las cambian mucho.
 * 2. **Salto de rumbo:** el giroscopio sabe cuánto giró el teléfono sin usar el campo magnético. Si el rumbo magnético cambia
 *    decenas de grados y el del giroscopio no, el campo se alteró.
 *
 * La primera sirve también al abrir la app (cuando aún no hay con qué comparar); la segunda detecta la alteración en el momento.
 */
object CompassHealth {
    /** Inclinación del campo (grados, positiva hacia abajo) a partir del campo [b] y la aceleración [a] (en reposo apunta hacia arriba). */
    fun inclinationDeg(b: DoubleArray, a: DoubleArray): Double? {
        val bn = norm(b); val an = norm(a)
        if (bn < 1e-6 || an < 1e-6) return null
        val s = (b[0] * a[0] + b[1] * a[1] + b[2] * a[2]) / (bn * an)       // seno de la elevación del campo sobre el horizonte
        return -Math.toDegrees(asin(s.coerceIn(-1.0, 1.0)))
    }

    fun strengthUt(b: DoubleArray) = norm(b)

    /**
     * Fuerza (µT) e inclinación medidas contra las esperadas para el lugar. Los márgenes son anchos a propósito: dentro de un edificio
     * el campo ya se desvía 20–30 %; solo se avisa cuando la lectura es claramente ajena a la Tierra.
     */
    fun fieldDisturbed(strengthUt: Double, inclinationDeg: Double?, expectedUt: Double, expectedInclinationDeg: Double): Boolean {
        if (expectedUt <= 0.0) return false
        val ratio = strengthUt / expectedUt
        if (ratio < 0.6 || ratio > 1.6) return true
        return inclinationDeg != null && abs(inclinationDeg - expectedInclinationDeg) > 25.0
    }

    /**
     * Aviso más suave que [fieldDisturbed]: el campo se aparta de lo normal lo bastante como para desviar el rumbo 10–15° (cerca de acero,
     * autos estacionados u hormigón armado), aunque no sea un imán encima. Medido en terreno: 30–35 µT donde debían ser 23 dieron 11–17° de error.
     */
    fun fieldImprecise(strengthUt: Double, inclinationDeg: Double?, expectedUt: Double, expectedInclinationDeg: Double): Boolean {
        if (expectedUt <= 0.0) return false
        val ratio = strengthUt / expectedUt
        if (ratio < 0.8 || ratio > 1.25) return true
        return inclinationDeg != null && abs(inclinationDeg - expectedInclinationDeg) > 12.0
    }

    /** Diferencia de ángulos llevada a −180…180. */
    fun wrap(deg: Double): Double = ((deg % 360.0) + 540.0) % 360.0 - 180.0

    private fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
}

/**
 * Compara el rumbo magnético con el del giroscopio (el de «rotation vector de juego», que tiene un cero arbitrario pero gira igual que el teléfono).
 * La diferencia entre ambos es constante mientras el campo está sano; si cambia más de [thresholdDeg] durante [settleMs], hay una alteración.
 * Si dura más de [acceptAfterMs] sin volver, se da por el nuevo estado normal (el usuario se movió a otro lugar) y queda solo la prueba del campo.
 */
class HeadingJumpDetector(
    private val thresholdDeg: Double = 25.0,
    private val settleMs: Long = 800L,
    private val acceptAfterMs: Long = 30_000L,
) {
    private var base: Double? = null
    private var since = 0L
    private var disturbedSince = 0L
    var disturbed = false
        private set

    fun reset() { base = null; since = 0L; disturbedSince = 0L; disturbed = false }

    /** [magDeg]: rumbo del sensor magnético; [gameDeg]: rumbo del giroscopio. Devuelve si hay alteración. */
    fun update(tMs: Long, magDeg: Double, gameDeg: Double): Boolean {
        val d = CompassHealth.wrap(magDeg - gameDeg)
        val b = base
        if (b == null) { base = d; return false }
        val dev = abs(CompassHealth.wrap(d - b))
        when {
            dev < 10.0 -> {                                             // sano: la base sigue despacio la deriva lenta del giroscopio
                base = CompassHealth.wrap(b + 0.02 * CompassHealth.wrap(d - b))
                since = 0L; disturbedSince = 0L; disturbed = false
            }
            dev > thresholdDeg -> {
                if (since == 0L) since = tMs
                if (!disturbed && tMs - since >= settleMs) { disturbed = true; disturbedSince = tMs }
                if (disturbed && tMs - disturbedSince >= acceptAfterMs) { base = d; since = 0L; disturbedSince = 0L; disturbed = false }
            }
            else -> since = 0L                                          // zona ambigua: se mantiene el estado
        }
        return disturbed
    }
}

/** Cuándo vale la pena recordar el ajuste ◎ de una sesión a otra. El sesgo de la brújula es del teléfono, pero cambia con el lugar y con el tiempo. */
object CalibrationMemory {
    const val MAX_AGE_MS = 24L * 3600 * 1000
    private const val MAX_DEG = 0.5                                      // ~55 km

    fun shouldRestore(savedAtMs: Long, nowMs: Long, savedLat: Double?, savedLon: Double?, lat: Double?, lon: Double?): Boolean {
        if (savedAtMs <= 0L || nowMs - savedAtMs !in 0L..MAX_AGE_MS) return false
        if (savedLat == null || savedLon == null || lat == null || lon == null) return true
        return abs(savedLat - lat) <= MAX_DEG && abs(savedLon - lon) <= MAX_DEG
    }
}
