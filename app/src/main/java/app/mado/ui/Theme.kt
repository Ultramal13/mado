package app.mado.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Lenguaje visual "objeto" de LUX-0: aparato de mano, pantalla negra, monoespaciado,
// escalas de puntos y un solo acento naranja. Ver docs/2026-10-19-lux0-diseno.md §6 del proyecto LUX-0.

val Accent = Color(0xFFFF5A1F)

data class Palette(
    val key: String,
    val body: Color,
    val keyBg: Color,
    val keyText: Color,
    val keyOn: Color,
    val keyOnText: Color,
    val text: Color,
    val screen: Color,
    val screenText: Color,
    val screenDim: Color,
    val dots: Color,
    val accent: Color,
    val red: Boolean,
)

val Claro = Palette(
    "light", Color(0xFFE3E3DF), Color(0xFFF6F6F2), Color(0xFF1A1A1A), Color(0xFF1A1A1A), Color.White,
    Color(0xFF1A1A1A), Color(0xFF050505), Color(0xFFF0F0F0), Color(0xFF8B8B8B), Color(0xFFFFB46B), Accent, false,
)

val Oscuro = Claro.copy(
    key = "dark", body = Color(0xFF232325), keyBg = Color(0xFF3A3A3D), keyText = Color(0xFFE8E8E4),
    keyOn = Color(0xFFF4F4F0), keyOnText = Color(0xFF111111), text = Color(0xFFE8E8E4),
)

private val Rojo1 = Color(0xFFB3261E)

/** Noche roja: para sets a oscuras, sin encandilar ni contaminar la escena. */
val NocheRoja = Palette(
    "red", Color(0xFF0B0000), Color(0xFF1A0303), Rojo1, Rojo1, Color(0xFF0B0000),
    Rojo1, Color.Black, Rojo1, Color(0xFF6A1712), Color(0xFF6A1712), Rojo1, true,
)

val LocalPalette = staticCompositionLocalOf { Claro }

fun mono(size: Int, color: Color, bold: Boolean = false) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    color = color,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

/** Tecla ancha. `accent` = acción principal (naranja). */
@Composable
fun WideKey(text: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg = if (accent) p.accent else p.keyBg
    val fg = if (accent) (if (p.red) p.body else Color.White) else p.keyText
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = mono(13, fg, true))
    }
}

/** Botoncito de encabezado (tema). */
@Composable
fun SmallKey(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, style = mono(12, p.keyText),
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(p.keyBg)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
