package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FovTest {
    @Test
    fun fullFrame50mmIs39point6Degrees() {
        assertEquals(39.6, Fov.fovDeg(36.0, 50.0), 0.05)
    }

    @Test
    fun equivalentFocalOfFullFrameIsItself() {
        assertEquals(24.0, Fov.equivalentFocal35(36.0, 24.0), 1e-9)
    }

    @Test
    fun diagonalEquivalentMatchesWhatManufacturersPublish() {
        // Un cuadro 36×24 es su propio equivalente, por ancho o por diagonal.
        assertEquals(24.0, Fov.equivalentFocal35Diagonal(36.0, 24.0, 24.0), 1e-9)
        // Sensor 4:3 de 8×6 mm con 71.6° horizontales (el lente principal de un teléfono): por diagonal son 24 mm, por ancho 25.
        val f = 4.0 / Math.tan(Math.toRadians(35.8))
        assertEquals(24.0, Fov.equivalentFocal35Diagonal(8.0, 6.0, f), 0.1)
        assertEquals(24.95, Fov.equivalentFocal35(8.0, f), 0.1)
        // Lo que informó Edo con su Pixel: 25 / 13 / 109 por ancho son 24 / 12.5 / 105 por diagonal (factor 0.96 en un sensor 4:3).
        assertEquals(24.0, 25.0 * 0.9617, 0.1)
        assertEquals(104.8, 109.0 * 0.9617, 0.1)
    }

    @Test
    fun frameFactorIsOneForSameFov() {
        val f = Fov.fovRad(36.0, 35.0)
        assertEquals(1.0, Fov.frameFactor(f, f), 1e-9)
    }

    @Test
    fun narrowerTargetGivesFactorBelowOne() {
        val phone = Fov.fovRad(36.0, 24.0)
        val target = Fov.fovRad(36.0, 50.0)
        assertEquals(0.48, Fov.frameFactor(target, phone), 1e-9)
    }

    @Test
    fun measuredFovFromTapeMeasure() {
        // 2 m de ancho visibles a 1 m de distancia = 90°.
        assertEquals(90.0, Fov.measuredFovDeg(200.0, 100.0), 1e-9)
    }

    @Test
    fun croppedWidthKeepsWidthWhenStreamIsNarrowerThanSensor() {
        assertEquals(9.0, Fov.croppedWidthMm(9.0, 6.0, 16.0 / 9.0), 1e-9)
        assertEquals(6.0 * 1.0, Fov.croppedWidthMm(9.0, 6.0, 1.0), 1e-9)
    }

    @Test
    fun intrinsicFovMatchesTheS26MainLens() {
        // Galaxy S26 Ultra, lente 5: fx = 2796.51 sobre un área activa de 4080x3060 (calibración interna) → 72.2° (cinta: 71.8°).
        val v = Fov.intrinsicFovDeg(4080, 3060, 2796.51, 4.0 / 3.0)!!
        assertEquals(72.2, v, 0.05)
        // Un stream 16:9 de un sensor 4:3 no recorta el ancho.
        assertEquals(v, Fov.intrinsicFovDeg(4080, 3060, 2796.51, 16.0 / 9.0)!!, 1e-9)
        // Un stream más angosto que el sensor sí lo recorta.
        assertTrue(Fov.intrinsicFovDeg(4080, 3060, 2796.51, 1.0)!! < v)
        assertEquals(null, Fov.intrinsicFovDeg(4080, 3060, 0.0, 1.33))
    }
}
