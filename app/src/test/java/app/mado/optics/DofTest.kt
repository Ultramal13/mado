package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DofTest {
    private val fullFrameCoc = Dof.cocMm(36.0, 24.0)

    @Test fun cocOfAFullFrameSensorIsAboutTwentyNineMicrons() {
        assertEquals(0.0289, fullFrameCoc, 0.0002)
        assertTrue(Dof.cocMm(23.5, 15.6) < fullFrameCoc)          // un sensor más chico exige un círculo más chico
    }

    @Test fun hyperfocalOf50mmAtF8OnFullFrame() {
        val r = Dof.compute(50.0, 8.0, 3.0, 0.029)!!
        assertEquals(10.83, r.hyperfocalM, 0.01)                    // 50²/(8·0.029) + 50 ≈ 10 826 mm
        assertEquals(2.355, r.nearM, 0.01)                          // a 3 m
        assertEquals(4.131, r.farM, 0.01)
        assertEquals(5.41, r.nearAtHyperfocalM, 0.01)
    }

    @Test fun focusingAtOrBeyondTheHyperfocalGivesInfinity() {
        val r = Dof.compute(50.0, 8.0, 11.0, 0.029)!!
        assertTrue(r.farIsInfinity)
        assertTrue(r.nearM in 5.0..6.0)
        assertTrue(Dof.compute(24.0, 11.0, 5.0, 0.029)!!.farIsInfinity)     // gran angular cerrado: todo nítido desde ~1.6 m
    }

    @Test fun morePossibleApertureMeansMoreDepthAndLongerFocalMeansLess() {
        val wide = Dof.compute(50.0, 2.0, 3.0, 0.029)!!; val narrow = Dof.compute(50.0, 11.0, 3.0, 0.029)!!
        assertTrue((narrow.farM - narrow.nearM) > (wide.farM - wide.nearM))
        val tele = Dof.compute(200.0, 5.6, 3.0, 0.029)!!; val normal = Dof.compute(50.0, 5.6, 3.0, 0.029)!!
        assertTrue((tele.farM - tele.nearM) < (normal.farM - normal.nearM))
    }

    @Test fun limitsAreOrderedAroundTheFocusDistance() {
        val r = Dof.compute(85.0, 1.8, 2.0, 0.029)!!
        assertTrue(r.nearM < 2.0 && 2.0 < r.farM)
    }

    @Test fun invalidInputsGiveNull() {
        assertNull(Dof.compute(0.0, 2.8, 3.0, 0.029)); assertNull(Dof.compute(50.0, 0.0, 3.0, 0.029))
        assertNull(Dof.compute(50.0, 2.8, 0.0, 0.029)); assertNull(Dof.compute(50.0, 2.8, 3.0, 0.0))
    }

    @Test fun distancesAreFormattedForReading() {
        assertEquals("0.38 m", Dof.format(0.376)); assertEquals("2.4 m", Dof.format(2.355)); assertEquals("12 m", Dof.format(12.2)); assertEquals("∞", Dof.format(Double.POSITIVE_INFINITY))
    }
}
