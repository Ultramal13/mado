package app.mado.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBookTest {
    private fun sample() = ProjectBook()
        .addProject("Fuyu Kyun", "p1").addPlace("p1", "Footprints", "l1").addPlace("p1", "GrassFactory", "l2")

    @Test
    fun addingSelectsTheNewItem() {
        val b = sample()
        assertEquals("p1", b.activeProject); assertEquals("l2", b.activePlace)
        assertEquals("GrassFactory", b.place?.name)
        assertEquals(listOf("Footprints", "GrassFactory"), b.project?.places?.map { it.name })
    }

    @Test
    fun selectingAProjectPicksItsFirstPlace() {
        val b = sample().addProject("Otro", "p2").selectProject("p1")
        assertEquals("l1", b.activePlace)
        assertNull(b.selectProject("nope").project)
    }

    @Test
    fun updateAndDeletePlace() {
        val b = sample().updatePlace("p1", Place("l1", "Footprints", "Saitama", "parking: 2 cars"))
        assertEquals("Saitama", b.project!!.places.first { it.id == "l1" }.address)
        val d = b.deletePlace("p1", "l2")                        // era la activa
        assertEquals("l1", d.activePlace)
        assertEquals(1, d.project!!.places.size)
    }

    @Test
    fun deletingTheActiveProjectFallsBackToAnother() {
        val b = sample().addProject("Otro", "p2")
        val d = b.deleteProject("p2")
        assertEquals("p1", d.activeProject)
        assertEquals(null, d.deleteProject("p1").activeProject)
    }

    @Test
    fun jsonRoundTripKeepsEverythingIncludingJapanese() {
        val b = sample().addProject("ふゆキュン", "p9").addPlace("p9", "暖炉の部屋", "l9").let { it.updatePlace("p9", it.place!!.copy(address = "埼玉県", notes = "駐車場 2台")) }
        val r = ProjectBook.fromJson(b.toJson())
        assertEquals(b, r)
        assertEquals("駐車場 2台", r.place?.notes)
    }

    @Test
    fun brokenJsonGivesAnEmptyBook() {
        assertTrue(ProjectBook.fromJson("not json").projects.isEmpty())
        assertTrue(ProjectBook.fromJson(null).projects.isEmpty())
        // Una locación activa que ya no existe se descarta.
        val r = ProjectBook.fromJson("""{"active_project":"p1","active_place":"zz","projects":[{"id":"p1","name":"A","places":[{"id":"l1","name":"B"}]}]}""")
        assertEquals("p1", r.activeProject); assertNull(r.activePlace)
    }

    @Test
    fun storeSavesAndLoads() {
        val f = java.io.File.createTempFile("projects", ".json")
        try {
            val store = ProjectStore(f)
            store.save(sample())
            assertEquals(sample(), store.load())
        } finally { f.delete() }
    }
}

class ShotLogTest {
    private fun entry(file: String, place: String?, scene: String, no: Int) =
        """{"file":"$file","uri":"content://x/$file","meta":{"scene":"$scene","shot_no":$no,"project":{"id":"p1"}${if (place != null) ""","place":{"id":"$place"}""" else ""}}}"""

    private val json = "[" + listOf(
        entry("MADO_1_marco.jpg", "l1", "kitchen", 1), entry("MADO_1_plano.jpg", "l1", "kitchen", 1),
        entry("MADO_2_marco.jpg", "l1", "Kitchen", 2), entry("MADO_2_plano.jpg", "l1", "Kitchen", 2),
        entry("MADO_3_marco.jpg", "l1", "stairs", 1), entry("MADO_3_plano.jpg", "l1", "stairs", 1),
        entry("MADO_4_marco.jpg", "l2", "kitchen", 7), entry("MADO_5_marco.jpg", null, "", 0),
    ).joinToString(",") + "]"

    @Test
    fun groupsFilesIntoShotsAndScenesPerPlace() {
        val e = ShotLog.parse(json)
        assertEquals(listOf("kitchen" to 2, "stairs" to 1), ShotLog.scenesFor(e, "l1"))
        assertEquals(listOf("kitchen" to 1), ShotLog.scenesFor(e, "l2"))
    }

    @Test
    fun nextShotNumberIsPerSceneAndPlace() {
        val e = ShotLog.parse(json)
        assertEquals(3, ShotLog.nextShotNo(e, "l1", "KITCHEN"))
        assertEquals(2, ShotLog.nextShotNo(e, "l1", "stairs"))
        assertEquals(1, ShotLog.nextShotNo(e, "l1", "garden"))
        assertEquals(8, ShotLog.nextShotNo(e, "l2", "kitchen"))
    }

    @Test
    fun brokenLogIsEmpty() {
        assertTrue(ShotLog.parse("oops").isEmpty())
        assertTrue(ShotLog.parse(null).isEmpty())
    }
}
