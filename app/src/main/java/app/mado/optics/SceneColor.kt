package app.mado.optics

private val PALETTE = intArrayOf(
    0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFB8C00.toInt(), 0xFF43A047.toInt(),
    0xFFD81B60.toInt(), 0xFF00ACC1.toInt(), 0xFF8E24AA.toInt(), 0xFF616161.toInt(),
)

/** Color de la etiqueta de una escena: el mismo texto da siempre el mismo color (como las etiquetas de colores de una Location List). */
fun sceneColorArgb(scene: String): Int = PALETTE[(scene.trim().lowercase().hashCode() and 0x7fffffff) % PALETTE.size]
