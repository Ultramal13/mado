package app.mado.data

import org.json.JSONArray
import org.json.JSONObject

/** Lee el JSON de cámaras (formato de `camaras_esquema.json`). Tolerante: ignora lo incompleto, no inventa nada. */
object CameraDbParser {

    fun parse(json: String): CameraDb {
        val root = JSONObject(json)
        return CameraDb(
            schemaVersion = root.optInt("schema_version", 1),
            cameras = root.optJSONArray("camaras").objects().map(::camera),
            lensSets = root.optJSONArray("juegos_de_lentes").objects().map(::lensSet),
            phoneProfiles = root.optJSONArray("perfiles_telefono").objects().map(::phoneProfile),
        )
    }

    private fun camera(o: JSONObject): Camera {
        val all = o.optJSONArray("modos").objects()
        val modes = all.mapNotNull(::mode)
        return Camera(
            id = o.getString("id"),
            brand = o.optString("marca"),
            model = o.optString("modelo"),
            type = o.optString("tipo", "lente_intercambiable"),
            mount = o.optStringOrNull("montura"),
            modes = modes,
            builtInLenses = o.optJSONArray("lentes_integradas").objects().mapNotNull(::builtIn),
            incompleteModes = all.size - modes.size,
            priority = if (o.has("principal_orden") && !o.isNull("principal_orden")) o.optInt("principal_orden") else null,
        )
    }

    private fun mode(o: JSONObject): RecordingMode? {
        val w = o.optDoubleOrNull("ancho_activo_mm") ?: return null
        val h = o.optDoubleOrNull("alto_activo_mm") ?: return null
        if (w <= 0 || h <= 0) return null
        return RecordingMode(o.getString("id"), o.optString("nombre", o.getString("id")), w, h, source(o.optJSONObject("fuente")), o.optBoolean("derivado", false))
    }

    private fun builtIn(o: JSONObject): BuiltInLens? {
        val f = o.optDoubleOrNull("focal_equivalente_ff_mm") ?: return null
        return BuiltInLens(o.getString("id"), o.optString("nombre", o.getString("id")), f, source(o.optJSONObject("fuente")))
    }

    private fun lensSet(o: JSONObject) = LensSet(
        id = o.getString("id"),
        name = o.optString("nombre"),
        mount = o.optStringOrNull("montura"),
        squeeze = o.optDoubleOrNull("squeeze") ?: 1.0,
        focalsMm = o.optJSONArray("focales_mm")?.let { a -> (0 until a.length()).map { a.getDouble(it) } }.orEmpty(),
    )

    private fun phoneProfile(o: JSONObject) = PhoneProfile(
        buildModel = o.optString("modelo_build"),
        name = o.optString("nombre_comercial"),
        lenses = o.optJSONArray("lentes").objects().map {
            PhoneLensProfile(
                camera2Id = it.optString("camera2_id", it.optString("id")),
                measuredHFovDeg = it.optDoubleOrNull("fov_horizontal_medido_grados"),
                reportedHFovDeg = it.optDoubleOrNull("fov_horizontal_reportado_grados"),
            )
        },
    )

    private fun source(o: JSONObject?) = o?.let { Source(it.optString("texto"), it.optStringOrNull("url")) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeUnless { it.isNaN() }
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else optString(k).ifBlank { null }
}
