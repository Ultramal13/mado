package app.mado.optics

import kotlin.math.roundToInt

/** Velocidad de obturación: regla de los 180° (exposición = la mitad del tiempo de cada cuadro). */
object Shutter {
    /** «1/60» para 29.97 o 30 fps, «1/48» para 23.98 o 24, «1/50» para 25…; vacío si no hay fps válidos. */
    fun suggest180(fps: Double?): String {
        if (fps == null || fps <= 0.0) return ""
        return "1/${(2.0 * fps).roundToInt()}"
    }
}
