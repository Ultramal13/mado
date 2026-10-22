package app.mado.optics

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.floor

/**
 * Hacia dónde apunta la cámara trasera, a partir de la matriz de rotación del teléfono (Android: world = R · device, fila a fila;
 * mundo: x = este, y = norte, z = arriba; teléfono: x = derecha, y = arriba de la pantalla, z = sale de la pantalla).
 * La cámara mira hacia −z del teléfono.
 */
object OrientationMath {
    data class Pose(val headingDeg: Double, val elevationDeg: Double, val levelDeg: Double)

    fun fromMatrix(r: FloatArray): Pose {
        // Dirección de la cámara en el mundo = −(tercera columna de R).
        val heading = (Math.toDegrees(atan2(-r[2].toDouble(), -r[5].toDouble())) + 360.0) % 360.0
        val elevation = Math.toDegrees(asin((-r[8]).toDouble().coerceIn(-1.0, 1.0)))
        // Inclinación de la imagen respecto del horizonte: 0 con el teléfono derecho (vertical u horizontal).
        val roll = Math.toDegrees(atan2(r[6].toDouble(), r[7].toDouble()))
        return Pose(heading, elevation, levelFromRoll(roll))
    }

    /** Lleva el balanceo a −45…45°: en vertical (0°) y en horizontal (±90°) «a nivel» es 0. */
    fun levelFromRoll(rollDeg: Double): Double {
        val x = rollDeg + 45.0
        return (x - 90.0 * floor(x / 90.0)) - 45.0
    }

    /** Índice 0..7 de los puntos cardinales (N, NE, E, SE, S, SO, O, NO). */
    fun compassIndex(headingDeg: Double): Int = (((headingDeg % 360.0) + 360.0) % 360.0 / 45.0 + 0.5).toInt() % 8

    fun isLevel(levelDeg: Double) = abs(levelDeg) < 1.0
}
