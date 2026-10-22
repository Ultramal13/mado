package app.mado.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Encabezado del aparato: [←] `mado` · nombre de la pantalla · ☰ menú. */
@Composable
fun Header(title: String, onMenu: () -> Unit, onBack: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            SmallKey("←", onBack)
            Spacer(Modifier.padding(start = 8.dp))
        }
        Text("mado", style = mono(16, p.text, true))
        Spacer(Modifier.weight(1f))
        Text(title, style = mono(13, p.text))
        Spacer(Modifier.padding(start = 10.dp))
        SmallKey("☰", onMenu)
    }
}

/** Logo de Mado: anillo de cinco rayas con un recuadro naranja 3:2 en el centro (mismos trazos que el de las fotos). */
@Composable
fun MadoLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val ring = p.text
    androidx.compose.foundation.Canvas(modifier.androidx_size(size)) {
        val r = this.size.minDimension / 2f
        val ringR = r * 0.92f
        val stroke = ringR * 5f / 33f
        val c = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
        val span = 72f - 11.5f - Math.toDegrees((stroke / ringR).toDouble()).toFloat()
        for (k in 0 until 5) {
            val center = -90f + k * 72f
            drawArc(
                ring, center - span / 2f, span, false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - ringR, c.y - ringR),
                size = androidx.compose.ui.geometry.Size(2 * ringR, 2 * ringR),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        val rw = ringR * 30f / 33f; val rh = ringR * 20f / 33f
        drawRoundRect(
            Accent, androidx.compose.ui.geometry.Offset(c.x - rw / 2, c.y - rh / 2), androidx.compose.ui.geometry.Size(rw, rh),
            androidx.compose.ui.geometry.CornerRadius(ringR * 3f / 33f),
        )
    }
}

private fun Modifier.androidx_size(s: androidx.compose.ui.unit.Dp): Modifier = this.then(Modifier.size(s))

/** Menú de acciones (calibrar, diagnóstico, idioma, tema…), con el logo, el nombre y la versión de la app. */
@Composable
fun MenuDialog(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit, closeLabel: String, version: String = "") {
    val p = LocalPalette.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.body,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MadoLogo(44.dp)
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    Text("mado", style = mono(20, p.text, true))
                    if (version.isNotEmpty()) Text("v$version", style = mono(11, p.text.copy(alpha = 0.6f)))
                }
            }
        },
        text = {
            Column {
                items.forEach { (label, action) ->
                    Text(
                        label, style = mono(14, p.text),
                        modifier = Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(vertical = 13.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(closeLabel, style = mono(13, p.text)) } },
    )
}

@Composable
fun BlackScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.screen).padding(12.dp)) { content() }
}
