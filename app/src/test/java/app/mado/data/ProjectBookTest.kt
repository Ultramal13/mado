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

class LocationSheetTest {
    private fun entry(file: String, place: String, scene: String, no: Int, time: String, extra: String = "") =
        """{"file":"$file","uri":"content://x/$file","meta":{"scene":"$scene","shot_no":$no,"time":"$time","target":{"label":"Canon EOS R5 · Photo 3:2","focal_mm":35.0},"place":{"id":"$place"},"project":{"id":"p1"}$extra}}"""

    private val op = ""","operator":{"fps":29.97,"shutter":"1/60","iso":800,"white_balance_k":5600,"picture_profile":"PP10","note":"LUT x","external_recording":"Ninja"},"orientation":{"heading_deg_magnetic":348.4}"""
    private val json = "[" + listOf(
        entry("MADO_2_marco.jpg", "l1", "stairs", 1, "2026-11-02T10:05:00Z"), entry("MADO_2_plano.jpg", "l1", "stairs", 1, "2026-11-02T10:05:00Z"),
        entry("MADO_1_marco.jpg", "l1", "kitchen", 2, "2026-11-02T10:00:00Z", op), entry("MADO_1_plano.jpg", "l1", "kitchen", 2, "2026-11-02T10:00:00Z", op),
        entry("MADO_0_marco.jpg", "l1", "Kitchen", 1, "2026-11-02T09:55:00Z"),
        entry("MADO_9_marco.jpg", "l2", "garden", 1, "2026-11-02T09:00:00Z"),
    ).joinToString(",") + "]"
    private val sheet = LocationSheets.build("Fuyu", Place("l1", "Footprints", "Saitama", "parking: 2 cars"), ShotLog.parse(json))

    @Test
    fun groupsByPlaceSceneAndShotNumber() {
        assertEquals(listOf("Kitchen", "stairs"), sheet.scenes.map { it.name })          // por hora de la primera toma
        assertEquals(listOf(1, 2), sheet.scenes[0].shots.map { it.shotNo })
        assertEquals(3, sheet.shotCount)                                                // el jardín es de otra locación
        assertEquals("content://x/MADO_1_plano.jpg", sheet.scenes[0].shots[1].planoUri)
        assertEquals(null, sheet.scenes[0].shots[0].planoUri)
        assertEquals(listOf("Canon EOS R5"), sheet.cameras)
    }

    @Test
    fun operatorAndNotesComeFromTheMetadata() {
        val s = sheet.scenes[0].shots[1]
        assertEquals("29.97p · 1/60 · ISO 800 · 5600K", s.operator)
        assertEquals("PP10 · LUT x · EXT Ninja", s.notes)
        assertEquals(348.4, s.headingDeg!!, 1e-9)
        assertEquals("", sheet.scenes[1].shots[0].operator)
    }

    @Test
    fun csvQuotesAndKeepsJapanese() {
        val s = LocationSheets.build("ふゆ\"キュン\"", Place("l1", "暖炉, 部屋"), ShotLog.parse(json))
        val csv = LocationSheets.csv(s, java.time.ZoneId.of("UTC"))
        assertTrue(csv.startsWith("﻿\"project\""))
        assertTrue(csv.contains("\"ふゆ\"\"キュン\"\"\",\"暖炉, 部屋\",\"Kitchen\",\"1\",\"2026-11-02 09:55\""))
        assertEquals(1 + 3, csv.trim().split("\r\n").size)
    }

    @Test
    fun localTimeFallsBackToTheRawText() {
        assertEquals("2026-11-03 07:00", LocationSheets.localTime("2026-11-02T22:00:00Z", java.time.ZoneId.of("Asia/Tokyo")))
        assertEquals("sin hora", LocationSheets.localTime("sin hora"))
    }
}
