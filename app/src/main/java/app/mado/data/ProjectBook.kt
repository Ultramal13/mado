package app.mado.data

import org.json.JSONArray
import org.json.JSONObject

/** Un sitio donde se hace el scout (estudio, casa…): dirección y notas de logística (estacionamiento, camarín, arte). */
data class Place(val id: String, val name: String, val address: String = "", val notes: String = "")

data class Project(val id: String, val name: String, val places: List<Place> = emptyList())

/**
 * Proyectos → locaciones, con la elegida en este momento. Inmutable: cada cambio devuelve un libro nuevo.
 * Las fotos referencian proyecto y locación por id (van en los metadatos de cada foto), y la escena y el número de plano
 * siguen siendo texto libre dentro de la locación.
 */
data class ProjectBook(
    val projects: List<Project> = emptyList(),
    val activeProject: String? = null,
    val activePlace: String? = null,
) {
    val project: Project? get() = projects.firstOrNull { it.id == activeProject }
    val place: Place? get() = project?.places?.firstOrNull { it.id == activePlace }

    fun addProject(name: String, id: String = newId()): ProjectBook =
        copy(projects = projects + Project(id, name.trim().ifEmpty { "—" }), activeProject = id, activePlace = null)

    fun renameProject(id: String, name: String) = copy(projects = projects.map { if (it.id == id) it.copy(name = name) else it })

    fun deleteProject(id: String): ProjectBook {
        val rest = projects.filterNot { it.id == id }
        if (activeProject != id) return copy(projects = rest)
        val next = rest.firstOrNull()
        return copy(projects = rest, activeProject = next?.id, activePlace = next?.places?.firstOrNull()?.id)
    }

    fun selectProject(id: String?): ProjectBook {
        val p = projects.firstOrNull { it.id == id }
        return copy(activeProject = p?.id, activePlace = p?.places?.firstOrNull()?.id)
    }

    fun addPlace(projectId: String, name: String, id: String = newId()): ProjectBook {
        if (projects.none { it.id == projectId }) return this
        val place = Place(id, name.trim().ifEmpty { "—" })
        return copy(
            projects = projects.map { if (it.id == projectId) it.copy(places = it.places + place) else it },
            activeProject = projectId, activePlace = id,
        )
    }

    fun updatePlace(projectId: String, place: Place) = copy(
        projects = projects.map { p -> if (p.id == projectId) p.copy(places = p.places.map { if (it.id == place.id) place else it }) else p },
    )

    fun deletePlace(projectId: String, placeId: String): ProjectBook {
        val updated = projects.map { p -> if (p.id == projectId) p.copy(places = p.places.filterNot { it.id == placeId }) else p }
        val book = copy(projects = updated)
        return if (activePlace == placeId) book.copy(activePlace = updated.firstOrNull { it.id == projectId }?.places?.firstOrNull()?.id) else book
    }

    fun selectPlace(id: String?): ProjectBook = if (project?.places?.any { it.id == id } == true) copy(activePlace = id) else this

    fun toJson(): String = JSONObject().apply {
        put("active_project", activeProject); put("active_place", activePlace)
        put("projects", JSONArray(projects.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("places", JSONArray(p.places.map {
                JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("notes", it.notes)
            }))
        }))
    }.toString(2)

    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString().take(8)

        /** Tolerante: un archivo vacío o dañado da un libro vacío, sin tirar la app. */
        fun fromJson(json: String?): ProjectBook = try {
            val o = JSONObject(json ?: return ProjectBook())
            val projects = o.optJSONArray("projects")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::project) }
            }.orEmpty()
            val ap = o.optString("active_project").takeIf { id -> projects.any { it.id == id } }
            val place = projects.firstOrNull { it.id == ap }?.places
            val apl = o.optString("active_place").takeIf { id -> place?.any { it.id == id } == true }
            ProjectBook(projects, ap, apl)
        } catch (_: Exception) { ProjectBook() }

        private fun project(o: JSONObject): Project? {
            val id = o.optString("id").ifBlank { return null }
            val places = o.optJSONArray("places")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { Place(it.optString("id").ifBlank { return@mapNotNull null }, it.optString("name"), it.optString("address"), it.optString("notes")) }
                }
            }.orEmpty()
            return Project(id, o.optString("name"), places)
        }
    }
}

/** Guarda el libro en un archivo (`files/projects.json`). */
class ProjectStore(private val file: java.io.File) {
    fun load(): ProjectBook = ProjectBook.fromJson(runCatching { file.readText() }.getOrNull())
    fun save(book: ProjectBook) { runCatching { file.writeText(book.toJson()) } }
}
