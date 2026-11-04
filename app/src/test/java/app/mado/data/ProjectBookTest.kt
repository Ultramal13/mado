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
        assertTrue(csv.lines()[0].contains("\"camera\",\"lens_set\",\"focal_mm\""))
        assertEquals(1 + 3, csv.trim().split("\r\n").size)
    }

    @Test
    fun localTimeFallsBackToTheRawText() {
        assertEquals("2026-11-03 07:00", LocationSheets.localTime("2026-11-02T22:00:00Z", java.time.ZoneId.of("Asia/Tokyo")))
        assertEquals("sin hora", LocationSheets.localTime("sin hora"))
    }
}

class PdfJpegTest {
    private fun objs(): String = buildString {
        append("%PDF-1.4\n%Óëéá\n")
        append("1 0 obj\n<</Producer (Skia)>>\nendobj\n")
        append("2 0 obj\n<</Type /Catalog\n/Pages 3 0 R>>\nendobj\n")
        append("3 0 obj\n<</Type /Pages\n/Count 0\n/Kids []>>\nendobj\n")
        append("4 0 obj\n<</Type /XObject\n/Subtype /Image\n/Width 9000\n/Height 2\n/ColorSpace /DeviceRGB\n/BitsPerComponent 8\n/Filter /FlateDecode\n/Length 4>> stream\nABCD\nendstream\nendobj\n")
        append("5 0 obj\n<</Length 9>> stream\nendobj\nxx\nendstream\nendobj\n")        // un flujo cuyo contenido incluye «endobj»
    }

    private fun jpeg(w: Int, h: Int): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xC0.toByte(), 0, 17, 8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun replacesTheMarkedImageAndRebuildsTheXref() {
        val body = objs()
        val offs = Regex("""(\d+) 0 obj\n""").findAll(body).map { it.range.first }.toList()
        val xref = "xref\n0 6\n0000000000 65535 f \n" + offs.joinToString("") { "%010d 00000 n \n".format(it) } +
            "trailer\n<</Size 6\n/Root 2 0 R\n/Info 1 0 R>>\nstartxref\n${body.length}\n%%EOF\n"
        val src = (body + xref).toByteArray(Charsets.ISO_8859_1)
        val j = jpeg(1500, 900)
        val out = PdfJpeg.swap(src, mapOf(9000 to PdfJpeg.Jpeg(j)))!!
        val text = String(out, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Filter /DCTDecode") && text.contains("/Width 1500") && text.contains("/Height 900"))
        assertTrue(text.contains("5 0 obj\n<</Length 9>> stream\nendobj\nxx\nendstream\nendobj\n"))       // el otro flujo queda intacto
        // Cada entrada de la nueva tabla xref apunta al inicio exacto de su objeto.
        val newOffsets = Regex("""(\d{10}) 00000 n \n""").findAll(text.substring(text.indexOf("xref\n"))).map { it.groupValues[1].toInt() }.toList()
        assertEquals(5, newOffsets.size)
        newOffsets.forEachIndexed { i, o -> assertTrue(text.startsWith("${i + 1} 0 obj\n", o)) }
        assertEquals(text.indexOf("xref\n"), Regex("""startxref\n(\d+)""").find(text)!!.groupValues[1].toInt())
        assertTrue(text.contains("/Root 2 0 R") && text.contains("/Info 1 0 R"))
    }

    @Test
    fun acceptsObjectsWrittenOutOfOrder() {
        // Android escribe los objetos mezclados (1, 3, 2…): la nueva xref debe seguir el número, no el orden del archivo.
        val body = "%PDF-1.4\n%\u00d3\u00eb\u00e9\u00e1\n1 0 obj\n<</Producer (x)>>\nendobj\n3 0 obj\n<</Type /Pages>>\nendobj\n" +
            "2 0 obj\n<</Type /Catalog\n/Pages 3 0 R>>\nendobj\n"
        val src = (body + "xref\n0 4\n0000000000 65535 f \n0000000000 00000 n \n0000000000 00000 n \n0000000000 00000 n \n" +
            "trailer\n<</Size 4\n/Root 2 0 R>>\nstartxref\n${body.length}\n%%EOF\n").toByteArray(Charsets.ISO_8859_1)
        val text = String(PdfJpeg.swap(src, emptyMap())!!, Charsets.ISO_8859_1)
        val offs = Regex("(\\d{10}) 00000 n \n").findAll(text.substring(text.indexOf("xref\n"))).map { it.groupValues[1].toInt() }.toList()
        assertEquals(3, offs.size)
        offs.forEachIndexed { i, o -> assertTrue(text.startsWith("${i + 1} 0 obj\n", o)) }
    }

    @Test
    fun returnsNullWhenTheFileIsNotWhatAndroidWrites() {
        assertEquals(null, PdfJpeg.swap("no es un pdf".toByteArray(), emptyMap()))
        assertEquals(null, PdfJpeg.swap("%PDF-1.4\n1 0 obj\n<</Length 99>> stream\nx\nendstream\nendobj\n".toByteArray(), emptyMap()))
    }

    @Test
    fun readsTheJpegSize() {
        assertEquals(1500 to 900, PdfJpeg.jpegSize(jpeg(1500, 900)))
    }
}

class ShotEditTest {
    private fun entry(file: String, scene: String, no: Int, extra: String = "") =
        """{"file":"$file","uri":"content://x/$file","meta":{"scene":"$scene","shot_no":$no,"time":"2026-11-02T10:00:00Z","place":{"id":"l1"}$extra}}"""
    private val op = ""","operator":{"fps":29.97,"shutter":"1/60","iso":800,"white_balance_k":5600,"picture_profile":"PP10","note":"LUT x","external_recording":"Ninja"}"""
    private val json = "[" + listOf(entry("MADO_1_marco.jpg", "kitchen", 1, op), entry("MADO_1_plano.jpg", "kitchen", 1, op), entry("MADO_2_marco.jpg", "stairs", 1)).joinToString(",") + "]"

    @Test
    fun operatorDataRoundTripsThroughTheMetadata() {
        val e = ShotLog.parse(json).first()
        assertEquals(OperatorData("29.97", "1/60", "800", "5600", "PP10", "LUT x", "Ninja"), e.op)
        assertEquals("29.97p · 1/60 · ISO 800 · 5600K", e.operator); assertEquals("PP10 · LUT x · EXT Ninja", e.notes)
    }

    @Test
    fun editChangesBothFilesOfTheShotAndNothingElse() {
        val edit = ShotEdit("cocina", 4, OperatorData(fps = "24", iso = "400", note = "ND 0.9"))
        val out = ShotLog.applyEdit(json, "MADO_1", edit)!!
        val e = ShotLog.parse(out)
        assertEquals(listOf("cocina", "cocina", "stairs"), e.map { it.scene })
        assertEquals(listOf(4, 4, 1), e.map { it.shotNo })
        assertEquals("24p · ISO 400", e[0].operator); assertEquals("24p · ISO 400", e[1].operator)
        assertEquals("ND 0.9", e[1].notes)
        assertEquals("", e[2].operator)
        assertEquals(null, ShotLog.applyEdit(json, "NOPE", edit))
    }

    @Test
    fun clearingEveryOperatorFieldRemovesTheOperatorBlock() {
        val out = ShotLog.applyEdit(json, "MADO_1", ShotEdit("kitchen", 1, OperatorData()))!!
        assertEquals(true, ShotLog.parse(out).all { it.op.isEmpty })
    }

    @Test
    fun removeDropsBothFilesAndReturnsTheirUris() {
        val (out, uris) = ShotLog.remove(json, "MADO_1")!!
        assertEquals(listOf("content://x/MADO_1_marco.jpg", "content://x/MADO_1_plano.jpg"), uris)
        assertEquals(listOf("MADO_2"), ShotLog.parse(out).map { it.shotKey })
        assertEquals(null, ShotLog.remove(json, "NOPE"))
    }
}


class LensKitsTest {
    private val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())

    @Test
    fun namedKitsComeWithTheirSource() {
        val names = db.lensSets.map { it.id }
        assertTrue(names.containsAll(listOf("dzofilm_arles", "zeiss_cp3", "cooke_s4i", "atlas_orion_2x")))
        listOf("dzofilm_arles", "zeiss_cp3", "cooke_s4i", "atlas_orion_2x").forEach { id ->
            val k = db.lensSets.first { it.id == id }
            assertTrue("$id sin fuente", k.source?.text?.isNotBlank() == true)
            assertTrue("$id sin focales", k.focalsMm.size >= 10)
        }
        assertEquals(2.0, db.lensSets.first { it.id == "atlas_orion_2x" }.squeeze, 1e-9)
        assertEquals(listOf(15.0, 18.0, 21.0, 25.0, 28.0, 35.0, 50.0, 85.0, 100.0, 135.0), db.lensSets.first { it.id == "zeiss_cp3" }.focalsMm)
    }

    @Test
    fun parsesFocalsTolerantly() {
        assertEquals(listOf(18.0, 25.0, 35.0, 50.5), KitStore.parseFocals("50.5, 18 25mm;35 35 abc 0 99999"))
    }

    @Test
    fun storeRoundTripsAndIgnoresGarbage() {
        val kits = listOf(LensSet("custom_1", "Mis primes", null, 1.0, listOf(24.0, 50.0), custom = true), LensSet("custom_2", "Ana 1.5", null, 1.5, listOf(40.0), custom = true))
        assertEquals(kits, KitStore.parse(KitStore.toJson(kits)))
        assertTrue(KitStore.parse("basura").isEmpty())
        assertTrue(KitStore.parse(null).isEmpty())
    }
}
