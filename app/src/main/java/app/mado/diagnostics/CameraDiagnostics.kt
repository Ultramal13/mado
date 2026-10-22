package app.mado.diagnostics

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import app.mado.optics.Fov
import org.json.JSONArray
import org.json.JSONObject

data class CameraInfo(
    val id: String,
    /** Id de la cámara lógica que lo expone, o null si es una cámara de nivel superior. */
    val parentId: String?,
    val facing: String,
    val isLogical: Boolean,
    val physicalIds: List<String>,
    val focalLengthsMm: List<Float>,
    val physicalSizeMm: Pair<Float, Float>?,
    val pixelArray: Pair<Int, Int>?,
    val activeArray: Pair<Int, Int>?,
    /** Ancho/alto del área activa en mm (físico * activo / pixel array). */
    val activeSizeMm: Pair<Double, Double>?,
    val hFovDeg: List<Double>,
    val vFovDeg: List<Double>,
    val equiv35Mm: List<Double>,
    val minFocusDiopters: Float?,
    val zoomRatioRange: Pair<Float, Float>?,
    val maxDigitalZoom: Float?,
    val hardwareLevel: String,
    val error: String? = null,
)

data class DiagnosticsReport(
    val device: Map<String, String>,
    val cameras: List<CameraInfo>,
) {
    fun toJson(): String = JSONObject().apply {
        put("schema_version", 1)
        put("etapa", 0)
        put("device", JSONObject(device))
        put("cameras", JSONArray().also { arr -> cameras.forEach { arr.put(it.toJson()) } })
    }.toString(2)
}

private fun CameraInfo.toJson() = JSONObject().apply {
    put("id", id)
    put("parent_id", parentId ?: JSONObject.NULL)
    put("facing", facing)
    put("is_logical", isLogical)
    put("physical_ids", JSONArray(physicalIds))
    put("focal_lengths_mm", JSONArray(focalLengthsMm.map { it.toDouble() }))
    put("physical_size_mm", physicalSizeMm?.let { JSONArray(listOf(it.first.toDouble(), it.second.toDouble())) } ?: JSONObject.NULL)
    put("pixel_array", pixelArray?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL)
    put("active_array", activeArray?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL)
    put("active_size_mm", activeSizeMm?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL)
    put("h_fov_deg", JSONArray(hFovDeg))
    put("v_fov_deg", JSONArray(vFovDeg))
    put("equiv_35mm", JSONArray(equiv35Mm))
    put("min_focus_diopters", minFocusDiopters?.toDouble() ?: JSONObject.NULL)
    put("zoom_ratio_range", zoomRatioRange?.let { JSONArray(listOf(it.first.toDouble(), it.second.toDouble())) } ?: JSONObject.NULL)
    put("max_digital_zoom", maxDigitalZoom?.toDouble() ?: JSONObject.NULL)
    put("hardware_level", hardwareLevel)
    put("error", error ?: JSONObject.NULL)
}

object CameraDiagnostics {

    fun collect(context: Context): DiagnosticsReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = mutableListOf<CameraInfo>()
        val seen = mutableSetOf<String>()

        for (id in manager.cameraIdList) {
            val info = read(manager, id, parentId = null)
            cameras += info
            seen += id
            // Las cámaras físicas detrás de una lógica no siempre aparecen en cameraIdList.
            for (pid in info.physicalIds) {
                if (seen.add(pid)) cameras += read(manager, pid, parentId = id)
            }
        }

        val device = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString(),
        )
        return DiagnosticsReport(device, cameras)
    }

    private fun read(manager: CameraManager, id: String, parentId: String?): CameraInfo {
        val c = try {
            manager.getCameraCharacteristics(id)
        } catch (e: Exception) {
            return CameraInfo(
                id, parentId, "?", false, emptyList(), emptyList(), null, null, null, null,
                emptyList(), emptyList(), emptyList(), null, null, null, "?",
                error = e.message ?: e.javaClass.simpleName,
            )
        }

        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val isLogical = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps
        val physicalIds = if (isLogical) c.physicalCameraIds.toList().sorted() else emptyList()

        val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val phys = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixel = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        val activeMm = if (phys != null && pixel != null && active != null) {
            Pair(
                phys.width.toDouble() * active.width() / pixel.width,
                phys.height.toDouble() * active.height() / pixel.height,
            )
        } else null

        val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { Pair(it.lower, it.upper) }
        } else null

        return CameraInfo(
            id = id,
            parentId = parentId,
            facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "?"
            },
            isLogical = isLogical,
            physicalIds = physicalIds,
            focalLengthsMm = focals,
            physicalSizeMm = phys?.let { Pair(it.width, it.height) },
            pixelArray = pixel?.let { Pair(it.width, it.height) },
            activeArray = active?.let { Pair(it.width(), it.height()) },
            activeSizeMm = activeMm,
            hFovDeg = activeMm?.let { m -> focals.map { Fov.fovDeg(m.first, it.toDouble()) } }.orEmpty(),
            vFovDeg = activeMm?.let { m -> focals.map { Fov.fovDeg(m.second, it.toDouble()) } }.orEmpty(),
            equiv35Mm = activeMm?.let { m -> focals.map { Fov.equivalentFocal35(m.first, it.toDouble()) } }.orEmpty(),
            minFocusDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
            zoomRatioRange = zoom,
            maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
            hardwareLevel = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "?"
            },
        )
    }
}
