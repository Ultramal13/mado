package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Test

class AspectAndDiagonalTest {
    @Test
    fun diagonalFovForA3by2Image() {
        // H 58.5° con 3:2 → D ≈ 67.8° (un visor de producción publica 68.4° con un sensor casi 3:2).
        val d = Math.toDegrees(Framing.diagonalFovRad(Math.toRadians(58.5), 1.5))
        assertEquals(67.8, d, 0.2)
    }

    @Test
    fun aspectLabelsAreReadable() {
        assertEquals("3:2", aspectLabel(1.5))
        assertEquals("16:9", aspectLabel(1.78))
        assertEquals("17:9", aspectLabel(1.895))
        assertEquals("2.39:1", aspectLabel(2.4))
        assertEquals("1.43:1", aspectLabel(1.43))
    }

    @Test
    fun deliveryRectIsCenteredInsideTheFrame() {
        val (w, h) = deliveryRect(300.0, 200.0, 16.0 / 9.0)          // 3:2 → 16:9: se recorta arriba y abajo
        assertEquals(300.0, w, 1e-9); assertEquals(168.75, h, 1e-9)
        val (w2, h2) = deliveryRect(300.0, 200.0, 1.0)               // 1:1: se recorta a los lados
        assertEquals(200.0, w2, 1e-9); assertEquals(200.0, h2, 1e-9)
    }
}
