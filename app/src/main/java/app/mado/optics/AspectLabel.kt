package app.mado.optics

import kotlin.math.abs

/** Aspectos habituales y cómo se escriben; el resto sale como «1.43:1». */
private val KNOWN = listOf(
    1.0 to "1:1", 1.2 to "6:5", 1.25 to "5:4", 4.0 / 3.0 to "4:3", 1.5 to "3:2", 16.0 / 9.0 to "16:9", 1.85 to "1.85:1",
    17.0 / 9.0 to "17:9", 2.0 to "2:1", 2.39 to "2.39:1", 2.4 to "2.39:1", 9.0 / 16.0 to "9:16", 0.8 to "4:5",
)

/** Texto de una relación de aspecto (ancho/alto): el nombre usual si está a menos de 1.5 %, o «N.NN:1». */
fun aspectLabel(aspect: Double): String {
    val near = KNOWN.minByOrNull { abs(it.first / aspect - 1.0) }
    if (near != null && abs(near.first / aspect - 1.0) < 0.015) return near.second
    return String.format(java.util.Locale.ROOT, "%.2f:1", aspect)
}

/**
 * Rectángulo (ancho, alto) del aspecto de entrega `delivery` centrado dentro de un encuadre de `w` x `h`:
 * si es más ancho que el encuadre se recorta arriba y abajo; si es más angosto, a los lados.
 */
fun deliveryRect(w: Double, h: Double, delivery: Double): Pair<Double, Double> =
    if (delivery >= w / h) w to w / delivery else h * delivery to h
