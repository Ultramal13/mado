package app.mado.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Menú de acciones (calibrar, diagnóstico, idioma, tema…). */
@Composable
fun MenuDialog(title: String, items: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit, closeLabel: String) {
    val p = LocalPalette.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.body,
        title = { Text(title, style = mono(14, p.text, true)) },
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
