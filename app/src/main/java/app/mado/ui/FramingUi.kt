package app.mado.ui

import android.util.Size as AndroidSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mado.R
import app.mado.camera.ThermalGuard
import app.mado.data.loc
import app.mado.diagnostics.CameraInfo
import app.mado.optics.Fov
import app.mado.optics.Framing
import java.util.Locale
import kotlin.math.atan
import kotlin.math.tan

/** Un recuadro por focal del kit; `current` = el que se está usando. */
internal data class FrameDraw(val label: String, val frame: Framing.Frame, val current: Boolean, val detail: String = label)

/**
 * Recuadros de todas las focales del kit sobre el preview, sin oscurecer lo de afuera. La actual va en naranja con su etiqueta
 * y las demás en blanco. Si la focal más ancha supera al teléfono (padding), el preview se achica y ese recuadro llena el visor.
 */
@Composable
internal fun FrameOverlay(frames: List<FrameDraw>, imageScale: Float, showTags: Boolean = true) {
    val p = LocalPalette.current
    val line = if (p.red) p.screenText else Color.White
    val accent = if (p.red) p.screenText else Accent
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height / 2)
        val iw = size.width * imageScale
        val ih = size.height * imageScale
        // Límite de lo que realmente ve el teléfono (solo se nota cuando hay padding).
        if (imageScale < 0.999f) {
            drawRect(line.copy(alpha = 0.55f), Offset(c.x - iw / 2, c.y - ih / 2), Size(iw, ih), style = Stroke(1.5.dp.toPx()))
        }
        // Las no actuales primero, para que la actual quede encima.
        frames.sortedBy { it.current }.forEach { fd ->
            // Fracciones ya expresadas en los ejes de la pantalla (el recuadro siempre es horizontal en el mundo).
            val fw = fd.frame.widthFrac.toFloat() * iw
            val fh = fd.frame.heightFrac.toFloat() * ih
            val tl = Offset(c.x - fw / 2, c.y - fh / 2)
            drawRect(if (fd.current) accent else line.copy(alpha = 0.85f), tl, Size(fw, fh), style = Stroke((if (fd.current) 3f else 1.5f).dp.toPx()))
            if (!showTags) return@forEach
            val style = TextStyle(
                fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (fd.current) Color.Black else Color.White,
            )
            val l = measurer.measure(if (fd.current) fd.detail else fd.label, style)
            val pad = 4.dp.toPx()
            // Si el recuadro sale de la pantalla, la etiqueta se queda dentro para que se pueda leer.
            val tagTl = Offset(tl.x.coerceIn(0f, maxOf(0f, size.width - l.size.width - 2 * pad)), tl.y.coerceIn(0f, maxOf(0f, size.height - l.size.height - pad)))
            drawRect(
                if (fd.current) accent else Color.Black.copy(alpha = 0.55f), tagTl,
                Size(l.size.width + 2 * pad, l.size.height + pad),
            )
            drawText(l, topLeft = Offset(tagTl.x + pad, tagTl.y + pad / 2))
        }
    }
}

/** Etiqueta flotante arriba a la izquierda: cámara y modo que se simulan. Tocarla abre el panel de configuración. */
@Composable
internal fun ViewerChip(framing: FramingState, onClick: () -> Unit, modifier: Modifier = Modifier, evLabel: String? = null) {
    Column(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(framing.cameraName() + "  ▾", style = mono(15, Color.White, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(framing.modeName(), framing.subLabel(), evLabel).filter { it.isNotEmpty() }.joinToString(" · ")
        Text(sub, style = mono(10, Color.White.copy(alpha = 0.75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Datos sobre la vista recortada: cámara, modo, focal y campo de visión horizontal y vertical. */
@Composable
internal fun CropInfo(framing: FramingState, hDeg: Double?, vDeg: Double?, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(framing.cameraName(), style = mono(14, Color.White, true), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val mode = framing.modeName()
        if (mode.isNotEmpty()) Text(mode, style = mono(10, Color.White.copy(alpha = 0.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val fov = if (hDeg != null && vDeg != null) "  ·  FOV H " + fmt(hDeg, 1) + "°  V " + fmt(vDeg, 1) + "°" else ""
        Text(framing.currentLabel() + fov, style = mono(12, Accent, true), maxLines = 1)
    }
}

/** Obturador: círculo naranja grande, como en una app de cámara. */
@Composable
internal fun ShutterKey(working: Boolean, enabled: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 60.dp) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(Accent.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (working) Text("…", style = mono(20, Color.White, true))
        else Box(Modifier.size(size * 0.78f).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Transparent)
            .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
    }
}

/**
 * Controles del visor sobre la imagen: ◀ focal ▶ y obturador. Iniciar/detener y ☰ van aparte (arriba a la derecha).
 * En vertical es una fila abajo; en horizontal (`vertical`), una columna al costado con ☰ y ■ arriba.
 */
@Composable
internal fun ViewerBar(
    framing: FramingState, running: Boolean, working: Boolean,
    onToggle: () -> Unit, onShot: () -> Unit, vertical: Boolean = false, onMenu: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val focal: @Composable () -> Unit = {
        BarKey("◀", { framing.step(-1) })
        Text(framing.currentLabel(), style = mono(14, Color.White, true), maxLines = 1, modifier = Modifier.widthIn(min = if (vertical) 0.dp else 64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        BarKey("▶", { framing.step(1) })
    }
    if (vertical) Column(
        modifier.background(Color.Black.copy(alpha = 0.6f), shape).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        onMenu?.let { BarKey("☰", it) }
        if (running) BarKey("■", onToggle)
        focal()
        ShutterKey(working, running && !working, onShot, 52.dp)
    }
    else Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(60.dp))
        Row(
            Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.background(Color.Black.copy(alpha = 0.6f), shape).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { focal() }
        }
        ShutterKey(working, running && !working, onShot)
    }
}

@Composable
internal fun BarKey(text: String, onClick: () -> Unit, accent: Boolean = false, enabled: Boolean = true) {
    val p = LocalPalette.current
    val bg = if (accent) p.accent else p.keyBg
    val fg = if (accent) (if (p.red) p.body else Color.White) else p.keyText
    Box(
        Modifier.size(width = 40.dp, height = 36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            .background(bg.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = mono(15, fg, true)) }
}

/** Selector de varias opciones a la vez (el kit de focales). */
@Composable
internal fun MultiSeg(options: List<String>, on: List<Boolean>, onToggle: (Int) -> Unit) {
    val p = LocalPalette.current
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = on[i]
            Text(
                label, style = mono(12, if (sel) p.keyOnText else p.keyText, sel),
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp)).background(if (sel) p.keyOn else p.keyBg)
                    .clickable { onToggle(i) }.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
internal fun FramingControls(
    framing: FramingState,
    target: Framing.Target?,
    frame: Framing.Frame?,
    phoneDeg: Double,
    verified: Boolean,
    lenses: List<CameraInfo>,
    lensIdx: Int,
    onLens: (Int) -> Unit,
    lens: CameraInfo,
    size: AndroidSize,
    running: Boolean,
    hasPermission: Boolean,
    reason: Reason?,
    error: String?,
    guard: ThermalGuard,
    onToggle: () -> Unit,
    shot: ShotUi,
    onShot: () -> Unit,
    orientation: String?,
    gpsOn: Boolean,
    onGps: (Boolean) -> Unit,
    gpsDenied: Boolean,
    ev: Int = 0,
    evInfo: Pair<IntRange, Double>? = null,
    onEv: (Int) -> Unit = {},
) {
    val p = LocalPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(stringResource(R.string.frm_camera))
        val visible = framing.visibleCameras
        val others = framing.otherCameras
        var showOthers by remember { mutableStateOf(false) }
        val moreLabel = stringResource(R.string.frm_more_cameras)
        val hasMore = principalsExist(framing) && others.isNotEmpty()
        val labels = visible.map { framing.cameras[it].displayName.substringBefore(" (") } +
            (if (hasMore) listOf(moreLabel) else emptyList()) + stringResource(R.string.frm_custom)
        val selected = when {
            framing.isCustom -> labels.lastIndex
            else -> visible.indexOf(framing.cameraIdx).coerceAtLeast(0)
        }
        Seg(labels, selected) { i ->
            when {
                i == labels.lastIndex -> { framing.cameraIdx = framing.cameras.size; framing.modeIdx = 0 }
                hasMore && i == visible.size -> showOthers = true
                else -> { framing.cameraIdx = visible[i]; framing.modeIdx = 0; framing.builtInIdx = 0 }
            }
        }
        if (showOthers) PickerDialog(
            title = stringResource(R.string.frm_camera),
            items = others.map { framing.cameras[it].displayName.substringBefore(" (") },
            selected = others.indexOf(framing.cameraIdx),
            onPick = { framing.cameraIdx = others[it]; framing.modeIdx = 0; framing.builtInIdx = 0 },
            onDismiss = { showOthers = false },
        )

        val cam = framing.camera
        if (framing.isCustom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column { Label(stringResource(R.string.frm_sensor_w)); NumField(framing.customW) { framing.customW = it } }
                Column { Label(stringResource(R.string.frm_sensor_h)); NumField(framing.customH) { framing.customH = it } }
            }
        } else if (framing.isFixedLens) {
            Label(stringResource(R.string.frm_lenses))
            Seg(cam!!.builtInLenses.map { "${it.name.substringBefore(" (").loc()} · ${fmt(it.equivalentFocalMm, 0)}mm" }, framing.builtInIdx) { framing.builtInIdx = it }
            if (cam.modes.isEmpty()) {
                Label(stringResource(R.string.frm_aspect))
                Seg(framing.fixedAspects.map { it.second }, framing.fixedAspectIdx) { framing.fixedAspectIdx = it }
            }
        } else if (cam != null) {
            Label(stringResource(R.string.frm_mode))
            val modeNames = cam.modes.map { it.name.substringBefore(" (").loc() }
            if (modeNames.size <= 8) Seg(modeNames, framing.modeIdx) { framing.modeIdx = it }
            else {
                var open by remember { mutableStateOf(false) }
                Seg(listOf(modeNames.getOrElse(framing.modeIdx) { "" } + "  ▾"), 0) { open = true }
                if (open) PickerDialog(
                    title = stringResource(R.string.frm_mode), items = modeNames, selected = framing.modeIdx,
                    onPick = { framing.modeIdx = it }, onDismiss = { open = false },
                )
            }
        }

        if (!framing.isFixedLens) {
            Label(stringResource(R.string.frm_lens_set))
            Seg(framing.db.lensSets.map { it.name.substringBefore(" (").loc() }, framing.lensSetIdx) { framing.lensSetIdx = it }
            val focals = framing.lensSet?.focalsMm.orEmpty()
            if (focals.isNotEmpty()) {
                Label(stringResource(R.string.frm_kit))
                MultiSeg(focals.map { fmt(it, 0) }, focals.map { f -> framing.kit.any { kotlin.math.abs(it - f) < 1e-6 } }) { framing.toggleKit(focals[it]) }
            }
            Label(stringResource(R.string.frm_focal))
            NumField(framing.focal) { framing.focal = it }
        }

        evInfo?.let { (range, step) ->
            Label(stringResource(R.string.frm_exposure))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepKey("−", ev > range.first) { onEv((ev - 1).coerceAtLeast(range.first)) }
                Text(evText(ev, step), style = mono(15, p.text, true), modifier = Modifier.widthIn(min = 76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                StepKey("+", ev < range.last) { onEv((ev + 1).coerceAtMost(range.last)) }
                StepKey("0", ev != 0) { onEv(0) }
            }
        }

        Label(stringResource(R.string.gps_label))
        Seg(listOf(stringResource(R.string.opt_no), stringResource(R.string.opt_yes)), if (gpsOn) 1 else 0) { onGps(it == 1) }
        if (gpsDenied) Text(stringResource(R.string.gps_denied), style = mono(10, p.accent))

        Label(stringResource(R.string.frm_phone_lens))
        Seg(lenses.map { "${it.id} · ${fmt(it.equiv35Mm.first(), 0)}mm" }, lensIdx, onLens)


        BlackScreen {
            Row {
                Text("id ${lens.id} · ${fmt(phoneDeg, 1)}°", style = mono(10, p.screenDim))
                Spacer(Modifier.weight(1f))
                val status = when {
                    error != null -> stringResource(R.string.fov_state_error)
                    guard.hot -> stringResource(R.string.fov_state_hot)
                    running -> stringResource(R.string.fov_state_live)
                    else -> stringResource(R.string.fov_state_stopped)
                }
                Text(status, style = mono(10, p.accent))
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text(target?.let { fmt(Math.toDegrees(it.hFovRad), 1) } ?: "----", style = mono(40, p.screenText, true))
                Text(" °", style = mono(16, p.screenDim))
            }
            Text(stringResource(R.string.frm_target_caption), style = mono(10, p.screenDim))
            if (target != null && frame != null) {
                val vDeg = Math.toDegrees(2.0 * atan(tan(target.hFovRad / 2.0) / target.aspect))
                Text(
                    stringResource(R.string.frm_aspect_v, fmt(target.aspect, 2), fmt(vDeg, 1)),
                    style = mono(11, p.screenText), modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.frm_frame, fmt(frame.widthFrac * 100, 0), fmt(frame.heightFrac * 100, 0)),
                    style = mono(11, p.accent, true),
                )
                if (frame.padding) Text(stringResource(R.string.frm_padding), style = mono(10, p.accent))
            } else {
                Text(stringResource(R.string.frm_no_target), style = mono(10, p.accent), modifier = Modifier.padding(top = 4.dp))
            }
            when (shot) {
                is ShotUi.Saved -> Text(stringResource(R.string.frm_saved, shot.name), style = mono(10, p.screenText), modifier = Modifier.padding(top = 4.dp))
                is ShotUi.Error -> Text(stringResource(R.string.frm_capture_error, shot.message), style = mono(10, p.accent), modifier = Modifier.padding(top = 4.dp))
                else -> {}
            }
            orientation?.let { Text(it, style = mono(10, p.screenText), modifier = Modifier.padding(top = 4.dp)) }
            val derived = !framing.isCustom && (framing.isFixedLens || framing.camera?.modes?.getOrNull(framing.modeIdx)?.derived == true)
            if (derived) Text(stringResource(R.string.frm_derived), style = mono(9, p.accent), modifier = Modifier.padding(top = 4.dp))
            val note = when {
                error != null -> stringResource(R.string.fov_error_cam, error)
                guard.hot -> stringResource(R.string.fov_hot_msg)
                reason == Reason.IDLE -> stringResource(R.string.fov_idle_msg)
                reason == Reason.MAX_RUN -> stringResource(R.string.fov_max_msg)
                guard.warm -> stringResource(R.string.fov_warm_msg)
                else -> null
            }
            note?.let { Text(it, style = mono(10, p.accent), modifier = Modifier.padding(top = 4.dp)) }
            Text(
                stringResource(if (verified) R.string.frm_verified else R.string.frm_unverified),
                style = mono(9, if (verified) p.screenDim else p.accent), modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

private fun fmt(v: Double, digits: Int) = String.format(Locale.ROOT, "%.${digits}f", v)

/** Compensación de exposición para mostrar: «EV +0.7» (0 = automática). */
internal fun evText(index: Int, step: Double): String =
    if (index == 0) "EV 0" else String.format(Locale.ROOT, "EV %+.1f", index * step)

/** Tecla chica para sumar o restar un paso. */
@Composable
private fun StepKey(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(width = 48.dp, height = 40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            .background(p.keyBg.copy(alpha = if (enabled) 1f else 0.4f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = mono(16, p.keyText, true)) }
}

private fun principalsExist(framing: FramingState) = framing.principalCount > 0

/** Lista desplazable para elegir entre muchas opciones (cámaras, modos). */
@Composable
internal fun PickerDialog(title: String, items: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.body,
        title = { Text(title, style = mono(14, p.text, true)) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                itemsIndexed(items) { i, name ->
                    Text(
                        name,
                        style = mono(13, if (i == selected) p.accent else p.text, i == selected),
                        modifier = Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 11.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), style = mono(13, p.text)) }
        },
    )
}
