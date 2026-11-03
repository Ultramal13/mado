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

    @Test
    fun colorProfileIsRememberedAndAllModesAreAlwaysListed() {
        val a = FramingState(db)
        a.cameraIdx = a.cameras.indexOfFirst { it.id == "sony_fx3" }
        a.selectColor("s_log3")
        // Sin selector de firmware: el catálogo siempre ofrece los modos del firmware más nuevo (aquí incluye el DCI 4K).
        assertTrue(a.modes.any { it.id == "video_dci_4k" })
        assertTrue(a.modeName().endsWith("S-Log3"))
        val b = FramingState(db); b.restore(a.snapshot())
        assertEquals("s_log3", b.colorProfile?.id)
        assertEquals("7.02", b.camera?.latestFirmware)
    }

    @Test
    fun lunaVerticalFormatSetsPortraitAspect() {
        val a = FramingState(db)
        a.cameraIdx = a.cameras.indexOfFirst { it.id == "insta360_luna_ultra" }
        a.modeIdx = a.modes.indexOfFirst { it.id == "video_4k_9_16" }
        val t = a.target()!!
        assertEquals(9.0 / 16.0, t.aspect, 1e-9)
    }

    @Test
    fun operatorSettingsAreRememberedAndShutterIsAutomaticUntilEdited() {
        val a = FramingState(db)
        a.opFps = "29.97"; a.opIso = "800"; a.opWb = "5600"; a.opNote = "PP7 S-Log3"
        assertEquals("1/60", a.shutterText())
        assertEquals("29.97p · 1/60 · ISO 800 · 5600K", a.operatorLine())
        a.opShutter = "1/100"
        val b = FramingState(db); b.restore(a.snapshot())
        assertEquals("1/100", b.shutterText()); assertEquals("PP7 S-Log3", b.opNote)
        assertEquals("29.97p · 1/100 · ISO 800 · 5600K", b.operatorLine())
        a.opShutter = null
        val c = FramingState(db); c.restore(a.snapshot())
        assertEquals(null, c.opShutter); assertEquals("1/60", c.shutterText())
        assertEquals("", FramingState(db).operatorLine())
    }

    @Test
    fun pictureProfileAndExternalRecordingAreRememberedAndFormNotes() {
        val a = FramingState(db)
        a.cameraIdx = a.cameras.indexOfFirst { it.id == "sony_fx3" }
        a.opPP = "PP10"; a.opNote = "LUT x"; a.opExternal = "Ninja · ProRes RAW"
        assertEquals("PP10 · LUT x · EXT Ninja · ProRes RAW", a.operatorNotes())
        val b = FramingState(db); b.restore(a.snapshot())
        assertEquals("PP10", b.opPP); assertEquals("Ninja · ProRes RAW", b.opExternal)
        // Un PP de otra cámara no se muestra si esta no lo tiene (la R5 no usa Picture Profiles).
        b.cameraIdx = b.cameras.indexOfFirst { it.id == "canon_eos_r5" }
        assertEquals("LUT x · EXT Ninja · ProRes RAW", b.operatorNotes())
    }
}
