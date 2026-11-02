package app.mado.data

/** Fuente de un dato numérico (regla del proyecto: ningún dato de cámara sin fuente). */
data class Source(val text: String, val url: String?)

/** Modo de grabación: el FOV depende del área activa del sensor en ese modo, no solo de la cámara. */
data class RecordingMode(
    val id: String,
    val name: String,
    /** Área activa del sensor en este modo; null en cámaras de lente fija que solo publican formato (resolución y aspecto). */
    val widthMm: Double?,
    val heightMm: Double?,
    val source: Source?,
    /** true si el valor lo calculamos (no lo publica el fabricante). */
    val derived: Boolean = false,
    /** Primera versión de firmware que ofrece este modo; null = desde el lanzamiento (o sin historial documentado). */
    val sinceFirmware: String? = null,
    val resolution: String? = null,
    /** Aspecto publicado (p. ej. 9/16); se usa cuando el modo no trae medidas de sensor. */
    val aspectRatio: Double? = null,
) {
    val hasArea: Boolean get() = widthMm != null && heightMm != null
    val aspect: Double get() = aspectRatio ?: (widthMm!! / heightMm!!)
}

/** Una versión de firmware publicada, con lo que cambió en cuanto a formatos y su fuente. */
data class FirmwareEntry(val version: String, val date: String?, val notes: String, val source: Source?)

/** Perfil de color o curva de grabación (log, estándar, HDR) que ofrece la cámara. */
data class ColorProfile(val id: String, val name: String, val kind: String, val source: Source?, val sinceFirmware: String? = null)

/** Compara versiones de firmware por segmentos numéricos: 7.02 > 7.00, 1.1.22 > 1.1.8. */
object FirmwareVersion {
    private fun parts(v: String) = v.trim().split('.', '-', ' ').mapNotNull { it.toIntOrNull() }
    fun compare(a: String, b: String): Int {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}

data class BuiltInLens(val id: String, val name: String, val equivalentFocalMm: Double, val source: Source?)

data class Camera(
    val id: String,
    val brand: String,
    val model: String,
    /** "lente_intercambiable" o "lente_fija". */
    val type: String,
    val mount: String?,
    /** Solo modos completos; los que tienen valores null se descartan al leer. */
    val modes: List<RecordingMode>,
    val builtInLenses: List<BuiltInLens>,
    val incompleteModes: Int,
    /** 1, 2, 3… para las cámaras principales del usuario (van primero); null = el resto. */
    val priority: Int? = null,
    /** Historial de firmware (los fabricantes agregan formatos después del lanzamiento); vacío = sin documentar. */
    val firmwareHistory: List<FirmwareEntry> = emptyList(),
    val colorProfiles: List<ColorProfile> = emptyList(),
) {
    val displayName: String get() = "$brand $model"

    /** Versión de firmware más nueva que conocemos, o null si no hay historial. */
    val latestFirmware: String? get() = firmwareHistory.map { it.version }.maxWithOrNull { a, b -> FirmwareVersion.compare(a, b) }

    /** Perfiles de color que existen en el firmware [firmware] (null = el más nuevo). */
    fun colorProfilesFor(firmware: String?): List<ColorProfile> =
        if (firmware == null) colorProfiles
        else colorProfiles.filter { it.sinceFirmware == null || FirmwareVersion.compare(it.sinceFirmware, firmware) <= 0 }

    /** Modos que existen en el firmware [firmware] (null = el más nuevo). */
    fun modesFor(firmware: String?): List<RecordingMode> =
        if (firmware == null) modes
        else modes.filter { it.sinceFirmware == null || FirmwareVersion.compare(it.sinceFirmware, firmware) <= 0 }
}

/** Juego de lentes: esféricos (squeeze 1.0) o anamórficos (squeeze > 1.0). */
data class LensSet(
    val id: String,
    val name: String,
    val mount: String?,
    val squeeze: Double,
    val focalsMm: List<Double>,
)

/** Calibración de una lente del teléfono; `measuredHFovDeg` null = sin verificar. */
data class PhoneLensProfile(
    val camera2Id: String,
    val measuredHFovDeg: Double?,
    val reportedHFovDeg: Double?,
)

data class PhoneProfile(val buildModel: String, val name: String, val lenses: List<PhoneLensProfile>)

data class CameraDb(
    val schemaVersion: Int,
    val cameras: List<Camera>,
    val lensSets: List<LensSet>,
    val phoneProfiles: List<PhoneProfile>,
)
