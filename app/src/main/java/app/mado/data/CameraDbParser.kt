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
        val fixedLens = o.optString("tipo") == "lente_fija"
        val modes = all.mapNotNull { mode(it, fixedLens) }
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
            firmwareHistory = o.optJSONArray("firmware_historial").objects().mapNotNull(::firmware),
            colorProfiles = o.optJSONArray("perfiles_color").objects().mapNotNull(::colorProfile),
            pictureProfiles = o.optJSONArray("perfiles_imagen")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        )
    }

    private fun firmware(o: JSONObject): FirmwareEntry? {
        val v = o.optStringOrNull("version") ?: return null
        return FirmwareEntry(v, o.optStringOrNull("fecha"), o.optString("cambios"), source(o.optJSONObject("fuente")))
    }

    private fun colorProfile(o: JSONObject): ColorProfile? {
        val id = o.optStringOrNull("id") ?: return null
        return ColorProfile(id, o.optString("nombre", id), o.optString("tipo", "estandar"), source(o.optJSONObject("fuente")), o.optStringOrNull("desde_firmware"))
    }

    /** "9:16" o "2.35:1" → 0.5625 / 2.35. */
    private fun parseAspect(s: String?): Double? {
        val p = s?.split(':') ?: return null
        if (p.size != 2) return null
        val a = p[0].trim().toDoubleOrNull() ?: return null
        val b = p[1].trim().toDoubleOrNull() ?: return null
        return if (a > 0 && b > 0) a / b else null
    }

    /**
     * Un modo vale si trae área del sensor (ancho y alto en mm) o, solo en cámaras de lente fija, si publica su aspecto:
     * ahí el FOV sale de la focal equivalente y el modo solo fija la proporción. Lo demás se descarta, sin inventar medidas.
     */
    private fun mode(o: JSONObject, fixedLens: Boolean): RecordingMode? {
        val w = o.optDoubleOrNull("ancho_activo_mm")?.takeIf { it > 0 }
        val h = o.optDoubleOrNull("alto_activo_mm")?.takeIf { it > 0 }
        val aspect = parseAspect(o.optStringOrNull("relacion_aspecto"))
        if ((w == null || h == null) && !(fixedLens && aspect != null)) return null
        val area = w != null && h != null
        return RecordingMode(
            o.getString("id"), o.optString("nombre", o.getString("id")),
            if (area) w else null, if (area) h else null,
            source(o.optJSONObject("fuente")), o.optBoolean("derivado", false),
            sinceFirmware = o.optStringOrNull("desde_firmware"),
            resolution = o.optStringOrNull("resolucion_px"),
            aspectRatio = if (area) null else aspect,
            fps = o.optJSONArray("fps")?.let { a -> (0 until a.length()).map { a.get(it).toString() } }.orEmpty(),
        )
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
