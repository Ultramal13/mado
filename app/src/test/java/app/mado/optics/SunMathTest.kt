package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Valores de referencia generados con la librería astronómica `ephem` (JPL/VSOP, sin refracción), independiente del algoritmo de la app.
 */
class SunMathTest {
    private fun at(iso: String) = Instant.parse(iso)

    private fun check(lat: Double, lon: Double, iso: String, az: Double, el: Double) {
        val p = SunMath.position(lat, lon, at(iso))
        assertEquals("azimut $iso", az, p.azimuthDeg, 0.15)
        assertEquals("elevación $iso", el, p.geometricElevationDeg, 0.1)
    }

    @Test fun positionMatchesReferenceInBothHemispheres() {
        check(-33.4489, -70.6693, "2026-11-11T15:00:00Z", 56.060, 64.875)     // Santiago
        check(35.6762, 139.6503, "2026-11-11T03:00:00Z", 190.264, 36.296)     // Tokio, otoño
        check(35.6762, 139.6503, "2026-06-21T02:30:00Z", 166.026, 77.435)     // Tokio, solsticio
        check(64.1466, -21.9426, "2026-12-21T12:00:00Z", 160.386, 0.824)      // Reikiavik, sol bajo
        check(-33.8688, 151.2093, "2026-01-15T20:00:00Z", 108.276, 10.599)    // Sídney, verano
        check(0.0, 0.0, "2026-03-20T12:00:00Z", 91.400, 88.140)               // ecuador, equinoccio
    }

    @Test fun sunriseAndSunsetMatchReferenceWithinTwoMinutes() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val d = SunMath.day(35.6762, 139.6503, LocalDate.of(2026, 11, 11), tokyo)
        near("2026-11-10T21:12:44Z", d.sunrise)
        near("2026-11-11T07:37:37Z", d.sunset)
        near("2026-11-10T21:49:30Z", d.goldenMorningEnd)           // sol a +6°
        near("2026-11-11T07:00:52Z", d.goldenEveningStart)
        near("2026-11-10T20:56:10Z", d.blueMorningEnd)             // −4°
        near("2026-11-10T20:35:33Z", d.blueMorningStart)           // −8°
        near("2026-11-11T07:54:11Z", d.blueEveningStart)           // −4°
        near("2026-11-11T08:14:48Z", d.blueEveningEnd)             // −8°
        val santiago = SunMath.day(-33.4489, -70.6693, LocalDate.of(2026, 11, 11), ZoneId.of("America/Santiago"))
        near("2026-11-11T09:34:24Z", santiago.sunrise)
        near("2026-11-11T23:19:30Z", santiago.sunset)
    }

    private fun near(iso: String, actual: Instant?) {
        assertNotNull("falta $iso", actual)
        val diff = abs(actual!!.epochSecond - at(iso).epochSecond)
        assertTrue("$iso vs $actual: ${diff}s de diferencia", diff <= 120)
    }

    @Test fun polarNightHasNoSunriseAndMidnightSunHasNoSunset() {
        val night = SunMath.day(78.22, 15.65, LocalDate.of(2026, 12, 21), ZoneId.of("Arctic/Longyearbyen"))
        assertNull(night.sunrise); assertNull(night.sunset)
        val summer = SunMath.day(78.22, 15.65, LocalDate.of(2026, 6, 21), ZoneId.of("Arctic/Longyearbyen"))
        assertNull(summer.sunrise); assertNull(summer.sunset)
    }

    @Test fun refractionLiftsTheSunAtTheHorizon() {
        assertEquals(0.0, SunMath.refractionDeg(90.0), 1e-9)
        assertEquals(0.48, SunMath.refractionDeg(0.0), 0.03)       // fórmula de la NOAA: 1735″ en el horizonte
        assertTrue(SunMath.refractionDeg(10.0) < SunMath.refractionDeg(1.0))
    }

    @Test fun magneticAzimuthSubtractsEastDeclination() {
        assertEquals(350.0, SunMath.trueToMagnetic(10.0, 20.0), 1e-9)       // declinación este 20°
        assertEquals(30.0, SunMath.trueToMagnetic(10.0, -20.0), 1e-9)       // declinación oeste 20°
    }

    @Test fun projectionPutsASunOnTheAxisAtTheCenter() {
        val (hw, hh) = SunMath.halfTans(60.0, 16.0 / 9.0)
        val p = SunMath.project(120.0, 30.0, 120.0, 30.0, hw, hh)
        assertTrue(p.inFront); assertEquals(0.0, p.x, 1e-9); assertEquals(0.0, p.y, 1e-9)
    }

    @Test fun sunToTheRightAndAboveGivesPositiveXandY() {
        val (hw, hh) = SunMath.halfTans(60.0, 16.0 / 9.0)
        val p = SunMath.project(130.0, 35.0, 120.0, 30.0, hw, hh)
        assertTrue(p.x > 0.0); assertTrue(p.y > 0.0)
        val left = SunMath.project(110.0, 25.0, 120.0, 30.0, hw, hh)
        assertTrue(left.x < 0.0); assertTrue(left.y < 0.0)
    }

    @Test fun sunAtTheEdgeOfTheFieldProjectsToOne() {
        val (hw, hh) = SunMath.halfTans(60.0, 1.5)
        val p = SunMath.project(90.0 + 30.0, 0.0, 90.0, 0.0, hw, hh)        // 30° a la derecha con campo de 60°
        assertEquals(1.0, p.x, 1e-6); assertEquals(0.0, p.y, 1e-6); assertTrue(p.inside)
    }

    @Test fun sunBehindTheCameraIsNotInFront() {
        val (hw, hh) = SunMath.halfTans(60.0, 1.5)
        val p = SunMath.project(270.0, 10.0, 90.0, 10.0, hw, hh)
        assertFalse(p.inFront); assertFalse(p.inside)
    }

    @Test fun angularDistanceIsSymmetricAndZeroOnTheAxis() {
        assertEquals(0.0, SunMath.angularDistanceDeg(100.0, 20.0, 100.0, 20.0), 1e-6)
        assertEquals(90.0, SunMath.angularDistanceDeg(190.0, 0.0, 100.0, 0.0), 1e-6)
        assertEquals(SunMath.angularDistanceDeg(10.0, 40.0, 200.0, 5.0), SunMath.angularDistanceDeg(200.0, 5.0, 10.0, 40.0), 1e-9)
    }

    @Test fun pathCoversTheDayAndPeaksAtNoonElevation() {
        val zone = ZoneId.of("Asia/Tokyo")
        val path = SunMath.path(35.6762, 139.6503, LocalDate.of(2026, 11, 11), zone)
        assertTrue(path.size in 50..100)
        assertTrue(path.all { it.elevationDeg > -3.0 })
        assertEquals(37.0, path.maxOf { it.elevationDeg }, 1.0)           // 90° − latitud + declinación (−17°) ≈ 37°
    }

    @Test fun frameWindowContainsSolarNoonWhenAimedAtTheSun() {
        val zone = ZoneId.of("Asia/Tokyo"); val date = LocalDate.of(2026, 11, 11)
        val noon = SunMath.solarNoon(35.6762, 139.6503, date, zone)
        val p = SunMath.position(35.6762, 139.6503, noon)
        val (hw, hh) = SunMath.halfTans(60.0, 1.5)
        val w = SunMath.frameWindow(35.6762, 139.6503, date, zone, 0.0, p.azimuthDeg, p.elevationDeg, hw, hh, 0.5, 0.5)!!
        assertTrue(!w.first.isAfter(noon) && !w.second.isBefore(noon))
        assertTrue("ventana de ${(w.second.epochSecond - w.first.epochSecond) / 60} min", w.second.epochSecond - w.first.epochSecond < 4 * 3600)
    }

    @Test fun frameWindowIsNullWhenAimedAwayFromTheSunPath() {
        val zone = ZoneId.of("Asia/Tokyo"); val date = LocalDate.of(2026, 11, 11)
        val (hw, hh) = SunMath.halfTans(60.0, 1.5)
        assertNull(SunMath.frameWindow(35.6762, 139.6503, date, zone, 0.0, 0.0, 0.0, hw, hh))   // al norte, a ras del horizonte
    }
}
