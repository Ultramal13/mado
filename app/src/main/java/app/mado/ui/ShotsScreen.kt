package app.mado.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mado.R
import app.mado.data.LocationSheets
import app.mado.data.OperatorData
import app.mado.data.ProjectBook
import app.mado.data.SheetShot
import app.mado.data.ShotEdit
import app.mado.data.ShotLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Los planos de la locación activa, por escena, con su foto: tocar uno permite corregir sus datos, abrir la foto o borrarlo. */
@Composable
fun ShotsScreen(book: ProjectBook) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<SheetShot?>(null) }
    val project = book.project; val place = book.place
    val sheet = remember(tick, project, place) { if (project != null && place != null) LocationSheets.build(project.name, place, ShotLog.read(ctx)) else null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sheet == null || sheet.scenes.isEmpty()) {
            Text(stringResource(R.string.shots_empty), style = mono(12, p.text.copy(alpha = 0.7f)))
        } else {
            Text("${project?.name} · ${place?.name}", style = mono(11, p.text.copy(alpha = 0.7f)))
            sheet.scenes.forEach { scene ->
                Text("${scene.name.ifEmpty { "—" }} · ${stringResource(R.string.proj_shots_count, scene.shots.size)}", style = mono(13, p.accent, true), modifier = Modifier.padding(top = 6.dp))
                scene.shots.forEach { s -> ShotRow(s) { editing = s } }
            }
        }
    }
    editing?.let { s -> ShotEditDialog(s, onDone = { editing = null; tick++ }) }
}

@Composable
private fun ShotRow(s: SheetShot, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.keyBg).clickable(onClick = onClick).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
    ) {
        Thumb(s.planoUri ?: s.marcoUri)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val focal = s.focalMm?.let { (if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) + if (s.focalEquivalent) " mm eq" else " mm" }
            Text(listOfNotNull("${stringResource(R.string.sheet_shot)} ${s.shotNo}", focal).joinToString(" · "), style = mono(13, p.keyText, true))
            Text(LocationSheets.localTime(s.time), style = mono(10, p.keyText.copy(alpha = 0.6f)))
            if (s.camera.isNotEmpty()) Text(s.camera, style = mono(10, p.keyText))
            if (s.operator.isNotEmpty()) Text(s.operator, style = mono(11, p.keyText, true))
            if (s.notes.isNotEmpty()) Text(s.notes, style = mono(10, p.accent))
        }
    }
}

/** Miniatura de una foto, leída en segundo plano a poca resolución. */
@Composable
private fun Thumb(uri: String?) {
    val ctx = LocalContext.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val u = android.net.Uri.parse(uri)
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, b) }
                var sample = 1
                while (b.outWidth / (sample * 2) >= 300) sample *= 2
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    val p = LocalPalette.current
    Box(Modifier.width(96.dp).size(96.dp, 120.dp).clip(RoundedCornerShape(6.dp)).background(p.body), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

@Composable
private fun ShotEditDialog(s: SheetShot, onDone: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var scene by remember { mutableStateOf(s.scene) }
    var no by remember { mutableIntStateOf(s.shotNo) }
    var fps by remember { mutableStateOf(s.op.fps) }; var shutter by remember { mutableStateOf(s.op.shutter) }
    var iso by remember { mutableStateOf(s.op.iso) }; var wb by remember { mutableStateOf(s.op.wb) }
    var pp by remember { mutableStateOf(s.op.pp) }; var note by remember { mutableStateOf(s.op.note) }
    var external by remember { mutableStateOf(s.op.external) }
    var confirmDelete by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = p.body,
        title = { Text("${stringResource(R.string.sheet_shot)} ${s.shotNo} · ${LocationSheets.localTime(s.time)}", style = mono(14, p.text, true)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(stringResource(R.string.frm_scene_label)); TextInput(scene, "") { scene = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.frm_shot_no), style = mono(11, p.text), modifier = Modifier.weight(1f))
                    SmallKey("−") { if (no > 1) no-- }
                    Text("$no", style = mono(15, p.text, true))
                    SmallKey("+") { if (no < 999) no++ }
                }
                Label(stringResource(R.string.frm_fps)); TextInput(fps, "29.97", maxLen = 8) { fps = it.filter { c -> c.isDigit() || c == '.' } }
                Label(stringResource(R.string.frm_shutter)); TextInput(shutter, "1/60", maxLen = 10) { shutter = it }
                Label(stringResource(R.string.frm_iso)); TextInput(iso, "800", maxLen = 8) { iso = it.filter { c -> c.isDigit() } }
                Label(stringResource(R.string.frm_wb)); TextInput(wb, "5600", maxLen = 8) { wb = it.filter { c -> c.isDigit() } }
                Label(stringResource(R.string.frm_pp)); TextInput(pp, "PP10", maxLen = 10) { pp = it }
                Label(stringResource(R.string.frm_op_note)); TextInput(note, "", maxLen = 60) { note = it }
                Label(stringResource(R.string.frm_op_external)); TextInput(external, "", maxLen = 60) { external = it }
                Text(stringResource(R.string.shot_edit_note), style = mono(10, p.text.copy(alpha = 0.7f)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallKey(stringResource(R.string.shot_open_photo)) {
                        (s.planoUri ?: s.marcoUri)?.let { u ->
                            val view = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(u), "image/jpeg"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { ctx.startActivity(view) }
                        }
                    }
                    SmallKey(stringResource(R.string.shot_delete)) { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                ShotLog.edit(ctx, s.key, ShotEdit(scene, no, OperatorData(fps, shutter, iso, wb, pp, note, external)))
                onDone()
            }) { Text(stringResource(R.string.shot_save), style = mono(13, p.accent, true)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text(stringResource(R.string.shot_cancel), style = mono(13, p.text)) } },
    )
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = p.body,
        title = { Text(stringResource(R.string.shot_delete), style = mono(15, p.text, true)) },
        text = { Text(stringResource(R.string.shot_delete_note), style = mono(12, p.text)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { ShotLog.delete(ctx, s.key, deleteFiles = true); confirmDelete = false; onDone() }) {
                Text(stringResource(R.string.shot_delete_files), style = mono(13, p.accent, true))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { ShotLog.delete(ctx, s.key, deleteFiles = false); confirmDelete = false; onDone() }) {
                Text(stringResource(R.string.shot_remove_sheet), style = mono(13, p.text))
            }
        },
    )
}
