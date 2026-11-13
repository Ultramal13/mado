package app.mado.ui

import app.mado.data.CameraDbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingDofTest {
    private val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())

    private fun fullFrame50(): FramingState = FramingState(db).apply {
        cameraIdx = cameras.size; customW = "36"; customH = "24"; focal = "50"; dofOn = true; aperture = "8"
    }

    @Test fun usesThePhoneFocusUnlessADistanceWasTyped() {
        val s = fullFrame50()
        assertEquals(2.0, s.dofDistance(2.0)!!, 1e-9)
        s.dofDistM = "3"
        assertEquals(3.0, s.dofDistance(2.0)!!, 1e-9)
        assertNull(fullFrame50().dofDistance(null))
    }

    @Test fun computesFromTheActiveSensorAndTheCurrentFocal() {
        val s = fullFrame50(); s.dofDistM = "3"
        val r = s.dof(s.dofDistance(null))!!
        assertEquals(2.36, r.nearM, 0.02); assertEquals(4.13, r.farM, 0.02)
        assertEquals("f/8 · 2.4–4.1 m", s.dofSummary(null))
        s.dofOn = false
        assertNull(s.dof(3.0)); assertNull(s.dofSummary(3.0))
    }

    @Test fun aSmallerSensorChangesTheCircleOfConfusion() {
        val ff = fullFrame50(); ff.dofDistM = "3"
        val apsc = fullFrame50().apply { customW = "23.5"; customH = "15.6"; dofDistM = "3" }
        assertTrue(apsc.dof(3.0)!!.hyperfocalM > ff.dof(3.0)!!.hyperfocalM)       // mismo lente, sensor más chico: círculo menor, hiperfocal mayor
    }

    @Test fun realCameraModesGiveASensorAndFixedLensesDoNot() {
        val s = FramingState(db)
        s.cameraIdx = s.cameras.indexOfFirst { it.id == "canon_eos_r5" }.takeIf { it >= 0 } ?: s.cameras.indexOfFirst { it.modes.any { m -> m.hasArea } }
        s.modeIdx = s.modes.indexOfFirst { it.hasArea }
        assertNotNull(s.sensorMm())
        val fixed = s.cameras.indexOfFirst { it.builtInLenses.isNotEmpty() && it.modes.none { m -> m.hasArea } }
        if (fixed >= 0) { s.cameraIdx = fixed; assertNull(s.sensorMm()) }
    }

    @Test fun settingsAreRemembered() {
        val a = fullFrame50(); a.dofDistM = "2.5"; a.aperture = "2.8"
        val b = FramingState(db); b.restore(a.snapshot())
        assertTrue(b.dofOn); assertEquals("2.8", b.aperture); assertEquals("2.5", b.dofDistM)
        val c = FramingState(db); c.restore("{\"aperture\":\"x\",\"dofDist\":\"-3\"}")
        assertEquals("5.6", c.aperture); assertEquals("", c.dofDistM)
    }

    @Test fun focusMarkOptionIsRememberedAndOnByDefault() {
        val a = FramingState(db)
        assertTrue(a.focusMarkOn)
        a.focusMarkOn = false
        val b = FramingState(db); b.restore(a.snapshot())
        assertTrue(!b.focusMarkOn)
    }
}
