package app.mado.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mado.optics.CalibrationMemory
import app.mado.optics.CompassHealth
import app.mado.optics.HeadingJumpDetector
import app.mado.optics.MagBiasEstimator
import app.mado.optics.MagCalibration
import app.mado.optics.OrientationMath

/** Estado de la calibración propia del magnetómetro (girar el teléfono en todas direcciones). */
enum class MagCalState { IDLE, COLLECTING, DONE, FAILED, NEED_LOCATION, UNSUPPORTED }

/**
 * Rumbo, elevación y nivel de la cámara con el sensor de rotación. Solo escucha mientras el visor está en marcha.
 *
 * Si el usuario calibró la brújula girando el teléfono, el sesgo magnético que se midió (imanes y hierro del propio teléfono) se resta del campo
 * y el rumbo del sistema se corrige con el norte que marca ese campo; la inclinación y el nivel siguen siendo los del sistema.
 */
class OrientationTracker(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val prefs = context.getSharedPreferences("mado", Context.MODE_PRIVATE)
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    // Para saber si el campo magnético está alterado (cables, imanes, metal): el giroscopio, el campo y la gravedad.
    private val gameSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val magSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rawMagSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    private val accSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var pose by mutableStateOf<OrientationMath.Pose?>(null)
        private set
    val available get() = sensor != null

    /** Calidad de la calibración del sensor magnético que informa Android (0 inservible … 3 alta) y, si el teléfono la da, el error estimado del rumbo en grados. */
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        private set
    var headingErrorDeg by mutableStateOf<Double?>(null)
        private set

    /** Hay algo que altera la brújula (el campo medido no es el de la Tierra en este lugar, o el rumbo magnético saltó sin que el teléfono girara). */
    var disturbed by mutableStateOf(false)
        private set
    /** El campo se aparta de lo normal (acero, autos, hormigón cerca): el rumbo puede estar 10–15° corrido, aunque no haya un imán encima. */
    var imprecise by mutableStateOf(false)
        private set
    /** Fuerza del campo medido (µT), suavizada, para dejarla anotada en la foto (con el sesgo propio ya restado si hay calibración). */
    @Volatile var fieldUt: Double? = null
        private set
    @Volatile var disturbedNow = false
        private set
    @Volatile var impreciseNow = false
        private set
    /** Fuerza (µT) e inclinación (° hacia abajo) esperadas en este lugar; null = no se sabe dónde estás y solo vale la prueba del salto de rumbo. */
    @Volatile var expectedField: Pair<Double, Double>? = null

    // --- Calibración propia ---
    @Volatile private var bias: DoubleArray? = loadBias()
    var hasBias by mutableStateOf(bias != null)
        private set
    /** Si el rumbo de la última lectura usa el sesgo propio (para anotarlo en la foto). */
    val biasActive get() = bias != null
    var calState by mutableStateOf(MagCalState.IDLE)
        private set
    /** 0..1 mientras se juntan muestras. */
    var calProgress by mutableFloatStateOf(0f)
        private set
    /** Tamaño del sesgo hallado (µT), para el mensaje al terminar. */
    var calBiasUt by mutableStateOf<Double?>(null)
        private set
    private var est: MagBiasEstimator? = null
    private var calStartMs = 0L

    private val jump = HeadingJumpDetector()
    private val bAvg = DoubleArray(3); private val aAvg = DoubleArray(3); private val aLast = DoubleArray(3)
    private var haveB = false; private var haveA = false
    private var gameHeading: Double? = null
    private var gameElev = 0.0
    private var fieldBad = false
    private var fieldMeh = false
    private val lastR = FloatArray(9); private var haveR = false
    private var bwEast = 0.0; private var bwNorth = 0.0; private var haveBw = false

    /** Última lectura, sin limitar la frecuencia (para guardarla al disparar). */
    @Volatile var latest: OrientationMath.Pose? = null
        private set
    private var lastUi = 0L

    private fun loadBias(): DoubleArray? {
        val at = prefs.getLong("mag_bias_at", 0L)
        if (!CalibrationMemory.shouldRestore(at, System.currentTimeMillis(), null, null, null, null)) return null
        val x = prefs.getString("mag_bias_x", null)?.toDoubleOrNull() ?: return null
        val y = prefs.getString("mag_bias_y", null)?.toDoubleOrNull() ?: return null
        val z = prefs.getString("mag_bias_z", null)?.toDoubleOrNull() ?: return null
        return doubleArrayOf(x, y, z)
    }

    fun start() {
        jump.reset(); haveB = false; haveA = false; haveR = false; haveBw = false; gameHeading = null; fieldBad = false; fieldMeh = false
        disturbedNow = false; disturbed = false; impreciseNow = false; imprecise = false
        if (calState == MagCalState.COLLECTING) { est = null; calState = MagCalState.IDLE }
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gameSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        rawMagSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }
    fun stop() { sm.unregisterListener(this) }

    /** Empieza a juntar muestras: el usuario gira el teléfono en todas direcciones (plano, de pie, de lado, boca abajo). */
    fun startMagCal() {
        val exp = expectedField
        when {
            exp == null -> calState = MagCalState.NEED_LOCATION
            rawMagSensor == null || accSensor == null -> calState = MagCalState.UNSUPPORTED
            else -> {
                est = MagBiasEstimator(MagCalibration.expectedVerticalUt(exp.first, exp.second))
                calStartMs = android.os.SystemClock.elapsedRealtime(); calProgress = 0f; calBiasUt = null; calState = MagCalState.COLLECTING
            }
        }
    }

    fun cancelMagCal() { est = null; calState = MagCalState.IDLE }

    /** Vuelve a usar el campo calibrado por Android, sin sesgo propio. */
    fun clearMagBias() {
        bias = null; hasBias = false; haveB = false; haveBw = false; est = null; calState = MagCalState.IDLE
        prefs.edit().remove("mag_bias_at").remove("mag_bias_x").remove("mag_bias_y").remove("mag_bias_z").apply()
    }

    private fun finishCal(e: MagBiasEstimator) {
        val b = e.solve()
        est = null
        if (b != null && e.residualRms(b) < 4.0) {
            bias = b; hasBias = true; haveB = false; haveBw = false
            calBiasUt = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]); calState = MagCalState.DONE
            prefs.edit().putLong("mag_bias_at", System.currentTimeMillis()).putString("mag_bias_x", b[0].toString())
                .putString("mag_bias_y", b[1].toString()).putString("mag_bias_z", b[2].toString()).apply()
        } else calState = MagCalState.FAILED
    }

    private fun smooth(avg: DoubleArray, v: DoubleArray, first: Boolean) {
        for (i in 0..2) avg[i] = if (first) v[i] else avg[i] * 0.9 + v[i] * 0.1
    }

    /** Campo medido contra el esperado para el lugar (con la fuerza y la inclinación suavizadas, no con una lectura suelta). */
    private fun checkField() {
        val exp = expectedField
        if (!haveB) return
        fieldUt = CompassHealth.strengthUt(bAvg)
        val incl = if (haveA) CompassHealth.inclinationDeg(bAvg, aAvg) else null
        fieldBad = exp != null && CompassHealth.fieldDisturbed(CompassHealth.strengthUt(bAvg), incl, exp.first, exp.second)
        fieldMeh = exp != null && !fieldBad && CompassHealth.fieldImprecise(CompassHealth.strengthUt(bAvg), incl, exp.first, exp.second)
    }

    private fun d(v: FloatArray) = doubleArrayOf(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Sin sesgo propio se usa el campo que ya calibró Android.
                if (bias == null) { smooth(bAvg, d(e.values), !haveB); haveB = true; checkField() }
                return
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> { onRawField(e); return }
            Sensor.TYPE_ACCELEROMETER -> {
                val a = d(e.values)
                smooth(aAvg, a, !haveA); haveA = true
                for (i in 0..2) aLast[i] = a[i]
                return
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val g = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(g, e.values)
                val gp = OrientationMath.fromMatrix(g)
                gameHeading = gp.headingDeg; gameElev = gp.elevationDeg
                return
            }
        }
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, e.values)
        for (i in 0..8) lastR[i] = r[i]; haveR = true
        var p = OrientationMath.fromMatrix(r)
        // Con calibración propia: el norte lo marca el campo sin sesgo, no el que supone el sistema.
        if (bias != null && haveBw) p = OrientationMath.Pose(MagCalibration.correctedHeading(p.headingDeg, bwEast, bwNorth), p.elevationDeg, p.levelDeg)
        latest = p
        // Salto de rumbo: solo con la cámara lejos de la vertical, donde el rumbo está bien definido en ambos sensores.
        gameHeading?.let { gh -> if (Math.abs(p.elevationDeg) < 70.0 && Math.abs(gameElev) < 70.0) jump.update(e.timestamp / 1_000_000L, p.headingDeg, gh) }
        val bad = jump.disturbed || fieldBad
        disturbedNow = bad
        impreciseNow = !bad && fieldMeh
        // La interfaz se refresca 4 veces por segundo como mucho: no hace falta más y se evita recomponer sin necesidad.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUi >= 250) {
            lastUi = now; pose = p
            disturbed = bad; imprecise = impreciseNow
            accuracy = e.accuracy
            headingErrorDeg = if (e.values.size > 4 && e.values[4] >= 0f) Math.toDegrees(e.values[4].toDouble()) else null
        }
    }

    /** Campo sin calibrar (con el sesgo de fábrica y el del teléfono): sirve para la calibración propia y, si ya existe, para medir el campo sin sesgo. */
    private fun onRawField(e: SensorEvent) {
        val raw = d(e.values)
        est?.let { est ->
            if (haveA) est.add(raw, aLast)
            calProgress = est.coverage().toFloat()
            if (est.ready) finishCal(est)
            else if (android.os.SystemClock.elapsedRealtime() - calStartMs > 45_000) { this.est = null; calState = MagCalState.FAILED }
        }
        val b = bias ?: return
        val eff = doubleArrayOf(raw[0] - b[0], raw[1] - b[1], raw[2] - b[2])
        smooth(bAvg, eff, !haveB); haveB = true; checkField()
        if (haveR) {
            // El campo sin sesgo en el «mundo» del sistema (este, norte): no cambia al girar el teléfono, así que se puede suavizar bien.
            val x = lastR[0] * eff[0] + lastR[1] * eff[1] + lastR[2] * eff[2]
            val y = lastR[3] * eff[0] + lastR[4] * eff[1] + lastR[5] * eff[2]
            if (!haveBw) { bwEast = x; bwNorth = y; haveBw = true } else { bwEast += 0.04 * (x - bwEast); bwNorth += 0.04 * (y - bwNorth) }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
