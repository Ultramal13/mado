package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class FramingTest {
    private val phoneH = Math.toRadians(74.0)
    private val phoneAspect = 4.0 / 3.0

    @Test
    fun fullFrame50mmInsideAPhoneAt74Degrees() {
        val t = Framing.target(36.0, 24.0, 50.0)
        val f = Framing.frame(t, phoneH, phoneAspect)
        assertEquals(0.4777, f.widthFrac, 1e-3)
        assertEquals(0.4246, f.heightFrac, 1e-3)
        assertFalse(f.padding)
    }

    @Test
    fun widerThanThePhoneNeedsPadding() {
        val t = Framing.target(36.0, 24.0, 14.0)
        val f = Framing.frame(t, phoneH, phoneAspect)
        assertTrue(f.widthFrac > 1.0)
        assertTrue(f.padding)
    }

    @Test
    fun sameFovAndAspectFillsThePreview() {
        val f = Framing.frame(Framing.Target(phoneH, phoneAspect), phoneH, phoneAspect)
        assertEquals(1.0, f.widthFrac, 1e-9)
        assertEquals(1.0, f.heightFrac, 1e-9)
    }

    @Test
    fun anamorphicAspectIsSensorTimesSqueeze() {
        val t = Framing.target(24.0, 18.0, 50.0, squeeze = 2.0)
        assertEquals(24.0 * 2 / 18.0, t.aspect, 1e-9)
        // El campo horizontal es el de un esférico con el doble de ancho de sensor.
        assertEquals(Fov.fovRad(48.0, 50.0), t.hFovRad, 1e-9)
    }

    @Test
    fun heightFractionMatchesVerticalFovRatio() {
        val t = Framing.target(36.0, 24.0, 35.0)
        val f = Framing.frame(t, phoneH, phoneAspect)
        val vTarget = Math.toRadians(Fov.fovDeg(24.0, 35.0))
        val vPhone = 2.0 * Math.atan(tan(phoneH / 2.0) / phoneAspect)
        assertEquals(tan(vTarget / 2.0) / tan(vPhone / 2.0), f.heightFrac, 1e-9)
    }

    @Test
    fun calibrationFactorShrinksTheFov() {
        val c = Framing.calibratedHFovRad(phoneH, 0.96)
        assertEquals(71.8, Math.toDegrees(c), 0.1)
        assertEquals(phoneH, Framing.calibratedHFovRad(phoneH, 1.0), 1e-12)
    }

    @Test
    fun portraitKeepsTheFrameHorizontalInTheWorld() {
        val t = Framing.target(36.0, 24.0, 50.0)
        val f = Framing.frame(t, phoneH, phoneAspect)
        val landscape = Framing.toScreen(f, phoneAspect, portrait = false)
        assertEquals(f, landscape)
        val portrait = Framing.toScreen(f, phoneAspect, portrait = true)
        // Pantalla en vertical: ancho = lado corto (S), alto = lado largo (L = aspecto × S). El recuadro debe seguir siendo 3:2 apaisado.
        val pixelAspect = (portrait.widthFrac * 1.0) / (portrait.heightFrac * phoneAspect)
        assertEquals(t.aspect, pixelAspect, 1e-9)
        assertTrue(portrait.widthFrac > portrait.heightFrac)
        // Mismo ángulo horizontal contra el lado corto: tan(Ht/2) / tan(Vp/2).
        val vPhone = 2.0 * Math.atan(tan(phoneH / 2.0) / phoneAspect)
        assertEquals(tan(t.hFovRad / 2.0) / tan(vPhone / 2.0), portrait.widthFrac, 1e-9)
    }

    @Test
    fun onlyTheCurrentFrameShrinksTheImage() {
        assertEquals(1f, Framing.imageScale(Framing.Frame(0.4, 0.3)), 1e-6f)
        assertEquals(1f, Framing.imageScale(null), 1e-6f)
        // Un recuadro más ancho que el teléfono (padding) achica la imagen a 1/ancho.
        assertEquals(0.5f, Framing.imageScale(Framing.Frame(2.0, 1.2)), 1e-6f)
    }
}
