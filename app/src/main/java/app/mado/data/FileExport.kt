package app.mado.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Guarda texto como archivo en Descargas/Mado (visible en la app Archivos, sin permisos de almacenamiento). */
object FileExport {
    data class Saved(val uri: Uri, val displayPath: String)

    fun saveJson(context: Context, name: String, json: String): Saved {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mado")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no se pudo crear el archivo")
        try {
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("no se pudo escribir")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, "Download/Mado/$name")
    }

    /** Nombre seguro para archivo: solo letras, números, punto, guion y guion bajo. */
    fun safeName(raw: String) = raw.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
}
