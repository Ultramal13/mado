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
    /** Perfil de color elegido por cámara (id de cámara → id del perfil). Sin entrada = ninguno. */
    var colorByCamera by mutableStateOf(mapOf<String, String>())
    var lensSetIdx by mutableIntStateOf(0)
    var focal by mutableStateOf("50")
    var customW by mutableStateOf("36")
    var customH by mutableStateOf("24")
    var builtInIdx by mutableIntStateOf(0)
    /** Focales del kit que se dibujan a la vez (la actual va resaltada); se suman o quitan desde el panel. */
    var kit by mutableStateOf(listOf(24.0, 35.0, 50.0, 85.0))
    /** Encuadre vertical (la cámara girada 90°, como se dispara una foto de retrato); false = horizontal. */
    var vertical by mutableStateOf(false)
    /** Guías de composición activas (se recuerdan entre usos). */
    var guides by mutableStateOf(setOf<Guide>())
    /** Aspecto de entrega (línea sobre el encuadre, p. ej. 16:9 sobre un sensor 3:2); -1 = igual al sensor, sin línea. */
    val deliveryAspects = listOf(16.0 / 9.0 to "16:9", 2.39 to "2.39:1", 1.85 to "1.85:1", 4.0 / 3.0 to "4:3", 1.0 to "1:1", 9.0 / 16.0 to "9:16", 4.0 / 5.0 to "4:5")
    var deliveryIdx by mutableIntStateOf(-1)
    val delivery: Double? get() = deliveryAspects.getOrNull(deliveryIdx)?.first
    /** Datos para el operador de la cámara (lo que decide el cinematógrafo): cuadros por segundo, obturación, ISO, balance de blancos y una nota (PP, LUT, ND…). */
    var opFps by mutableStateOf("")
    /** null = obturación automática por la regla de los 180° a partir de los fps. */
    var opShutter by mutableStateOf<String?>(null)
    var opIso by mutableStateOf("")
    var opWb by mutableStateOf("")
    var opNote by mutableStateOf("")
    /** Picture Profile elegido (p. ej. «PP10»; solo si la cámara los tiene) y grabación externa por HDMI (grabador y formato, p. ej. «Ninja · ProRes RAW»). */
    var opPP by mutableStateOf("")
    var opExternal by mutableStateOf("")
    /** Línea de notas para el operador: PP, nota libre y grabación externa, con lo que haya. */
    fun operatorNotes(): String = listOfNotNull(
        opPP.trim().takeIf { it.isNotEmpty() && camera?.pictureProfiles?.contains(it) != false },
        opNote.trim().takeIf { it.isNotEmpty() },
        opExternal.trim().takeIf { it.isNotEmpty() }?.let { "EXT $it" },
    ).joinToString(" · ")
    fun shutterText(): String = opShutter ?: app.mado.optics.Shutter.suggest180(opFps.toDoubleOrNull())
    /** «29.97p · 1/60 · ISO 800 · 5600K» con lo que haya; vacío si no hay nada. */
    fun operatorLine(): String = listOfNotNull(
        opFps.trim().takeIf { it.isNotEmpty() }?.let { "${it}p" },
        shutterText().takeIf { it.isNotEmpty() },
        opIso.trim().takeIf { it.isNotEmpty() }?.let { "ISO $it" },
        opWb.trim().takeIf { it.isNotEmpty() }?.let { "${it}K" },
    ).joinToString(" · ")

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
        put("color", org.json.JSONObject(colorByCamera))
        put("lensSet", lensSet?.id)
        put("focal", focal)
        put("kit", org.json.JSONArray(kit))
        put("customW", customW); put("customH", customH)
        put("builtIn", camera?.builtInLenses?.getOrNull(builtInIdx)?.id)
        put("aspect", fixedAspectIdx)
        put("delivery", deliveryIdx)
        put("vertical", vertical)
        put("scene", scene); put("shotNo", shotNo)
        put("opFps", opFps); put("opShutter", opShutter ?: org.json.JSONObject.NULL); put("opIso", opIso); put("opWb", opWb); put("opNote", opNote)
        put("opPP", opPP); put("opExternal", opExternal)
    }.toString()

    /** Vuelve a la selección guardada por [snapshot]; lo que ya no exista en el catálogo se ignora y queda el valor por omisión. */
    fun restore(json: String?) {
        if (json.isNullOrBlank()) return
        try {
            val o = org.json.JSONObject(json)
            val cam = o.optString("camera")
            if (cam == "custom") cameraIdx = cameras.size
            else cameras.indexOfFirst { it.id == cam }.takeIf { it >= 0 }?.let { cameraIdx = it }
            o.optJSONObject("color")?.let { c -> colorByCamera = c.keys().asSequence().associateWith { c.optString(it) }.filterValues { it.isNotBlank() } }
            if (!isCustom) {
                modes.indexOfFirst { it.id == o.optString("mode") }.takeIf { it >= 0 }?.let { modeIdx = it }
                camera?.builtInLenses?.indexOfFirst { it.id == o.optString("builtIn") }?.takeIf { it >= 0 }?.let { builtInIdx = it }
            }
            lensSets.indexOfFirst { it.id == o.optString("lensSet") }.takeIf { it >= 0 }?.let { lensSetIdx = it }
            o.optString("focal").takeIf { it.toDoubleOrNull() != null }?.let { focal = it }
            o.optJSONArray("kit")?.let { a -> List(a.length()) { a.optDouble(it) }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.let { kit = it } }
            o.optString("customW").takeIf { it.toDoubleOrNull() != null }?.let { customW = it }
            o.optString("customH").takeIf { it.toDoubleOrNull() != null }?.let { customH = it }
            o.optInt("aspect", -1).takeIf { it in fixedAspects.indices }?.let { fixedAspectIdx = it }
            deliveryIdx = o.optInt("delivery", -1).takeIf { it in deliveryAspects.indices } ?: -1
            vertical = o.optBoolean("vertical", false)
            scene = o.optString("scene", "").take(40)
            shotNo = o.optInt("shotNo", 1).coerceIn(1, 999)
            opFps = o.optString("opFps", "").take(8); opIso = o.optString("opIso", "").take(8); opWb = o.optString("opWb", "").take(8)
            opShutter = if (o.isNull("opShutter") || !o.has("opShutter")) null else o.optString("opShutter").take(10)
            opNote = o.optString("opNote", "").take(60)
            opPP = o.optString("opPP", "").take(10); opExternal = o.optString("opExternal", "").take(60)
        } catch (_: Exception) { /* guardado dañado: se parte desde cero */ }
    }

    val isCustom get() = cameraIdx >= cameras.size
    val camera: Camera? get() = cameras.getOrNull(cameraIdx)
    /** Juegos de lentes: los del catálogo y, al final, los propios del usuario. */
    var customKits by mutableStateOf(listOf<app.mado.data.LensSet>())
    var kitStore: app.mado.data.KitStore? = null
    val lensSets: List<app.mado.data.LensSet> get() = db.lensSets + customKits
    val lensSet get() = lensSets.getOrNull(lensSetIdx)

    /** Elige un juego de lentes: el kit dibujado pasa a las focales del juego más cercanas a 24, 35, 50 y 85 mm, y la focal actual a la más cercana. */
    fun selectLensSet(idx: Int) {
        val set = lensSets.getOrNull(idx) ?: return
        lensSetIdx = idx
        if (set.focalsMm.isEmpty()) return
        fun nearest(f: Double) = set.focalsMm.minBy { kotlin.math.abs(it - f) }
        kit = listOf(24.0, 35.0, 50.0, 85.0).map(::nearest).distinct().sorted()
        currentFocal?.let { focal = fmtF(nearest(it)) }
    }

    /** Crea ([id] null) o cambia un juego propio, lo guarda en el teléfono y lo deja elegido. */
    fun saveKit(id: String?, name: String, squeeze: Double, focals: List<Double>) {
        val kitId = id ?: app.mado.data.KitStore.newId()
        val kit = app.mado.data.LensSet(kitId, name.trim().ifEmpty { "—" }, null, squeeze.coerceAtLeast(1.0), focals, custom = true)
        customKits = if (customKits.any { it.id == kitId }) customKits.map { if (it.id == kitId) kit else it } else customKits + kit
        kitStore?.save(customKits)
        selectLensSet(lensSets.indexOfFirst { it.id == kitId })
    }

    fun deleteKit(id: String) {
        val current = lensSet?.id
        customKits = customKits.filterNot { it.id == id }
        kitStore?.save(customKits)
        lensSetIdx = if (current == id) 0 else lensSets.indexOfFirst { it.id == current }.coerceAtLeast(0)
    }
    val squeeze get() = lensSet?.squeeze ?: 1.0
    /** Modos de la cámara actual. El catálogo siempre refleja el firmware más nuevo que conocemos (`Camera.latestFirmware`). */
    val modes: List<app.mado.data.RecordingMode> get() = camera?.modes.orEmpty()

    /** Perfil de color (log, estándar…) elegido para la cámara actual, o null. */
    val colorProfile: app.mado.data.ColorProfile? get() = camera?.let { cam -> colorProfiles.firstOrNull { it.id == colorByCamera[cam.id] } }

    /** Perfiles de color (log, estándar, HDR) de la cámara actual. */
    val colorProfiles: List<app.mado.data.ColorProfile> get() = camera?.colorProfiles.orEmpty()

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
        return orient(Framing.Target(Fov.fovRad(Fov.FULL_FRAME_WIDTH_MM, eq), mode?.aspect ?: fixedAspects[fixedAspectIdx].first))
    }

    /** Aplica la orientación elegida: en vertical el objetivo se gira 90°. */
    private fun orient(t: Framing.Target?): Framing.Target? = if (vertical && t != null) Framing.rotated(t) else t

    /** Objetivo para una focal dada (cámara intercambiable o personalizada), o null si faltan datos. */
    fun targetFor(f: Double): Framing.Target? = orient(baseTargetFor(f))

    private fun baseTargetFor(f: Double): Framing.Target? = when {
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
