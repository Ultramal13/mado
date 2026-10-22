package app.mado.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mado.R
import app.mado.diagnostics.CameraInfo
import app.mado.diagnostics.DiagnosticsReport
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    report: DiagnosticsReport,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeviceScreen(report)
            report.cameras.forEach { CameraScreen(it) }
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WideKey(stringResource(R.string.action_share), false, Modifier.weight(1f), onShare)
            WideKey(stringResource(R.string.action_save_file), false, Modifier.weight(1f), onSave)
            WideKey(stringResource(R.string.action_copy_json), true, Modifier.weight(1f), onCopy)
        }
    }
}

@Composable
private fun DeviceScreen(report: DiagnosticsReport) {
    val p = LocalPalette.current
    BlackScreen {
        Row {
            Text(
                "${report.device["manufacturer"].orEmpty()} ${report.device["model"].orEmpty()}".lowercase(),
                style = mono(14, p.screenText, true),
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.diag_count, report.cameras.size), style = mono(10, p.accent))
        }
        Text("android ${report.device["android"].orEmpty()}", style = mono(11, p.screenDim))
        // Garantía visible: esta pantalla solo lee metadatos, no abre la cámara.
        Text(stringResource(R.string.diag_safe), style = mono(10, p.screenDim), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CameraScreen(c: CameraInfo) {
    val p = LocalPalette.current
    BlackScreen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.diag_camera_id, c.id), style = mono(14, p.screenText, true))
            Text("  " + stringResource(when (c.facing) { "back" -> R.string.facing_back; "front" -> R.string.facing_front; "external" -> R.string.facing_external; else -> R.string.facing_unknown }), style = mono(11, p.screenDim))
            Spacer(Modifier.weight(1f))
            val tag = when {
                c.isLogical -> stringResource(R.string.diag_logical, c.physicalIds.joinToString(","))
                c.parentId != null -> stringResource(R.string.diag_physical_of, c.parentId)
                else -> null
            }
            tag?.let { Text(it, style = mono(10, p.accent)) }
        }
        if (c.error != null) {
            Text(c.error, style = mono(11, p.accent), modifier = Modifier.padding(top = 6.dp))
            return@BlackScreen
        }

        // Lectura grande: FOV horizontal de la primera focal, como el número grande de lux-0.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
            Text(c.hFovDeg.firstOrNull()?.let { fmt(it, 1) } ?: "----", style = mono(40, p.screenText, true))
            Text(" °", style = mono(16, p.screenDim))
        }
        Text(stringResource(R.string.diag_hfov_caption), style = mono(10, p.screenDim))

        FovScale(c.hFovDeg)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(p.screenDim))

        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Line(stringResource(R.string.diag_focal_label), c.focalLengthsMm.joinToString { fmt(it.toDouble(), 2) } + " mm")
            Line(stringResource(R.string.diag_equiv_label), c.equiv35Mm.joinToString { fmt(it, 1) } + " mm")
            Line(stringResource(R.string.diag_vfov_label), c.vFovDeg.joinToString { fmt(it, 2) } + " °")
            Line(stringResource(R.string.diag_active_label), c.activeSizeMm?.let { "${fmt(it.first, 3)} × ${fmt(it.second, 3)} mm" } ?: "—")
            Line(stringResource(R.string.diag_full_label), c.physicalSizeMm?.let { "${fmt(it.first.toDouble(), 3)} × ${fmt(it.second.toDouble(), 3)} mm" } ?: "—")
            Line(stringResource(R.string.diag_pixels_label), "${c.pixelArray?.let { "${it.first}×${it.second}" } ?: "—"} · ${c.activeArray?.let { "${it.first}×${it.second}" } ?: "—"}")
            Line(stringResource(R.string.diag_zoom_label), "${c.zoomRatioRange?.let { "${fmt(it.first.toDouble(), 2)}–${fmt(it.second.toDouble(), 2)}" } ?: "—"} · ${c.maxDigitalZoom?.let { fmt(it.toDouble(), 1) } ?: "—"}")
            Line(stringResource(R.string.diag_level_label), c.hardwareLevel)
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val p = LocalPalette.current
    SelectionContainer {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = mono(10, p.screenDim))
            Text(value, style = mono(11, p.screenText), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

private const val FOV_MIN = 10.0
private const val FOV_MAX = 130.0
private fun fovFraction(deg: Double) = ((deg - FOV_MIN) / (FOV_MAX - FOV_MIN)).toFloat().coerceIn(0f, 1f)

/** Escala de puntos de FOV horizontal (10°–130°) con un marcador por focal. */
@Composable
private fun FovScale(fovs: List<Double>) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            var x = 4f
            while (x < size.width) {
                drawCircle(p.dots, 1.6.dp.toPx(), Offset(x, size.height / 2)); x += 8.dp.toPx()
            }
            fovs.forEach {
                val px = fovFraction(it) * size.width
                drawRoundRect(
                    p.screenText, Offset(px - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(2f),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
            listOf(20.0, 40.0, 60.0, 90.0, 120.0).forEach { t ->
                val f = fovFraction(t)
                val shift = if (f > 0.9f) (-24).dp else (-8).dp
                Text("${t.toInt()}°", style = mono(9, p.screenDim), modifier = Modifier.offset(x = maxWidth * f + shift))
            }
        }
    }
}

// Locale.ROOT: separador decimal estable para comparar con mediciones.
private fun fmt(v: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)
