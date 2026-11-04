package app.mado.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mado.R
import app.mado.data.ProjectBook
import app.mado.data.ShotEntry
import app.mado.data.ShotLog
import kotlinx.coroutines.launch

/** Proyectos y locaciones del scout: altas, nombres, dirección, notas de logística y las escenas con sus planos. */
@Composable
fun ProjectsScreen(book: ProjectBook, shots: List<ShotEntry>, onBook: (ProjectBook) -> Unit, onShots: () -> Unit = {}) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf<String?>(null) }       // "project" o "place"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(stringResource(R.string.proj_projects))
        if (book.projects.isEmpty()) Text(stringResource(R.string.proj_none), style = mono(11, p.text.copy(alpha = 0.7f)))
        else Seg(book.projects.map { it.name }, book.projects.indexOfFirst { it.id == book.activeProject }) { book.projects.getOrNull(it)?.let { pr -> onBook(book.selectProject(pr.id)) } }
        var newProject by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { TextInput(newProject, stringResource(R.string.proj_new_hint)) { newProject = it } }
            SmallKey("+") { if (newProject.isNotBlank()) { onBook(book.addProject(newProject)); newProject = "" } }
        }

        val project = book.project
        if (project != null) {
            Label(stringResource(R.string.proj_name))
            TextInput(project.name, "") { onBook(book.renameProject(project.id, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallKey(stringResource(R.string.proj_delete_project)) { confirm = "project" } }

            Label(stringResource(R.string.proj_places))
            if (project.places.isEmpty()) Text(stringResource(R.string.proj_no_places), style = mono(11, p.text.copy(alpha = 0.7f)))
            else Seg(project.places.map { it.name }, project.places.indexOfFirst { it.id == book.activePlace }) { project.places.getOrNull(it)?.let { pl -> onBook(book.selectPlace(pl.id)) } }
            var newPlace by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { TextInput(newPlace, stringResource(R.string.proj_place_hint)) { newPlace = it } }
                SmallKey("+") { if (newPlace.isNotBlank()) { onBook(book.addPlace(project.id, newPlace)); newPlace = "" } }
            }

            val place = book.place
            if (place != null) {
                Label(stringResource(R.string.proj_name))
                TextInput(place.name, "") { onBook(book.updatePlace(project.id, place.copy(name = it))) }
                Label(stringResource(R.string.proj_address))
                TextInput(place.address, stringResource(R.string.proj_address_hint), maxLen = 120) { onBook(book.updatePlace(project.id, place.copy(address = it))) }
                Label(stringResource(R.string.proj_notes))
                TextInput(place.notes, stringResource(R.string.proj_notes_hint), maxLen = 400, singleLine = false) { onBook(book.updatePlace(project.id, place.copy(notes = it))) }
                Row { SmallKey(stringResource(R.string.proj_delete_place)) { confirm = "place" } }

                Label(stringResource(R.string.proj_scenes))
                val scenes = ShotLog.scenesFor(shots, place.id)
                if (scenes.isEmpty()) Text(stringResource(R.string.proj_no_shots), style = mono(11, p.text.copy(alpha = 0.7f)))
                else scenes.forEach { (scene, n) ->
                    Text("${scene.ifEmpty { "—" }} · ${stringResource(R.string.proj_shots_count, n)}", style = mono(13, p.text), modifier = Modifier.padding(vertical = 2.dp))
                }
                if (scenes.isNotEmpty()) {
                    Row { SmallKey(stringResource(R.string.proj_view_shots), onShots) }
                    ExportSection(project.name, place, shots)
                }
            }
        }
    }

    confirm?.let { what ->
        val project = book.project; val place = book.place
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = p.body,
            title = { Text(stringResource(if (what == "project") R.string.proj_delete_project else R.string.proj_delete_place), style = mono(15, p.text, true)) },
            text = { Text(stringResource(R.string.proj_delete_note), style = mono(12, p.text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (what == "project" && project != null) onBook(book.deleteProject(project.id))
                    if (what == "place" && project != null && place != null) onBook(book.deletePlace(project.id, place.id))
                    confirm = null
                }) { Text(stringResource(R.string.proj_delete_yes), style = mono(13, p.accent, true)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_close), style = mono(13, p.text)) } },
        )
    }
}

/** Proyecto y locación activos (con su lista para cambiarlos) y atajos a las escenas ya usadas en esa locación. */
@Composable
internal fun ProjectPicker(
    book: ProjectBook, shots: List<ShotEntry>, framing: FramingState,
    onBook: (ProjectBook) -> Unit, onManage: () -> Unit,
) {
    val p = LocalPalette.current
    Label(stringResource(R.string.frm_project))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var pickProject by remember { mutableStateOf(false) }
        var pickPlace by remember { mutableStateOf(false) }
        val noneLabel = stringResource(R.string.frm_project_none)
        val projectName = book.project?.name ?: noneLabel
        val places = book.project?.places.orEmpty()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Seg(listOf("$projectName  ▾"), 0) { if (book.projects.isNotEmpty()) pickProject = true else onManage() }
            if (book.project != null) Seg(listOf("${book.place?.name ?: stringResource(R.string.frm_place_none)}  ▾"), 0) { if (places.isNotEmpty()) pickPlace = true else onManage() }
        }
        SmallKey(stringResource(R.string.frm_manage), onManage)
        if (pickProject) PickerDialog(
            title = stringResource(R.string.frm_project), items = book.projects.map { it.name }, selected = book.projects.indexOfFirst { it.id == book.activeProject },
            onPick = { onBook(book.selectProject(book.projects[it].id)) }, onDismiss = { pickProject = false },
        )
        if (pickPlace) PickerDialog(
            title = stringResource(R.string.frm_place), items = places.map { it.name }, selected = places.indexOfFirst { it.id == book.activePlace },
            onPick = { onBook(book.selectPlace(places[it].id)) }, onDismiss = { pickPlace = false },
        )
    }
    val scenes = book.place?.let { ShotLog.scenesFor(shots, it.id) }.orEmpty().filter { it.first.isNotEmpty() }
    if (scenes.isNotEmpty()) {
        Text(stringResource(R.string.frm_scene_pick), style = mono(10, p.text.copy(alpha = 0.7f)))
        Seg(scenes.map { it.first }, scenes.indexOfFirst { it.first.equals(framing.scene.trim(), ignoreCase = true) }) { i ->
            framing.scene = scenes[i].first
            framing.shotNo = ShotLog.nextShotNo(shots, book.place?.id, scenes[i].first).coerceAtMost(999)
        }
    }
}

/** Hoja de locaciones: PDF con plantilla y color a elegir, y los datos en CSV; se guarda en Descargas/Mado y se comparte. */
@Composable
private fun ExportSection(projectName: String, place: app.mado.data.Place, shots: List<ShotEntry>) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var template by remember { mutableStateOf(0) }
    var accent by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val summaryFmt = stringResource(R.string.sheet_summary)
    val labels = app.mado.data.SheetPdf.Labels(
        shot = stringResource(R.string.sheet_shot), address = stringResource(R.string.proj_address), notes = stringResource(R.string.proj_notes),
        generated = stringResource(R.string.sheet_generated),
        summary = { n, sc -> String.format(summaryFmt, n, sc) },
        cameras = stringResource(R.string.sheet_cameras), page = stringResource(R.string.sheet_page), scene = stringResource(R.string.frm_scene_label),
        noPhoto = stringResource(R.string.sheet_no_photo),
    )
    val errorText = stringResource(R.string.proj_export_error)
    val workingText = stringResource(R.string.proj_working)
    val savedFmt = stringResource(R.string.proj_saved)
    val shareLabel = stringResource(R.string.action_share)

    fun run(pdf: Boolean) {
        if (busy) return
        busy = true; status = workingText
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching {
                val sheet = app.mado.data.LocationSheets.build(projectName, place, ShotLog.read(ctx))
                val base = app.mado.data.FileExport.safeName("Mado_${projectName}_${place.name}").ifEmpty { "Mado_location" } +
                    "_" + java.time.LocalDate.now().toString().replace("-", "")
                if (pdf) app.mado.data.FileExport.saveFile(ctx, "$base.pdf", "application/pdf") {
                    app.mado.data.SheetPdf.write(ctx, sheet, if (template == 0) app.mado.data.SheetPdf.Template.LIST else app.mado.data.SheetPdf.Template.LARGE, app.mado.data.SheetPdf.ACCENTS[accent], labels, it)
                } else app.mado.data.FileExport.saveFile(ctx, "$base.csv", "text/csv") { it.write(app.mado.data.LocationSheets.csv(sheet).toByteArray(Charsets.UTF_8)) }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                busy = false
                result.onSuccess { saved ->
                    status = String.format(savedFmt, saved.displayPath)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = if (pdf) "application/pdf" else "text/csv"
                        putExtra(android.content.Intent.EXTRA_STREAM, saved.uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(android.content.Intent.createChooser(send, shareLabel))
                }.onFailure { status = "$errorText ${it.message.orEmpty()}" }
            }
        }
    }

    Label(stringResource(R.string.proj_export))
    Text(stringResource(R.string.proj_template), style = mono(10, p.text.copy(alpha = 0.7f)))
    Seg(listOf(stringResource(R.string.proj_template_list), stringResource(R.string.proj_template_large)), template) { template = it }
    Text(stringResource(R.string.proj_accent), style = mono(10, p.text.copy(alpha = 0.7f)))
    Seg(listOf(R.string.proj_accent_orange, R.string.proj_accent_blue, R.string.proj_accent_green, R.string.proj_accent_graphite).map { stringResource(it) }, accent) { accent = it }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WideKey(stringResource(R.string.proj_make_pdf), true, Modifier.weight(1f)) { run(true) }
        WideKey(stringResource(R.string.proj_make_csv), false, Modifier.weight(1f)) { run(false) }
    }
    status?.let { Text(it, style = mono(11, p.text)) }
}
