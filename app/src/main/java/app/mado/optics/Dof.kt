package app.mado.optics

import kotlin.math.hypot

/**
 * Profundidad de campo e hiperfocal de una cámara con un lente de focal [focalMm] y apertura f/[fNumber], enfocada a una distancia dada.
 * Fórmulas clásicas con círculo de confusión c (mm): H = f²/(N·c) + f; cerca = s(H−f)/(H+s−2f); lejos = s(H−f)/(H−s), infinito si s ≥ H.
 * Es una aproximación: no distingue anamórficos ni la ampliación final de la imagen.
 */
object Dof {
    /** Distancias en metros; [farM] es infinito cuando el enfoque está en la hiperfocal o más allá. */
    data class Result(val hyperfocalM: Double, val nearM: Double, val farM: Double, val cocMm: Double) {
        val farIsInfinity get() = farM.isInfinite()
        /** Con el enfoque en la hiperfocal, desde la mitad de esa distancia hasta el infinito queda nítido. */
        val nearAtHyperfocalM get() = hyperfocalM / 2.0
    }

    /** Aperturas habituales para elegir en la app. */
    val apertures = listOf(1.4, 1.8, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)

    /** Círculo de confusión (mm) como la diagonal del sensor entre 1500 (≈ 0,029 mm en full frame). */
    fun cocMm(sensorWidthMm: Double, sensorHeightMm: Double): Double = hypot(sensorWidthMm, sensorHeightMm) / 1500.0

    fun compute(focalMm: Double, fNumber: Double, distanceM: Double, cocMm: Double): Result? {
        if (focalMm <= 0 || fNumber <= 0 || distanceM <= 0 || cocMm <= 0) return null
        val s = distanceM * 1000.0
        val h = focalMm * focalMm / (fNumber * cocMm) + focalMm
        val near = s * (h - focalMm) / (h + s - 2 * focalMm)
        val far = if (s >= h) Double.POSITIVE_INFINITY else s * (h - focalMm) / (h - s)
        return Result(h / 1000.0, near / 1000.0, far / 1000.0, cocMm)
    }

    /** «0.38 m», «2.4 m», «12 m» o «∞». */
    fun format(distanceM: Double): String = when {
        distanceM.isInfinite() -> "∞"
        distanceM < 1.0 -> String.format(java.util.Locale.ROOT, "%.2f m", distanceM)
        distanceM < 10.0 -> String.format(java.util.Locale.ROOT, "%.1f m", distanceM)
        else -> String.format(java.util.Locale.ROOT, "%.0f m", distanceM)
    }
}
