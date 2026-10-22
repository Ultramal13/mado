package app.mado.optics

import kotlin.math.atan
import kotlin.math.tan

/** Óptica básica del CLAUDE.md. Ángulos en radianes salvo que el nombre diga lo contrario. */
object Fov {
    /** FOV = 2 * atan(ancho_activo_mm / (2 * focal_mm)) */
    fun fovRad(activeMm: Double, focalMm: Double): Double =
        2.0 * atan(activeMm / (2.0 * focalMm))

    fun fovDeg(activeMm: Double, focalMm: Double): Double =
        Math.toDegrees(fovRad(activeMm, focalMm))

    /**
     * Factor de recuadro = tan(fov_objetivo/2) / tan(fov_telefono/2).
     * < 1: el recuadro cabe dentro del preview. > 1: el objetivo es más ancho que el teléfono (padding).
     */
    fun frameFactor(targetFovRad: Double, phoneFovRad: Double): Double =
        tan(targetFovRad / 2.0) / tan(phoneFovRad / 2.0)

    /** Focal equivalente full frame (36 mm de ancho) para el mismo FOV horizontal. */
    fun equivalentFocal35(activeMm: Double, focalMm: Double): Double =
        focalMm * FULL_FRAME_WIDTH_MM / activeMm

    /**
     * Ancho activo que realmente ve un stream de relación `streamAspect` (ancho/alto): si el sensor es más
     * ancho que el stream se recorta a los lados; si es más angosto, el ancho se conserva.
     */
    fun croppedWidthMm(activeW: Double, activeH: Double, streamAspect: Double): Double =
        minOf(activeW, activeH * streamAspect)

    /** FOV horizontal real (rad) a partir de un ancho visible W medido a distancia d (mismas unidades). */
    fun measuredFovDeg(visibleWidth: Double, distance: Double): Double =
        Math.toDegrees(2.0 * atan(visibleWidth / (2.0 * distance)))

    const val FULL_FRAME_WIDTH_MM = 36.0
}
