package app.mado.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Mediciones de la prueba de FOV, en `files/fov_tests.json` (recuperable por adb run-as o con "compartir"). */
class FovResults(context: Context) {
    private val file = context.filesDir.resolve("fov_tests.json")

    fun all(): JSONArray = runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())

    fun append(entry: JSONObject): Int {
        val arr = all().put(entry)
        file.writeText(arr.toString(2))
        return arr.length()
    }

    fun json(): String = all().toString(2)
}
