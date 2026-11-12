package app.mado.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mado.optics.OrientationMath

/** Rumbo, elevación y nivel de la cámara con el sensor de rotación. Solo escucha mientras el visor está en marcha. */
class OrientationTracker(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var pose by mutableStateOf<OrientationMath.Pose?>(null)
        private set
    val available get() = sensor != null

    /** Calidad de la calibración del sensor magnético que informa Android (0 inservible … 3 alta) y, si el teléfono la da, el error estimado del rumbo en grados. */
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        private set
    var headingErrorDeg by mutableStateOf<Double?>(null)
        private set

    /** Última lectura, sin limitar la frecuencia (para guardarla al disparar). */
    @Volatile var latest: OrientationMath.Pose? = null
        private set
    private var lastUi = 0L

    fun start() { sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, e.values)
        val p = OrientationMath.fromMatrix(r)
        latest = p
        // La interfaz se refresca 4 veces por segundo como mucho: no hace falta más y se evita recomponer sin necesidad.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUi >= 250) {
            lastUi = now; pose = p
            accuracy = e.accuracy
            headingErrorDeg = if (e.values.size > 4 && e.values[4] >= 0f) Math.toDegrees(e.values[4].toDouble()) else null
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
