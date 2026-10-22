package app.mado.data

import java.util.Locale

/**
 * Los nombres de modos, lentes y juegos de lentes vienen del catálogo (en español). Esta tabla los muestra en inglés o japonés
 * cuando la app está en ese idioma; lo que no está en la tabla (ARRIRAW, UHD, 4K…) se deja como está.
 */
object NameLocalizer {
    private val en = listOf(
        "Primes esféricos" to "Spherical primes", "Anamórfico" to "Anamorphic", "con Movie cropping = Enable" to "with Movie cropping = Enable",
        "Lente teleobjetivo" to "Telephoto lens", "Lente principal" to "Main lens", "Foto" to "Photo",
    )
    private val ja = listOf(
        "Primes esféricos" to "球面プライム", "Anamórfico" to "アナモルフィック", "con Movie cropping = Enable" to "Movie cropping 有効",
        "Lente teleobjetivo" to "望遠レンズ", "Lente principal" to "メインレンズ", "Foto" to "写真", "Video" to "動画",
    )
    private val exactEn = mapOf("principal" to "main", "tele" to "tele")
    private val exactJa = mapOf("principal" to "メイン", "tele" to "望遠")

    fun tr(name: String, lang: String = Locale.getDefault().language): String {
        val (table, exact) = when (lang) {
            "en" -> en to exactEn
            "ja" -> ja to exactJa
            else -> return name
        }
        exact[name.trim().lowercase()]?.let { return it }
        var out = name
        table.forEach { (from, to) -> out = out.replace(from, to) }
        return out
    }
}

fun String.loc(): String = NameLocalizer.tr(this)
