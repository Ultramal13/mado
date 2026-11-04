package app.mado.data

import org.json.JSONArray
import org.json.JSONObject

/** Juegos de lentes propios del usuario (nombre, compresión anamórfica y focales), guardados en `files/kits.json`. */
class KitStore(private val file: java.io.File) {
    fun load(): List<LensSet> = parse(runCatching { file.readText() }.getOrNull())
    fun save(kits: List<LensSet>) { runCatching { file.writeText(toJson(kits)) } }

    companion object {
        fun newId() = "custom_" + java.util.UUID.randomUUID().toString().take(8)

        fun toJson(kits: List<LensSet>): String = JSONArray(kits.map { k ->
            JSONObject().put("id", k.id).put("name", k.name).put("squeeze", k.squeeze).put("focals", JSONArray(k.focalsMm))
        }).toString(2)

        /** Tolerante: un archivo dañado da una lista vacía. */
        fun parse(json: String?): List<LensSet> = try {
            val a = JSONArray(json ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val focals = o.optJSONArray("focals")?.let { f -> (0 until f.length()).map { f.optDouble(it) } }.orEmpty().filter { it > 0 && !it.isNaN() }
                LensSet(id, o.optString("name").ifBlank { id }, null, o.optDouble("squeeze", 1.0).takeIf { it >= 1.0 } ?: 1.0, focals, custom = true)
            }
        } catch (_: Exception) { emptyList() }

        /** «18, 25 35;50» → [18.0, 25.0, 35.0, 50.0] (sin repetidos, de menor a mayor). */
        fun parseFocals(text: String): List<Double> =
            text.split(Regex("[,;\\s]+")).mapNotNull { it.replace("mm", "", ignoreCase = true).toDoubleOrNull() }.filter { it in 4.0..2000.0 }.distinct().sorted()
    }
}
