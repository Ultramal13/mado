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

/** Proyectos y locaciones del scout: altas, nombres, dirección, notas de logística y las escenas con sus planos. */
@Composable
fun ProjectsScreen(book: ProjectBook, shots: List<ShotEntry>, onBook: (ProjectBook) -> Unit) {
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
