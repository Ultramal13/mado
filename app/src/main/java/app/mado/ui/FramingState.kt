package app.mado.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mado.data.Camera
import app.mado.data.CameraDb
import app.mado.data.loc
import app.mado.optics.Fov
import app.mado.optics.Guide
import app.mado.optics.Framing

/** Selección del usuario para el modo marco: cámara, modo de grabación, juego de lentes y focal. */
class FramingState(val db: CameraDb) {
    /** Cámaras con datos completos + al final la personalizada (índice == cameras.size). */
    // Las principales (orden del usuario) primero; el resto por nombre.
    val cameras: List<Camera> = db.cameras.filter { it.modes.isNotEmpty() || it.builtInLenses.isNotEmpty() }
        .sortedWith(compareBy<Camera> { it.priority ?: Int.MAX_VALUE }.thenBy { it.displayName.lowercase() })
    val principalCount = cameras.count { it.priority != null }

    /** Índices de `cameras` con chip propio: las principales, más la elegida si no lo es (el resto va en la lista «más cámaras»). */
    val visibleCameras: List<Int> get() {
        val principals = (0 until principalCount).toList()
        return if (principalCount == 0) cameras.indices.toList()
        else if (cameraIdx in cameras.indices && cameraIdx >= principalCount) principals + cameraIdx else principals
    }
    val otherCameras: List<Int> get() = (principalCount until cameras.size).toList()

    var cameraIdx by mutableIntStateOf(if (principalCount > 0) 0 else cameras.size)      // arranca en la primera principal
    var modeIdx by mutableIntStateOf(0)
    /** Firmware instalado por cámara (id → versión) y perfil de color elegido por cámara (id → id del perfil). Sin entrada = el más nuevo / ninguno. */
    var firmwareByCamera by mutableStateOf(mapOf<String, String>())
    var colorByCamera by mutableStateOf(mapOf<String, String>())
    var lensSetIdx by mutableIntStateOf(0)
    var focal by mutableStateOf("50")
    var customW by mutableStateOf("36")
    var customH by mutableStateOf("24")
    var builtInIdx by mutableIntStateOf(0)
    /** Focales del kit que se dibujan a la vez (la actual va resaltada); se suman o quitan desde el panel. */
    var kit by mutableStateOf(listOf(24.0, 35.0, 50.0, 85.0))
    /** Guías de composición activas (se recuerdan entre usos). */
    var guides by mutableStateOf(setOf<Guide>())
    /** Aspecto de entrega (línea sobre el encuadre, p. ej. 16:9 sobre un sensor 3:2); -1 = igual al sensor, sin línea. */
    val deliveryAspects = listOf(16.0 / 9.0 to "16:9", 2.39 to "2.39:1", 1.85 to "1.85:1", 4.0 / 3.0 to "4:3", 1.0 to "1:1", 9.0 / 16.0 to "9:16", 4.0 / 5.0 to "4:5")
    var deliveryIdx by mutableIntStateOf(-1)
    val delivery: Double? get() = deliveryAspects.getOrNull(deliveryIdx)?.first
    /** Escena o lugar de las próximas fotos («暖炉», «cocina») y número de plano dentro de esa escena; se recuerdan. */
    var scene by mutableStateOf("")
    var shotNo by mutableIntStateOf(1)
    /** Aspecto elegido cuando una cámara de lente fija no trae modos con medidas. */
    var fixedAspectIdx by mutableIntStateOf(0)
    val fixedAspects = listOf(16.0 / 9.0 to "16:9", 4.0 / 3.0 to "4:3", 3.0 / 2.0 to "3:2", 2.39 to "2.39:1", 1.0 to "1:1")

    /**
     * Foto fija de lo elegido (por id, no por posición: el catálogo puede cambiar de una versión a otra) para recordarlo
     * entre usos de la app. Las guías de composición se guardan aparte.
     */
    fun snapshot(): String = org.json.JSONObject().apply {
        put("camera", if (isCustom) "custom" else camera?.id)
        put("mode", modes.getOrNull(modeIdx)?.id)
        put("firmware", org.json.JSONObject(firmwareByCamera))
        put("color", org.json.JSONObject(colorByCamera))
        put("lensSet", lensSet?.id)
        put("focal", focal)
        put("kit", org.json.JSONArray(kit))
        put("customW", customW); put("customH", customH)
        put("builtIn", camera?.builtInLenses?.getOrNull(builtInIdx)?.id)
        put("aspect", fixedAspectIdx)
        put("delivery", deliveryIdx)
        put("scene", scene); put("shotNo", shotNo)
    }.toString()

    /** Vuelve a la selección guardada por [snapshot]; lo que ya no exista en el catálogo se ignora y queda el valor por omisión. */
    fun restore(json: String?) {
        if (json.isNullOrBlank()) return
        try {
            val o = org.json.JSONObject(json)
            val cam = o.optString("camera")
            if (cam == "custom") cameraIdx = cameras.size
            else cameras.indexOfFirst { it.id == cam }.takeIf { it >= 0 }?.let { cameraIdx = it }
            o.optJSONObject("firmware")?.let { f -> firmwareByCamera = f.keys().asSequence().associateWith { f.optString(it) }.filterValues { it.isNotBlank() } }
            o.optJSONObject("color")?.let { c -> colorByCamera = c.keys().asSequence().associateWith { c.optString(it) }.filterValues { it.isNotBlank() } }
            if (!isCustom) {
                modes.indexOfFirst { it.id == o.optString("mode") }.takeIf { it >= 0 }?.let { modeIdx = it }
                camera?.builtInLenses?.indexOfFirst { it.id == o.optString("builtIn") }?.takeIf { it >= 0 }?.let { builtInIdx = it }
            }
            db.lensSets.indexOfFirst { it.id == o.optString("lensSet") }.takeIf { it >= 0 }?.let { lensSetIdx = it }
            o.optString("focal").takeIf { it.toDoubleOrNull() != null }?.let { focal = it }
            o.optJSONArray("kit")?.let { a -> List(a.length()) { a.optDouble(it) }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { kit = it } }
            o.optString("customW").takeIf { it.toDoubleOrNull() != null }?.let { customW = it }
            o.optString("customH").takeIf { it.toDoubleOrNull() != null }?.let { customH = it }
            o.optInt("aspect", -1).takeIf { it in fixedAspects.indices }?.let { fixedAspectIdx = it }
            deliveryIdx = o.optInt("delivery", -1).takeIf { it in deliveryAspects.indices } ?: -1
            scene = o.optString("scene", "").take(40)
            shotNo = o.optInt("shotNo", 1).coerceIn(1, 999)
        } catch (_: Exception) { /* guardado dañado: se parte desde cero */ }
    }

    val isCustom get() = cameraIdx >= cameras.size
    val camera: Camera? get() = cameras.getOrNull(cameraIdx)
    val lensSet get() = db.lensSets.getOrNull(lensSetIdx)
    val squeeze get() = lensSet?.squeeze ?: 1.0
    /** Versión de firmware elegida para la cámara actual; null = la más nueva que conocemos (o sin historial). */
    val firmware: String? get() = camera?.let { cam -> firmwareByCamera[cam.id]?.takeIf { it != cam.latestFirmware && cam.firmwareHistory.any { e -> e.version == it } } }

    /** Modos de la cámara actual que existen en ese firmware. */
    val modes: List<app.mado.data.RecordingMode> get() = camera?.modesFor(firmware).orEmpty()

    /** Cambia el firmware de la cámara actual sin perder el modo elegido, si existe en esa versión. */
    fun selectFirmware(version: String) {
        val cam = camera ?: return
        val keep = modes.getOrNull(modeIdx)?.id
        firmwareByCamera = if (version == cam.latestFirmware) firmwareByCamera - cam.id else firmwareByCamera + (cam.id to version)
        modeIdx = modes.indexOfFirst { it.id == keep }.coerceAtLeast(0)
    }

    /** Perfil de color (log, estándar…) elegido para la cámara actual, o null. */
    val colorProfile: app.mado.data.ColorProfile? get() = camera?.let { cam -> colorProfiles.firstOrNull { it.id == colorByCamera[cam.id] } }

    /** Perfiles de color de la cámara actual que existen en el firmware elegido. */
    val colorProfiles: List<app.mado.data.ColorProfile> get() = camera?.colorProfilesFor(firmware).orEmpty()

    fun selectColor(id: String?) {
        val cam = camera ?: return
        colorByCamera = if (id == null) colorByCamera - cam.id else colorByCamera + (cam.id to id)
    }

    val isFixedLens get() = camera?.builtInLenses?.isNotEmpty() == true && (camera?.type == "lente_fija" || camera?.modes?.isEmpty() == true)

    /** Una focal (o lente integrada) del juego, ya resuelta; `current` = la que se está usando. */
    data class Option(val label: String, val target: Framing.Target, val current: Boolean)

    private fun fmtF(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    val currentFocal: Double? get() = focal.toDoubleOrNull()?.takeIf { it > 0 }

    /** Nombre y modelo de la cámara que se simula (o la medida del sensor si es personalizada). */
    fun cameraName(): String =
        if (isCustom) "${customW}×${customH} mm" else camera!!.displayName.substringBefore(" (")

    /** Modo de grabación (o aspecto, en lente fija sin modos; o el squeeze, en la personalizada). */
    fun modeName(): String = when {
        isCustom -> if (squeeze != 1.0) "${squeeze}x" else ""
        isFixedLens && camera!!.modes.isEmpty() -> fixedAspects[fixedAspectIdx].second
        else -> modes.getOrNull(modeIdx)?.name.orEmpty().substringBefore(" (").loc()
    } + colorProfile?.let { " · ${it.name}" }.orEmpty()

    /** Etiqueta corta de lo que se está simulando. */
    fun label(): String = cameraName() + modeName().let { if (it.isEmpty()) "" else " · $it" }

    /** Segunda línea de la barra: el juego de lentes o la lente integrada. */
    fun subLabel(): String = when {
        isFixedLens -> camera!!.builtInLenses.getOrNull(builtInIdx)?.name.orEmpty().substringBefore(" (").loc()
        else -> lensSet?.name?.substringBefore(" (").orEmpty().loc() + if (squeeze != 1.0) " · ${squeeze}x" else ""
    }

    /** Focal actual para mostrar entre las flechas. */
    fun currentLabel(): String = when {
        isFixedLens -> camera!!.builtInLenses.getOrNull(builtInIdx)?.let { fmtF(it.equivalentFocalMm) + " mm eq" } ?: "—"
        else -> currentFocal?.let { fmtF(it) + " mm" } ?: "—"
    }

    /** Focal para el número grande de la barra: «35mm» o «20mm eq». */
    fun bigLabel(): String = currentLabel().replace(" mm", "mm")

    private fun fixedTarget(i: Int): Framing.Target? {
        val eq = camera?.builtInLenses?.getOrNull(i)?.equivalentFocalMm ?: return null
        val mode = modes.getOrNull(modeIdx)
        // Lente fija: focal equivalente full frame → FOV horizontal de un sensor de 36 mm de ancho.
        return Framing.Target(Fov.fovRad(Fov.FULL_FRAME_WIDTH_MM, eq), mode?.aspect ?: fixedAspects[fixedAspectIdx].first)
    }

    /** Objetivo para una focal dada (cámara intercambiable o personalizada), o null si faltan datos. */
    fun targetFor(f: Double): Framing.Target? = when {
        f <= 0 -> null
        isCustom -> {
            val w = customW.toDoubleOrNull(); val h = customH.toDoubleOrNull()
            if (w == null || h == null || w <= 0 || h <= 0) null else Framing.target(w, h, f, squeeze)
        }
        isFixedLens -> null
        else -> modes.getOrNull(modeIdx)?.takeIf { it.hasArea }?.let { Framing.target(it.widthMm!!, it.heightMm!!, f, squeeze) }
    }

    /** Objetivo actual, o null si faltan datos. */
    fun target(): Framing.Target? = if (isFixedLens) fixedTarget(builtInIdx) else currentFocal?.let(::targetFor)

    /** Todas las focales que se dibujan: el kit más la actual (o las lentes integradas de una cámara de lente fija). */
    fun options(): List<Option> {
        if (isFixedLens) return camera!!.builtInLenses.indices.mapNotNull { i ->
            fixedTarget(i)?.let { Option(fmtF(camera!!.builtInLenses[i].equivalentFocalMm), it, i == builtInIdx) }
        }
        val cur = currentFocal
        val focals = (kit + listOfNotNull(cur)).distinct().sorted()
        return focals.mapNotNull { f -> targetFor(f)?.let { Option(fmtF(f), it, cur != null && kotlin.math.abs(f - cur) < 1e-6) } }
    }

    /** Pasa a la focal siguiente (+1) o anterior (−1) del kit. */
    fun step(dir: Int) {
        if (isFixedLens) {
            builtInIdx = (builtInIdx + dir).coerceIn(0, camera!!.builtInLenses.lastIndex); return
        }
        val list = kit.sorted(); if (list.isEmpty()) return
        val cur = currentFocal ?: list.first()
        val next = if (dir > 0) list.firstOrNull { it > cur + 1e-6 } ?: list.last() else list.lastOrNull { it < cur - 1e-6 } ?: list.first()
        focal = fmtF(next)
    }

    fun toggleKit(f: Double) {
        kit = if (kit.any { kotlin.math.abs(it - f) < 1e-6 }) kit.filterNot { kotlin.math.abs(it - f) < 1e-6 } else kit + f
    }
}
