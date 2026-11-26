package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Test

class LensChoiceTest {
    @Test
    fun calibratedLensWins() {
        // S26: ids 2, 5, 6, 7 → equivalentes ~13, 24, 67, 111 mm; la 5 está calibrada.
        assertEquals(1, defaultLensIndex(listOf(13.0, 24.0, 67.0, 111.0), listOf(false, true, false, false)))
    }

    @Test
    fun withoutProfileTheMainLensIsChosenNotTheUltraWide() {
        // Xperia 1 III: ultra gran angular 17, principal 25, tele 75.
        assertEquals(1, defaultLensIndex(listOf(17.0, 25.0, 75.0), listOf(false, false, false)))
        assertEquals(0, defaultLensIndex(emptyList(), emptyList()))
    }

    @Test
    fun lensKindsFollowTheEquivalentFocalLength() {
        assertEquals(PhoneLensKind.ULTRA_WIDE, phoneLensKind(12.0))
        assertEquals(PhoneLensKind.ULTRA_WIDE, phoneLensKind(17.9))
        assertEquals(PhoneLensKind.MAIN, phoneLensKind(24.0))
        assertEquals(PhoneLensKind.MAIN, phoneLensKind(25.0))
        assertEquals(PhoneLensKind.TELE, phoneLensKind(67.0))
        assertEquals(PhoneLensKind.TELE, phoneLensKind(105.0))
    }

    @Test
    fun lensesAreShownFromWidestToLongest() {
        // Pixel de Edo: ids 2, 3, 4 con 24, 12 y 105 mm → se muestran 12, 24, 105 (posiciones 1, 0, 2).
        assertEquals(listOf(1, 0, 2), phoneLensDisplayOrder(listOf(24.0, 12.0, 105.0)))
        // S26: ids 2, 5, 6, 7 con 13, 24, 67, 111 ya vienen en orden.
        assertEquals(listOf(0, 1, 2, 3), phoneLensDisplayOrder(listOf(13.0, 24.0, 67.0, 111.0)))
        // Sin equivalente conocido va al final.
        assertEquals(listOf(1, 2, 0), phoneLensDisplayOrder(listOf(0.0, 24.0, 105.0)))
        assertEquals(emptyList<Int>(), phoneLensDisplayOrder(emptyList()))
    }
}
