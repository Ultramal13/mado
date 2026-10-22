package app.mado.data

import android.content.ContentValues
import android.content.Context
import android.media.ExifInterface
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda una foto en Pictures/Mado (MediaStore, sin permisos de almacenamiento) con orientación y metadatos EXIF,
 * y deja un registro en `files/captures.json`. Los datos del encuadre van en el EXIF UserComment como JSON.
 */
object PhotoSaver {

    data class Saved(val name: String, val uri: String)

    fun save(
        context: Context, jpeg: ByteArray, rotationDeg: Int, meta: JSONObject, now: Date = Date(), suffix: String = "",
        location: android.location.Location? = null, headingDeg: Double? = null,
    ): Saved {
        val name = "MADO_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(now) + suffix + ".jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mado")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no se pudo crear el archivo")
        try {
            resolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: error("no se pudo escribir")
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation(rotationDeg).toString())
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).format(now))
                exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Mado")
                // UserComment del EXIF es ASCII: los caracteres no ASCII van escapados (\uXXXX) y el JSON sigue siendo válido.
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, asciiJson(meta))
                location?.let {
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, gpsRational(it.latitude))
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (it.latitude >= 0) "N" else "S")
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, gpsRational(it.longitude))
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (it.longitude >= 0) "E" else "W")
                    if (it.hasAltitude()) {
                        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "${(kotlin.math.abs(it.altitude) * 100).toLong()}/100")
                        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (it.altitude >= 0) "0" else "1")
                    }
                }
                headingDeg?.let {
                    // Dirección hacia donde apunta la cámara, respecto del norte magnético.
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "${(it * 100).toLong()}/100")
                    exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
                }
                exif.saveAttributes()
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        appendLog(context, name, uri.toString(), meta)
        return Saved(name, uri.toString())
    }

    internal fun asciiJson(meta: JSONObject): String =
        meta.toString().map { if (it.code > 127) "\\u%04x".format(it.code) else it.toString() }.joinToString("")

    /** Grados decimales → «g/1,m/1,s/1000» (formato racional de EXIF). */
    internal fun gpsRational(deg: Double): String {
        val a = kotlin.math.abs(deg)
        val d = a.toInt(); val mFull = (a - d) * 60.0; val m = mFull.toInt(); val sMilli = Math.round((mFull - m) * 60.0 * 1000.0)
        return "$d/1,$m/1,$sMilli/1000"
    }

    fun exifOrientation(rotationDeg: Int): Int = when (((rotationDeg % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun appendLog(context: Context, name: String, uri: String, meta: JSONObject) {
        val f = context.filesDir.resolve("captures.json")
        val arr = runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
        arr.put(JSONObject().put("file", name).put("uri", uri).put("meta", meta))
        f.writeText(arr.toString(2))
    }
}
