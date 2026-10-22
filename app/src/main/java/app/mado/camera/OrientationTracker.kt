package app.mado.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
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
        if (now - lastUi >= 250) { lastUi = now; pose = p }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
