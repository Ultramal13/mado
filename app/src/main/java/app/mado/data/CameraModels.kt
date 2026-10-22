package app.mado.data

/** Fuente de un dato numérico (regla del proyecto: ningún dato de cámara sin fuente). */
data class Source(val text: String, val url: String?)

/** Modo de grabación: el FOV depende del área activa del sensor en ese modo, no solo de la cámara. */
data class RecordingMode(
    val id: String,
    val name: String,
    val widthMm: Double,
    val heightMm: Double,
    val source: Source?,
    /** true si el valor lo calculamos (no lo publica el fabricante). */
    val derived: Boolean = false,
) {
    val aspect: Double get() = widthMm / heightMm
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
) {
    val displayName: String get() = "$brand $model"
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
