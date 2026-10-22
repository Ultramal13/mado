package app.mado.optics

import org.junit.Assert.assertEquals
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
}
