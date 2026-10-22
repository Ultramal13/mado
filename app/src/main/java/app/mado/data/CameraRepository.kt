package app.mado.data

import android.content.Context

/**
 * Carga la base de cámaras. Orden: copia remota guardada (`files/camaras_remote.json`, si es válida) y, si no,
 * el JSON de `assets/camaras.json`. La descarga remota se agrega después; el esquema ya lo permite.
 */
object CameraRepository {
    fun load(context: Context): CameraDb {
        val remote = runCatching { CameraDbParser.parse(context.filesDir.resolve("camaras_remote.json").readText()) }.getOrNull()
        if (remote != null && remote.cameras.isNotEmpty()) return remote
        return CameraDbParser.parse(context.assets.open("camaras.json").bufferedReader().use { it.readText() })
    }
}
