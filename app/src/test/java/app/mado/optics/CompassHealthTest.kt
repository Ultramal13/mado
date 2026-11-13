package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassHealthTest {
    // Santiago: campo de ~23 µT que apunta hacia arriba (inclinación ≈ −30°). Teléfono en reposo sobre la mesa: la aceleración apunta hacia arriba (z).
    private val up = doubleArrayOf(0.0, 0.0, 9.81)

    private fun earthField(strength: Double, inclinationDeg: Double): DoubleArray {
        // Teléfono plano: horizontal = norte (y); componente vertical hacia arriba si la inclinación es negativa.
        val i = Math.toRadians(inclinationDeg)
        return doubleArrayOf(0.0, strength * Math.cos(i), -strength * Math.sin(i))
    }

    @Test
    fun inclinationFollowsTheFieldDirection() {
        assertEquals(-30.0, CompassHealth.inclinationDeg(earthField(23.0, -30.0), up)!!, 1e-6)
        assertEquals(60.0, CompassHealth.inclinationDeg(earthField(46.0, 60.0), up)!!, 1e-6)
        assertNotNull(CompassHealth.inclinationDeg(doubleArrayOf(1.0, 0.0, 0.0), up))
        assertEquals(null, CompassHealth.inclinationDeg(doubleArrayOf(0.0, 0.0, 0.0), up))
    }

    @Test
    fun healthyFieldPassesAndStrangeFieldsDoNot() {
        val b = earthField(23.0, -30.0)
        assertFalse(CompassHealth.fieldDisturbed(CompassHealth.strengthUt(b), CompassHealth.inclinationDeg(b, up), 23.0, -30.0))
        // Indoors the field drifts 25 %: still fine.
        val indoors = earthField(23.0 * 1.25, -30.0)
        assertFalse(CompassHealth.fieldDisturbed(CompassHealth.strengthUt(indoors), CompassHealth.inclinationDeg(indoors, up), 23.0, -30.0))
        // A magnet or a charging cable: three times the Earth field.
        assertTrue(CompassHealth.fieldDisturbed(70.0, -30.0, 23.0, -30.0))
        // Same strength but pointing somewhere the Earth's field never points here.
        val odd = earthField(23.0, 40.0)
        assertTrue(CompassHealth.fieldDisturbed(CompassHealth.strengthUt(odd), CompassHealth.inclinationDeg(odd, up), 23.0, -30.0))
        // Without an expected value there is nothing to compare.
        assertFalse(CompassHealth.fieldDisturbed(500.0, 10.0, 0.0, 0.0))
    }

    @Test
    fun impreciseIsASofterLevelThanDisturbed() {
        // Medido en terreno: 30–35 µT donde debían ser 23 dieron 11–17° de error.
        assertTrue(CompassHealth.fieldImprecise(31.0, -30.0, 23.0, -30.0))
        assertFalse(CompassHealth.fieldDisturbed(31.0, -30.0, 23.0, -30.0))
        // Un campo normal no avisa; tampoco la desviación típica de un interior (+15 %).
        assertFalse(CompassHealth.fieldImprecise(23.5, -31.0, 23.0, -30.0))
        assertFalse(CompassHealth.fieldImprecise(26.5, -30.0, 23.0, -30.0))
        // La inclinación sola también cuenta.
        assertTrue(CompassHealth.fieldImprecise(23.0, -10.0, 23.0, -30.0))
        assertFalse(CompassHealth.fieldImprecise(500.0, 10.0, 0.0, 0.0))
    }

    @Test
    fun wrapKeepsAnglesInRange() {
        assertEquals(-10.0, CompassHealth.wrap(350.0), 1e-9)
        assertEquals(10.0, CompassHealth.wrap(-350.0), 1e-9)
        assertEquals(-170.0, CompassHealth.wrap(190.0), 1e-9)
    }

    @Test
    fun rotatingThePhoneIsNotADisturbance() {
        val d = HeadingJumpDetector()
        var t = 0L
        // Ambos sensores giran juntos 90° en 3 s (con un desfase fijo entre sus ceros): nunca hay alteración.
        for (i in 0..60) {
            val game = i * 1.5
            assertFalse(d.update(t, game + 137.0, game))
            t += 50
        }
    }

    @Test
    fun aMagneticJumpWithoutRotationIsDetectedAfterSettling() {
        val d = HeadingJumpDetector()
        var t = 0L
        repeat(20) { assertFalse(d.update(t, 270.0, 10.0)); t += 50 }
        // El campo se altera: el magnético salta 70° y el giroscopio no se mueve.
        var detectedAt = -1L
        repeat(40) { i ->
            if (d.update(t, 200.0, 10.0) && detectedAt < 0) detectedAt = t
            t += 50
        }
        assertTrue("debía detectarlo", detectedAt >= 0)
        assertTrue("no debe saltar al primer instante", detectedAt - 1000L >= 700L)
        assertTrue(d.disturbed)
        // Vuelve el campo sano: el aviso se apaga.
        repeat(5) { d.update(t, 270.0, 10.0); t += 50 }
        assertFalse(d.disturbed)
    }

    @Test
    fun aShortGlitchIsIgnored() {
        val d = HeadingJumpDetector()
        var t = 0L
        repeat(10) { d.update(t, 270.0, 10.0); t += 50 }
        repeat(6) { assertFalse(d.update(t, 200.0, 10.0)); t += 50 }       // 300 ms: menos que el tiempo de espera
        repeat(5) { assertFalse(d.update(t, 270.0, 10.0)); t += 50 }
    }

    @Test
    fun aLongLastingChangeBecomesTheNewNormal() {
        val d = HeadingJumpDetector(acceptAfterMs = 5_000L)
        var t = 0L
        repeat(10) { d.update(t, 270.0, 10.0); t += 50 }
        repeat(200) { d.update(t, 200.0, 10.0); t += 50 }                   // 10 s con el campo cambiado
        assertFalse("pasado el plazo se acepta como nuevo estado", d.disturbed)
    }

    @Test
    fun savedCalibrationIsReusedOnlyWhileFreshAndNearby() {
        val now = 1_000_000_000_000L
        val h = 3600_000L
        assertTrue(CalibrationMemory.shouldRestore(now - 2 * h, now, -33.54, -70.66, -33.54, -70.66))
        assertTrue("sin lugar conocido se reutiliza", CalibrationMemory.shouldRestore(now - h, now, -33.54, -70.66, null, null))
        assertFalse("pasó más de un día", CalibrationMemory.shouldRestore(now - 25 * h, now, -33.54, -70.66, -33.54, -70.66))
        assertFalse("otra ciudad", CalibrationMemory.shouldRestore(now - h, now, -33.54, -70.66, 35.68, 139.69))
        assertFalse("nunca se guardó", CalibrationMemory.shouldRestore(0L, now, null, null, null, null))
        assertFalse("reloj atrasado", CalibrationMemory.shouldRestore(now + h, now, null, null, null, null))
    }
}
