package app.mado.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusMarkTest {
    @Test fun autofocusStatesAreClassified() {
        assertTrue(FocusMark(0.5f, 0.5f, 4).focused); assertTrue(FocusMark(0.5f, 0.5f, 2).focused)
        assertTrue(FocusMark(0.5f, 0.5f, 5).failed); assertTrue(FocusMark(0.5f, 0.5f, 6).failed)
        assertFalse(FocusMark(0.5f, 0.5f, 3).focused); assertFalse(FocusMark(0.5f, 0.5f, 3).failed); assertFalse(FocusMark(0.5f, 0.5f, null).focused)
    }

    @Test fun markMovesIntoACenteredCrop() {
        // Recorte del 50 % del ancho y del alto: el centro sigue en el centro y el punto a 3/4 pasa al borde del recorte.
        assertEquals(0.5f, FocusMark(0.5f, 0.5f, 4).inCrop(0.5f, 0.5f)!!.x, 1e-6f)
        val edge = FocusMark(0.75f, 0.25f, 4).inCrop(0.5f, 0.5f)!!
        assertEquals(1.0f, edge.x, 1e-6f); assertEquals(0.0f, edge.y, 1e-6f)
        assertNull(FocusMark(0.9f, 0.5f, 4).inCrop(0.5f, 0.5f))                  // fuera del recorte
        assertNotNull(FocusMark(0.9f, 0.5f, 4).inCrop(1.2f, 1.0f))               // recorte con padding: la foto es más chica que el lienzo
        assertEquals("0.41 m", FocusMark(0.5f, 0.5f, 4, "0.41 m").inCrop(1f, 1f)!!.label)
    }
}
