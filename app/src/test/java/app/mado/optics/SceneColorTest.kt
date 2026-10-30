package app.mado.optics

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneColorTest {
    @Test
    fun sameSceneAlwaysGetsTheSameColor() {
        assertEquals(sceneColorArgb("暖炉"), sceneColorArgb("  暖炉 "))
        assertEquals(sceneColorArgb("Kitchen"), sceneColorArgb("kitchen"))
    }
}
