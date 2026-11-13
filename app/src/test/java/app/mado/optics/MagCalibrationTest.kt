package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class MagCalibrationTest {
    // Santiago: ~23 µT, inclinación −30° (apunta hacia arriba). Mundo: x = este, y = norte, z = arriba.
    private val strength = 23.0
    private val incl = -30.0
    private val earth = doubleArrayOf(0.0, strength * cos(Math.toRadians(incl)), -strength * sin(Math.toRadians(incl)))
    private val vertical = MagCalibration.expectedVerticalUt(strength, incl)
    private val trueBias = doubleArrayOf(80.0, -42.0, 55.0)

    /** Matriz de rotación dispositivo→mundo a partir de tres giros (rumbo, cabeceo, balanceo), como la que da el sistema. */
    private fun rot(yaw: Double, pitch: Double, roll: Double): DoubleArray {
        val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch); val cr = cos(roll); val sr = sin(roll)
        return doubleArrayOf(
            cy * cr - sy * sp * sr, -sy * cp, cy * sr + sy * sp * cr,
            sy * cr + cy * sp * sr, cy * cp, sy * sr - cy * sp * cr,
            -cp * sr, sp, cp * cr,
        )
    }

    private fun toDevice(r: DoubleArray, w: DoubleArray) = doubleArrayOf(
        r[0] * w[0] + r[3] * w[1] + r[6] * w[2], r[1] * w[0] + r[4] * w[1] + r[7] * w[2], r[2] * w[0] + r[5] * w[1] + r[8] * w[2],
    )

    private fun feed(est: MagBiasEstimator, rnd: Random, count: Int, pitchRange: Double, rollRange: Double, noise: Double = 0.4) {
        repeat(count) {
            val r = rot(rnd.nextDouble() * 6.28, (rnd.nextDouble() - 0.5) * pitchRange, (rnd.nextDouble() - 0.5) * rollRange)
            val b = toDevice(r, earth)
            val raw = DoubleArray(3) { b[it] + trueBias[it] + rnd.nextGaussian() * noise }
            val g = toDevice(r, doubleArrayOf(0.0, 0.0, 9.81))
            val acc = DoubleArray(3) { g[it] + rnd.nextGaussian() * 0.05 }
            est.add(raw, acc)
        }
    }

    @Test
    fun recoversTheBiasFromVariedOrientations() {
        val est = MagBiasEstimator(vertical)
        feed(est, Random(7), 400, pitchRange = 3.0, rollRange = 6.0)         // de pie, de lado y boca abajo, en todos los rumbos
        assertTrue("debía estar listo", est.ready)
        val b = est.solve()!!
        for (i in 0..2) assertEquals("eje $i", trueBias[i], b[i], 0.3)
        assertTrue("el ajuste debe ser fino", est.residualRms(b) < 1.0)
        assertEquals(1.0, est.coverage(), 1e-9)
    }

    @Test
    fun refusesWhenThePhoneOnlyTurnsAroundTheVerticalAxis() {
        val est = MagBiasEstimator(vertical)
        // Plano sobre la mesa, girando como un plato: «arriba» no cambia y el sesgo horizontal no se puede separar del campo.
        feed(est, Random(3), 400, pitchRange = 0.0, rollRange = 0.0)
        assertFalse(est.ready)
        assertNull(est.solve())
        assertTrue(est.coverage() < 0.9)
    }

    @Test
    fun ignoresSamplesWhileTheEstimatorIsMoving() {
        val est = MagBiasEstimator(vertical)
        assertFalse(est.add(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(0.0, 0.0, 14.0)))     // sacudida
        assertFalse(est.add(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(0.0, 0.0, 5.0)))      // caída libre
        assertEquals(0, est.n)
        assertTrue(est.add(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(0.0, 0.0, 9.8)))
        assertEquals(1, est.n)
    }

    @Test
    fun minEigenMatchesKnownMatrices() {
        assertEquals(0.2, MagBiasEstimator.minEigen(doubleArrayOf(0.5, 0.0, 0.0, 0.0, 0.3, 0.0, 0.0, 0.0, 0.2)), 1e-9)
        assertEquals(1.0 / 3, MagBiasEstimator.minEigen(doubleArrayOf(1.0 / 3, 0.0, 0.0, 0.0, 1.0 / 3, 0.0, 0.0, 0.0, 1.0 / 3)), 1e-9)
        // Matriz con parte fuera de la diagonal: autovalores 1 y 3 en el bloque 2×2, y 5.
        assertEquals(1.0, MagBiasEstimator.minEigen(doubleArrayOf(2.0, 1.0, 0.0, 1.0, 2.0, 0.0, 0.0, 0.0, 5.0)), 1e-9)
        assertNotNull(MagBiasEstimator.solve3(doubleArrayOf(2.0, 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 4.0), doubleArrayOf(2.0, 6.0, 12.0)))
        assertEquals(3.0, MagBiasEstimator.solve3(doubleArrayOf(2.0, 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 4.0), doubleArrayOf(2.0, 6.0, 12.0))!![2], 1e-9)
        assertNull(MagBiasEstimator.solve3(DoubleArray(9), doubleArrayOf(1.0, 1.0, 1.0)))
    }

    @Test
    fun expectedVerticalPointsUpInTheSouthernHemisphere() {
        assertEquals(11.5, MagCalibration.expectedVerticalUt(23.0, -30.0), 1e-9)       // Santiago: el campo sube
        assertEquals(-40.0, MagCalibration.expectedVerticalUt(46.0, 60.4), 0.5)        // hemisferio norte: el campo baja
    }

    @Test
    fun headingIsCorrectedWhenTheSystemNorthIsWrong() {
        // El sistema cree que la cámara mira a 65° desde su norte; en realidad mira al norte magnético.
        // En el «mundo» del sistema, el campo horizontal apunta entonces a 65° (este = sen, norte = cos).
        val e = 65.0
        val bh = 20.0
        val h = MagCalibration.correctedHeading(e, bh * sin(Math.toRadians(e)), bh * cos(Math.toRadians(e)))
        assertEquals(0.0, h, 1e-6)
        // Si el sistema acierta, el campo horizontal apunta al norte y el rumbo no cambia.
        assertEquals(250.0, MagCalibration.correctedHeading(250.0, 0.0, 20.0), 1e-9)
        // Cruce por 0°/360°.
        assertEquals(350.0, MagCalibration.correctedHeading(10.0, bh * sin(Math.toRadians(20.0)), bh * cos(Math.toRadians(20.0))), 1e-6)
    }
}
