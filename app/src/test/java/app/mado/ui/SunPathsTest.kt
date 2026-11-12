package app.mado.ui

import app.mado.optics.SunMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SunPathsTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val date = LocalDate.of(2026, 11, 11)

    @Test fun seasonsAddThreePathsAndTodayStaysOnTop() {
        val raw = computeRawPaths(35.6762, 139.6503, date, zone, true)
        assertEquals(4, raw.size)
        assertTrue(raw.last().hours); assertTrue(raw.dropLast(1).none { it.hours })
        assertTrue(raw.dropLast(1).all { it.label != null })
        assertEquals(1, computeRawPaths(35.6762, 139.6503, date, zone, false).size)
    }

    @Test fun projectedPathCrossesTheImageWhenTheCameraFacesTheSun() {
        val raw = computeRawPaths(35.6762, 139.6503, date, zone, false)
        val noon = SunMath.solarNoon(35.6762, 139.6503, date, zone)
        val p = SunMath.position(35.6762, 139.6503, noon)
        val (hw, hh) = SunMath.halfTans(70.0, 16.0 / 9.0)
        val arcs = projectPaths(raw, 0.0, p.azimuthDeg, p.elevationDeg, hw, hh, noon)
        val pts = arcs.single().points
        assertTrue(pts.filterNotNull().any { Math.abs(it.x) < 0.2 && Math.abs(it.y) < 0.2 })     // a mediodía pasa por el centro
        assertTrue(pts.filterNotNull().any { it.hour != null })                                   // hay marcas por hora
        val past = pts.filterNotNull().filter { it.past }
        assertNotNull(past.firstOrNull()); assertTrue(past.size < pts.filterNotNull().size)       // lo ya pasado se atenúa y lo que viene no
    }

    @Test fun pointsBehindTheCameraAreDropped() {
        val raw = computeRawPaths(35.6762, 139.6503, date, zone, false)
        val (hw, hh) = SunMath.halfTans(70.0, 16.0 / 9.0)
        val arcs = projectPaths(raw, 0.0, 0.0, 0.0, hw, hh, null)         // mirando al norte: el sol de noviembre pasa por el sur
        assertTrue(arcs.single().points.all { it == null })
    }
}
