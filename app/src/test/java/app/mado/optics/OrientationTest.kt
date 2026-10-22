package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTest {
    // Teléfono derecho (vertical), cámara al norte: world_x = d_x, world_y = −d_z, world_z = d_y.
    private val north = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
    // Teléfono derecho, cámara al este: world_x = −d_z, world_y = −d_x, world_z = d_y.
    private val east = floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)

    @Test
    fun uprightFacingNorth() {
        val p = OrientationMath.fromMatrix(north)
        assertEquals(0.0, p.headingDeg, 1e-6); assertEquals(0.0, p.elevationDeg, 1e-6); assertEquals(0.0, p.levelDeg, 1e-6)
    }

    @Test
    fun uprightFacingEast() {
        val p = OrientationMath.fromMatrix(east)
        assertEquals(90.0, p.headingDeg, 1e-6); assertEquals(0.0, p.elevationDeg, 1e-6)
    }

    @Test
    fun lookingUpGivesPositiveElevation() {
        // La dirección de la cámara en el mundo es −(3.ª columna): z del mundo = −r[8].
        val r = north.copyOf().also { it[8] = -0.5f }
        assertEquals(30.0, OrientationMath.fromMatrix(r).elevationDeg, 1e-4)
    }

    @Test
    fun landscapeCountsAsLevel() {
        assertEquals(0.0, OrientationMath.levelFromRoll(90.0), 1e-9)
        assertEquals(0.0, OrientationMath.levelFromRoll(-90.0), 1e-9)
        assertEquals(4.0, OrientationMath.levelFromRoll(4.0), 1e-9)
        assertEquals(-4.0, OrientationMath.levelFromRoll(86.0), 1e-9)
    }

    @Test
    fun compassPoints() {
        assertEquals(0, OrientationMath.compassIndex(359.0))
        assertEquals(0, OrientationMath.compassIndex(10.0))
        assertEquals(2, OrientationMath.compassIndex(90.0))
        assertEquals(6, OrientationMath.compassIndex(262.0))
    }
}
