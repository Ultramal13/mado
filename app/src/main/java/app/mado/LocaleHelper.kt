package app.mado

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Idioma de la app elegido por el usuario: «auto» (el del teléfono), es, en o ja. Funciona igual en todas las versiones
 * de Android (envuelve el contexto de la actividad); cambiarlo recrea la pantalla.
 */
object LocaleHelper {
    const val AUTO = "auto"
    val OPTIONS = listOf(AUTO, "es", "en", "ja")

    fun saved(context: Context): String =
        context.getSharedPreferences("mado", Context.MODE_PRIVATE).getString("lang", AUTO)?.takeIf { it in OPTIONS } ?: AUTO

    fun save(context: Context, tag: String) {
        context.getSharedPreferences("mado", Context.MODE_PRIVATE).edit().putString("lang", tag).apply()
    }

    /** Código corto para el botón del encabezado. */
    fun shortLabel(tag: String) = if (tag == AUTO) "auto" else tag.uppercase(Locale.ROOT)

    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (tag == AUTO) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(locale))
        return base.createConfigurationContext(cfg)
    }
}
