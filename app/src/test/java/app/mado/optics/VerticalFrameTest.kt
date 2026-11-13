package app.mado.optics

import app.mado.data.CameraDbParser
import app.mado.ui.FramingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalFrameTest {
    private val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())

    @Test fun rotatingASensorSwapsItsHorizontalAndVerticalField() {
        val t = Framing.target(36.0, 24.0, 50.0)                       // full frame 3:2 a 50 mm: 39.6° × 27.0°
        val r = Framing.rotated(t)
        assertEquals(27.0, Math.toDegrees(r.hFovRad), 0.05)
        assertEquals(2.0 / 3.0, r.aspect, 1e-9)
        val back = Framing.rotated(r)                                   // girar dos veces devuelve el original
        assertEquals(Math.toDegrees(t.hFovRad), Math.toDegrees(back.hFovRad), 1e-9)
        assertEquals(t.aspect, back.aspect, 1e-9)
    }

    @Test fun verticalFrameIsNarrowerAndTallerOnTheSameImage() {
        val t = Framing.target(36.0, 24.0, 50.0)
        val phone = Math.toRadians(70.0)
        val h = Framing.frame(t, phone, 4.0 / 3.0)
        val v = Framing.frame(Framing.rotated(t), phone, 4.0 / 3.0)
        assertTrue(v.widthFrac < h.widthFrac)
        assertTrue(v.heightFrac > h.heightFrac)
        // El recuadro conserva el aspecto de la cámara dividido por el del preview: 3:2 horizontal y 2:3 vertical sobre una imagen 4:3.
        assertEquals(1.5 / (4.0 / 3.0), h.widthFrac / h.heightFrac, 1e-9)
        assertEquals((2.0 / 3.0) / (4.0 / 3.0), v.widthFrac / v.heightFrac, 1e-9)
    }

    @Test fun stateTurnsTheTargetAndRemembersTheOrientation() {
        val s = FramingState(db)
        s.cameraIdx = s.cameras.size                                    // cámara personalizada
        s.customW = "36"; s.customH = "24"; s.focal = "50"
        assertFalse(s.vertical)
        assertEquals(39.6, Math.toDegrees(s.target()!!.hFovRad), 0.05)
        s.vertical = true
        assertEquals(27.0, Math.toDegrees(s.target()!!.hFovRad), 0.05)
        assertEquals(2.0 / 3.0, s.target()!!.aspect, 1e-9)
        assertTrue(s.options().first { it.current }.target.aspect < 1.0)
        val b = FramingState(db); b.restore(s.snapshot())
        assertTrue(b.vertical)
    }
}
