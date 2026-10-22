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
