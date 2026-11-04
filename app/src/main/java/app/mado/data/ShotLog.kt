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
    /** Hora de la toma (ISO, UTC), cámara y modo simulados, focal, ajustes del operador y rumbo, para la hoja de locaciones. */
    val time: String = "",
    val camera: String = "",
    val focalMm: Double? = null,
    /** La focal es la equivalente full frame de un lente fijo (se muestra «mm eq»). */
    val focalEquivalent: Boolean = false,
    val operator: String = "",
    val notes: String = "",
    val headingDeg: Double? = null,
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
                time = meta.optString("time"),
                camera = meta.optJSONObject("target")?.optString("label").orEmpty(),
                focalMm = meta.optJSONObject("target")?.let { if (it.isNull("focal_mm")) null else it.optDouble("focal_mm").takeIf { v -> !v.isNaN() } },
                focalEquivalent = meta.optJSONObject("target")?.optBoolean("focal_equivalent", false) ?: false,
                operator = operatorSettings(meta.optJSONObject("operator")), notes = operatorNotes(meta.optJSONObject("operator")),
                headingDeg = meta.optJSONObject("orientation")?.let { if (it.isNull("heading_deg_magnetic")) null else it.optDouble("heading_deg_magnetic").takeIf { v -> !v.isNaN() } },
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    /** «29.97p · 1/60 · ISO 800 · 5600K» con lo que haya en los datos del operador. */
    internal fun operatorSettings(o: org.json.JSONObject?): String {
        if (o == null) return ""
        fun d(k: String) = if (o.isNull(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
        return listOfNotNull(
            d("fps")?.let { "${num(it)}p" }, o.optString("shutter").takeIf { it.isNotEmpty() && it != "null" },
            d("iso")?.let { "ISO ${num(it)}" }, d("white_balance_k")?.let { "${num(it)}K" },
        ).joinToString(" · ")
    }

    /** Picture Profile, nota libre y grabación externa («EXT …»), separados por « · ». */
    internal fun operatorNotes(o: org.json.JSONObject?): String {
        if (o == null) return ""
        fun s(k: String) = if (o.isNull(k)) null else o.optString(k).trim().takeIf { it.isNotEmpty() }
        return listOfNotNull(s("picture_profile"), s("note"), s("external_recording")?.let { "EXT $it" }).joinToString(" · ")
    }

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
