package app.mado.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Una sola lectura de ubicación al disparar (nada de seguimiento continuo). Solo si el usuario la activó y dio el permiso. */
object LocationHelper {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 5000): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || providers.isEmpty()) return last
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(providers.first(), cancel, context.mainExecutor) { cont.resume(it) }
            }
        }
        return fresh ?: last
    }

    /** «-33.43720  -70.65060» (grados decimales con signo). */
    fun format(l: Location) = String.format(java.util.Locale.ROOT, "%.5f  %.5f", l.latitude, l.longitude)
}
