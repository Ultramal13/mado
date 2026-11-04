package app.mado.data

import org.json.JSONArray
import org.json.JSONObject

/** Ajustes para el operador de una toma, tal como se escriben en el panel (texto), para mostrarlos y corregirlos. */
data class OperatorData(
    val fps: String = "", val shutter: String = "", val iso: String = "", val wb: String = "",
    val pp: String = "", val note: String = "", val external: String = "",
) {
    /** «29.97p · 1/60 · ISO 800 · 5600K» con lo que haya. */
    fun settingsLine(): String = listOfNotNull(
        fps.trim().takeIf { it.isNotEmpty() }?.let { "${it}p" }, shutter.trim().takeIf { it.isNotEmpty() },
        iso.trim().takeIf { it.isNotEmpty() }?.let { "ISO $it" }, wb.trim().takeIf { it.isNotEmpty() }?.let { "${it}K" },
    ).joinToString(" · ")

    /** Picture Profile, nota libre y grabación externa («EXT …»), separados por « · ». */
    fun notesLine(): String = listOfNotNull(
        pp.trim().takeIf { it.isNotEmpty() }, note.trim().takeIf { it.isNotEmpty() }, external.trim().takeIf { it.isNotEmpty() }?.let { "EXT $it" },
    ).joinToString(" · ")

    val isEmpty: Boolean get() = this == OperatorData()
}

/** Corrección de los datos de una toma ya guardada: escena, número de plano y ajustes del operador. */
data class ShotEdit(val scene: String, val shotNo: Int, val op: OperatorData)

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
    val op: OperatorData = OperatorData(),
    val headingDeg: Double? = null,
) {
    val operator: String get() = op.settingsLine()
    val notes: String get() = op.notesLine()
}

object ShotLog {
    fun parse(json: String?): List<ShotEntry> = try {
        val a = JSONArray(json ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val meta = o.optJSONObject("meta") ?: return@mapNotNull null
            val file = o.optString("file").ifBlank { return@mapNotNull null }
            ShotEntry(
                shotKey = keyOf(file),
                file = file, uri = o.optString("uri"),
                projectId = meta.optJSONObject("project")?.optString("id")?.ifBlank { null },
                placeId = meta.optJSONObject("place")?.optString("id")?.ifBlank { null },
                scene = meta.optString("scene").trim(), shotNo = meta.optInt("shot_no", 0),
                time = meta.optString("time"),
                camera = meta.optJSONObject("target")?.optString("label").orEmpty(),
                focalMm = meta.optJSONObject("target")?.let { if (it.isNull("focal_mm")) null else it.optDouble("focal_mm").takeIf { v -> !v.isNaN() } },
                focalEquivalent = meta.optJSONObject("target")?.optBoolean("focal_equivalent", false) ?: false,
                op = operatorData(meta.optJSONObject("operator")),
                headingDeg = meta.optJSONObject("orientation")?.let { if (it.isNull("heading_deg_magnetic")) null else it.optDouble("heading_deg_magnetic").takeIf { v -> !v.isNaN() } },
            )
        }
    } catch (_: Exception) { emptyList() }

    fun keyOf(file: String) = file.removeSuffix(".jpg").removeSuffix("_marco").removeSuffix("_plano")

    private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    internal fun operatorData(o: JSONObject?): OperatorData {
        if (o == null) return OperatorData()
        fun d(k: String) = if (o.isNull(k)) "" else o.optDouble(k).takeIf { !it.isNaN() }?.let(::num).orEmpty()
        fun s(k: String) = if (o.isNull(k)) "" else o.optString(k).trim().takeIf { it != "null" }.orEmpty()
        return OperatorData(d("fps"), s("shutter"), d("iso"), d("white_balance_k"), s("picture_profile"), s("note"), s("external_recording"))
    }

    /**
     * Devuelve el registro con la toma [key] corregida (en sus dos archivos), o null si no está. La barra y la etiqueta que ya van
     * dibujadas en las fotos no cambian: la hoja de locaciones y el CSV usan los datos corregidos.
     */
    fun applyEdit(json: String, key: String, edit: ShotEdit): String? {
        val a = JSONArray(json)
        var found = false
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (keyOf(o.optString("file")) != key) continue
            val meta = o.optJSONObject("meta") ?: continue
            meta.put("scene", edit.scene.trim()); meta.put("shot_no", edit.shotNo)
            val op = edit.op
            if (op.isEmpty) meta.remove("operator") else meta.put("operator", JSONObject()
                .put("fps", op.fps.trim().toDoubleOrNull() ?: JSONObject.NULL).put("shutter", op.shutter.trim().ifEmpty { JSONObject.NULL })
                .put("iso", op.iso.trim().toIntOrNull() ?: JSONObject.NULL).put("white_balance_k", op.wb.trim().toIntOrNull() ?: JSONObject.NULL)
                .put("picture_profile", op.pp.trim().ifEmpty { JSONObject.NULL }).put("note", op.note.trim().ifEmpty { JSONObject.NULL })
                .put("external_recording", op.external.trim().ifEmpty { JSONObject.NULL }))
            found = true
        }
        return if (found) a.toString(2) else null
    }

    /** Quita la toma [key] del registro: devuelve el registro nuevo y las direcciones de sus archivos, o null si no está. */
    fun remove(json: String, key: String): Pair<String, List<String>>? {
        val a = JSONArray(json)
        val kept = JSONArray(); val uris = ArrayList<String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i)
            if (o != null && keyOf(o.optString("file")) == key) uris.add(o.optString("uri")) else kept.put(a.get(i))
        }
        return if (uris.isEmpty()) null else kept.toString(2) to uris
    }

    fun read(context: android.content.Context): List<ShotEntry> =
        parse(runCatching { context.filesDir.resolve("captures.json").readText() }.getOrNull())

    /** Corrige una toma en el archivo del registro. */
    fun edit(context: android.content.Context, key: String, edit: ShotEdit): Boolean = runCatching {
        val f = context.filesDir.resolve("captures.json")
        applyEdit(f.readText(), key, edit)?.also { f.writeText(it) } != null
    }.getOrDefault(false)

    /** Quita una toma del registro y, si [deleteFiles], también borra sus fotos de la galería (las creó Mado, no pide permiso). */
    fun delete(context: android.content.Context, key: String, deleteFiles: Boolean): Boolean = runCatching {
        val f = context.filesDir.resolve("captures.json")
        val (json, uris) = remove(f.readText(), key) ?: return@runCatching false
        f.writeText(json)
        if (deleteFiles) uris.forEach { runCatching { context.contentResolver.delete(android.net.Uri.parse(it), null, null) } }
        true
    }.getOrDefault(false)

    /** Planos (no archivos) de una locación agrupados por escena, en orden de aparición: escena → cantidad de planos. */
    fun scenesFor(entries: List<ShotEntry>, placeId: String?): List<Pair<String, Int>> =
        entries.filter { it.placeId == placeId }.distinctBy { it.shotKey }
            .groupBy { it.scene.lowercase() }
            .map { (_, v) -> v.first().scene to v.size }

    /** Número de plano siguiente para una escena de la locación (máximo anotado + 1). */
    fun nextShotNo(entries: List<ShotEntry>, placeId: String?, scene: String): Int =
        (entries.filter { it.placeId == placeId && it.scene.equals(scene.trim(), ignoreCase = true) }.maxOfOrNull { it.shotNo } ?: 0) + 1
}
