package app.mado.camera

import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Protección térmica (enfoque de LUX-0): la cámara se apaga en estado MODERATE o peor y no vuelve
 * a abrirse hasta que el teléfono baje. En LIGHT solo se avisa. `status` es estado de Compose.
 */
class ThermalGuard(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val listener = PowerManager.OnThermalStatusChangedListener { status = it }
    private val executor = context.mainExecutor

    var status by mutableIntStateOf(power.currentThermalStatus)
        private set

    val hot get() = status >= PowerManager.THERMAL_STATUS_MODERATE
    val warm get() = status >= PowerManager.THERMAL_STATUS_LIGHT

    fun start() {
        power.addThermalStatusListener(executor, listener)
        status = power.currentThermalStatus
    }

    fun stop() = power.removeThermalStatusListener(listener)

    companion object {
        fun name(status: Int) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "?"
        }
    }
}
