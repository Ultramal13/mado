package app.mado.ui

import app.mado.data.CameraDbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingStatePersistTest {
    private val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())

    @Test
    fun selectionSurvivesRestart() {
        val a = FramingState(db)
        a.cameraIdx = a.cameras.indexOfFirst { it.id == "sony_fx3" }
        a.modeIdx = 1
        a.lensSetIdx = db.lensSets.lastIndex
        a.focal = "35"
        a.kit = listOf(20.0, 35.0, 85.0)
        val b = FramingState(db)
        b.restore(a.snapshot())
        assertEquals("sony_fx3", b.camera?.id)
        assertEquals(1, b.modeIdx)
        assertEquals(db.lensSets.lastIndex, b.lensSetIdx)
        assertEquals("35", b.focal)
        assertEquals(listOf(20.0, 35.0, 85.0), b.kit)
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun customCameraIsRemembered() {
        val a = FramingState(db)
        a.cameraIdx = a.cameras.size
        a.customW = "28.5"; a.customH = "15.9"
        val b = FramingState(db); b.restore(a.snapshot())
        assertTrue(b.isCustom)
        assertEquals("28.5", b.customW)
    }

    @Test
    fun brokenOrUnknownSavedDataIsIgnored() {
        val s = FramingState(db)
        s.restore(null); s.restore(""); s.restore("{no es json")
        s.restore("{\"camera\":\"no_existe\",\"focal\":\"x\",\"kit\":[]}")
        assertEquals(0, s.cameraIdx)          // sigue en la primera principal
        assertEquals("50", s.focal)
    }

    @Test
    fun sceneShotAndDeliveryAspectAreRemembered() {
        val a = FramingState(db)
        a.scene = "暖炉"; a.shotNo = 3; a.deliveryIdx = 0
        val b = FramingState(db); b.restore(a.snapshot())
        assertEquals("暖炉", b.scene); assertEquals(3, b.shotNo); assertEquals(16.0 / 9.0, b.delivery!!, 1e-9)
    }
}
