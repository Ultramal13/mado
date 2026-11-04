package app.mado.data

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Un plano de la hoja: foto «plano» (recortada) y/o «marco» (con los recuadros) más los datos para el operador. */
data class SheetShot(
    val key: String, val shotNo: Int, val scene: String, val time: String, val camera: String, val focalMm: Double?, val focalEquivalent: Boolean,
    val operator: String, val notes: String, val headingDeg: Double?, val marcoUri: String?, val planoUri: String?,
)

data class SheetScene(val name: String, val shots: List<SheetShot>)

/** Todo lo de una locación listo para dibujar: escenas en orden de aparición y planos en orden de número. */
data class LocationSheet(val projectName: String, val place: Place, val scenes: List<SheetScene>) {
    val shotCount: Int get() = scenes.sumOf { it.shots.size }
    val cameras: List<String> get() = scenes.flatMap { it.shots }.map { it.camera.substringBefore(" · ") }.filter { it.isNotEmpty() }.distinct()
}

object LocationSheets {
    /** Agrupa los archivos del registro (marco y plano de cada toma) por toma, escena y número de plano. */
    fun build(projectName: String, place: Place, entries: List<ShotEntry>): LocationSheet {
        val shots = entries.filter { it.placeId == place.id }.groupBy { it.shotKey }.map { (key, files) ->
            val first = files.first()
            SheetShot(
                key = key, shotNo = first.shotNo, scene = first.scene, time = first.time, camera = first.camera, focalMm = first.focalMm, focalEquivalent = first.focalEquivalent,
                operator = first.operator, notes = first.notes, headingDeg = first.headingDeg,
                marcoUri = files.firstOrNull { it.file.endsWith("_marco.jpg") }?.uri, planoUri = files.firstOrNull { it.file.endsWith("_plano.jpg") }?.uri,
            )
        }.filter { it.marcoUri != null || it.planoUri != null }
        val scenes = shots.groupBy { it.scene.lowercase() }.values
            // El nombre de la escena es el de su primera toma (si se escribió con otras mayúsculas después, no cambia).
            .map { group -> SheetScene(group.minBy { it.time }.scene, group.sortedWith(compareBy<SheetShot> { it.shotNo }.thenBy { it.time })) }
            .sortedBy { sc -> sc.shots.minOf { it.time } }
        return LocationSheet(projectName, place, scenes)
    }

    /** «2026-11-02 22:02» en la zona horaria del teléfono; si el texto no es una hora válida, se devuelve tal cual. */
    fun localTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) { iso }

    /** Hoja de datos en CSV (una fila por plano) para quien prefiera armar el documento en Keynote, Canva o una planilla. */
    fun csv(sheet: LocationSheet, zone: ZoneId = ZoneId.systemDefault()): String {
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val header = listOf("project", "location", "scene", "shot", "time", "camera", "focal_mm", "operator", "notes", "heading_deg", "plano_file", "marco_file")
        val rows = sheet.scenes.flatMap { sc ->
            sc.shots.map { s ->
                listOf(
                    sheet.projectName, sheet.place.name, sc.name, s.shotNo.toString(), localTime(s.time, zone), s.camera, s.focalMm?.toString().orEmpty(),
                    s.operator, s.notes, s.headingDeg?.let { "%.0f".format(java.util.Locale.ROOT, it) }.orEmpty(),
                    s.key + "_plano.jpg", s.key + "_marco.jpg",
                )
            }
        }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { r -> r.joinToString(",") { cell(it) } } + "\r\n"
    }
}
