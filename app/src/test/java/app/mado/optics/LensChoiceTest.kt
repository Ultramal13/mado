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
}
