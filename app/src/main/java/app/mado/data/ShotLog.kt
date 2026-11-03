package app.mado.data

import org.json.JSONArray

/** Una foto del registro `captures.json` (cada plano guarda dos archivos, `_marco` y `_plano`, con los mismos metadatos). */
data class ShotEntry(
    val shotKey: String,
    val file: String,
    val uri: String,
    val projectId: String?,
    val placeId: String?,
    val scene: String,
    val shotNo: Int,
)

object ShotLog {
    fun parse(json: String?): List<ShotEntry> = try {
        val a = JSONArray(json ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val meta = o.optJSONObject("meta") ?: return@mapNotNull null
            val file = o.optString("file").ifBlank { return@mapNotNull null }
            ShotEntry(
                shotKey = file.removeSuffix(".jpg").removeSuffix("_marco").removeSuffix("_plano"),
                file = file, uri = o.optString("uri"),
                projectId = meta.optJSONObject("project")?.optString("id")?.ifBlank { null },
                placeId = meta.optJSONObject("place")?.optString("id")?.ifBlank { null },
                scene = meta.optString("scene").trim(), shotNo = meta.optInt("shot_no", 0),
            )
        }
    } catch (_: Exception) { emptyList() }

    fun read(context: android.content.Context): List<ShotEntry> =
        parse(runCatching { context.filesDir.resolve("captures.json").readText() }.getOrNull())

    /** Planos (no archivos) de una locación agrupados por escena, en orden de aparición: escena → cantidad de planos. */
    fun scenesFor(entries: List<ShotEntry>, placeId: String?): List<Pair<String, Int>> =
        entries.filter { it.placeId == placeId }.distinctBy { it.shotKey }
            .groupBy { it.scene.lowercase() }
            .map { (_, v) -> v.first().scene to v.size }

    /** Número de plano siguiente para una escena de la locación (máximo anotado + 1). */
    fun nextShotNo(entries: List<ShotEntry>, placeId: String?, scene: String): Int =
        (entries.filter { it.placeId == placeId && it.scene.equals(scene.trim(), ignoreCase = true) }.maxOfOrNull { it.shotNo } ?: 0) + 1
}
