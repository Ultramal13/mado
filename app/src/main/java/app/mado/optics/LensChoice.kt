package app.mado.optics

import kotlin.math.abs

/** Equivalente en 35 mm de la lente principal de un teléfono (la «1x»): ~24 mm. */
private const val MAIN_LENS_EQUIV_MM = 24.0

/**
 * Lente con la que arranca la app: la primera que tiene un perfil calibrado; si ninguna lo tiene, la principal
 * (la de equivalente más cercano a 24 mm, no la ultra gran angular que suele ir primera en la lista).
 */
fun defaultLensIndex(equivMm: List<Double>, calibrated: List<Boolean>): Int {
    if (equivMm.isEmpty()) return 0
    val cal = calibrated.indexOfFirst { it }
    if (cal >= 0) return cal
    return equivMm.indices.minByOrNull { abs(equivMm[it] - MAIN_LENS_EQUIV_MM) } ?: 0
}

/** Tipo de lente de un teléfono según su equivalente en 35 mm (por diagonal): ultra gran angular, principal («1x») o tele. */
enum class PhoneLensKind { ULTRA_WIDE, MAIN, TELE }

fun phoneLensKind(equivMm: Double): PhoneLensKind = when {
    equivMm < 18.0 -> PhoneLensKind.ULTRA_WIDE
    equivMm < 35.0 -> PhoneLensKind.MAIN
    else -> PhoneLensKind.TELE
}

/** Orden en que se muestran los lentes: de más ancho a más largo (el orden de los ids de Camera2 no dice nada al usuario). Los que no traen equivalente van al final. */
fun phoneLensDisplayOrder(equivMm: List<Double>): List<Int> =
    equivMm.indices.sortedWith(compareBy({ equivMm[it] <= 0.0 }, { equivMm[it] }, { it }))
