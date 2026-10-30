package app.mado.optics

import kotlin.math.atan
import kotlin.math.tan

/** Encuadre de una cámara objetivo sobre el preview del teléfono. */
object Framing {

    /** Recuadro centrado, en fracciones del preview (1.0 = todo el ancho/alto). `padding` si no cabe en el preview. */
    data class Frame(val widthFrac: Double, val heightFrac: Double) {
        val padding: Boolean get() = widthFrac > 1.0 || heightFrac > 1.0
    }

    /** Cámara objetivo ya resuelta: FOV horizontal y relación de aspecto de la imagen resultante (ancho/alto). */
    data class Target(val hFovRad: Double, val aspect: Double)

    /**
     * Objetivo a partir de sensor activo + focal + squeeze anamórfico.
     * El squeeze ensancha el campo horizontal (la focal vertical es la nominal), así que:
     * ancho efectivo = ancho × squeeze y aspecto = (ancho × squeeze) / alto.
     */
    fun target(activeWidthMm: Double, activeHeightMm: Double, focalMm: Double, squeeze: Double = 1.0): Target {
        val effectiveWidth = activeWidthMm * squeeze
        return Target(Fov.fovRad(effectiveWidth, focalMm), effectiveWidth / activeHeightMm)
    }

    /** `phoneAspect` = ancho/alto del preview del teléfono (p. ej. 4/3). */
    fun frame(target: Target, phoneHFovRad: Double, phoneAspect: Double): Frame {
        val w = Fov.frameFactor(target.hFovRad, phoneHFovRad)
        // El alto sale del aspecto: tan(vFov/2) = tan(hFov/2) / aspect, también en el teléfono.
        val h = w * phoneAspect / target.aspect
        return Frame(w, h)
    }

    /**
     * Pasa un recuadro (en fracciones del lado largo y del lado corto del preview) a fracciones del ancho y del alto de la pantalla.
     * Con el teléfono en vertical el lado largo es el alto: el recuadro sigue siendo horizontal en el mundo (como una cámara
     * en horizontal), así que su ancho se mide contra el lado corto (× aspecto) y su alto contra el largo (÷ aspecto).
     */
    fun toScreen(frame: Frame, phoneAspect: Double, portrait: Boolean): Frame =
        if (portrait) Frame(frame.widthFrac * phoneAspect, frame.heightFrac / phoneAspect) else frame

    /**
     * Escala de la imagen del teléfono dentro del visor: 1 si el recuadro actual cabe; si es más ancho que el teléfono
     * (padding) la imagen se achica para mostrarlo entero. Solo cuenta el recuadro actual: los demás del kit que no caben
     * simplemente salen del visor, en vez de achicar toda la imagen.
     */
    fun imageScale(current: Frame?): Float =
        if (current == null) 1f else (1.0 / maxOf(1.0, current.widthFrac, current.heightFrac)).toFloat()

    /** Campo de visión diagonal (rad) a partir del horizontal y la relación de aspecto (ancho/alto) de la imagen. */
    fun diagonalFovRad(hFovRad: Double, aspect: Double): Double =
        2.0 * atan(tan(hFovRad / 2.0) * kotlin.math.sqrt(1.0 + 1.0 / (aspect * aspect)))

    /** Recorte del FOV horizontal del teléfono por un perfil calibrado: factor sobre tan(FOV/2) (1.0 = sin calibrar). */
    fun calibratedHFovRad(reportedHFovRad: Double, tanHalfFactor: Double): Double =
        2.0 * atan(tan(reportedHFovRad / 2.0) * tanHalfFactor)
}
