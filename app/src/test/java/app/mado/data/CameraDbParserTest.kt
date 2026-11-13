package app.mado.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraDbParserTest {
    private val json = """
    {
      "schema_version": 1,
      "camaras": [
        {
          "id": "demo", "marca": "Demo", "modelo": "X1", "tipo": "lente_intercambiable", "montura": "E", "principal_orden": 2,
          "modos": [
            {"id": "ok", "nombre": "Completo", "ancho_activo_mm": 36.0, "alto_activo_mm": 24.0, "derivado": true,
             "fuente": {"texto": "Ficha", "url": "https://example.com"}},
            {"id": "falta", "nombre": "Incompleto", "ancho_activo_mm": null, "alto_activo_mm": null}
          ]
        },
        {
          "id": "fija", "marca": "Demo", "modelo": "F", "tipo": "lente_fija", "montura": null,
          "lentes_integradas": [{"id": "a", "nombre": "A", "focal_equivalente_ff_mm": 20}, {"id": "b", "nombre": "B"}],
          "modos": []
        }
      ],
      "juegos_de_lentes": [{"id": "an2", "nombre": "Anamórfico", "squeeze": 2.0, "focales_mm": [35, 50]}],
      "perfiles_telefono": [{"modelo_build": "SM-X", "nombre_comercial": "Tel",
        "lentes": [{"camera2_id": "5", "fov_horizontal_medido_grados": 71.8, "fov_horizontal_reportado_grados": 74.0},
                   {"camera2_id": "6", "fov_horizontal_medido_grados": null}]}]
    }
    """.trimIndent()

    @Test
    fun keepsOnlyCompleteModesAndCountsTheRest() {
        val cam = CameraDbParser.parse(json).cameras.first()
        assertEquals(1, cam.modes.size)
        assertEquals(2, cam.priority)
        assertEquals(1, cam.incompleteModes)
        assertEquals(1.5, cam.modes.first().aspect, 1e-9)
        assertEquals("https://example.com", cam.modes.first().source?.url)
        assertEquals(true, cam.modes.first().derived)
    }

    @Test
    fun skipsBuiltInLensesWithoutFocal() {
        val cam = CameraDbParser.parse(json).cameras[1]
        assertEquals(listOf("a"), cam.builtInLenses.map { it.id })
        assertNull(cam.mount)
        assertNull(cam.priority)
    }

    @Test
    fun readsLensSetsAndPhoneProfiles() {
        val db = CameraDbParser.parse(json)
        assertEquals(2.0, db.lensSets.first().squeeze, 1e-9)
        assertEquals(listOf(35.0, 50.0), db.lensSets.first().focalsMm)
        val lenses = db.phoneProfiles.first().lenses
        assertEquals(71.8, lenses[0].measuredHFovDeg!!, 1e-9)
        assertNull(lenses[1].measuredHFovDeg)
        assertNotNull(db.phoneProfiles.first())
    }
}

class BundledCamerasTest {
    @Test
    fun bundledJsonParsesAndHasValidatedCameras() {
        val text = java.io.File("src/main/assets/camaras.json").readText()
        val db = CameraDbParser.parse(text)
        org.junit.Assert.assertTrue(db.cameras.size >= 7)
        // Todo modo debe traer fuente y medidas plausibles (sensores de 5 a 60 mm de ancho).
        db.cameras.flatMap { it.modes }.forEach {
            org.junit.Assert.assertNotNull("${it.id} sin fuente", it.source)
            if (it.hasArea) {
                org.junit.Assert.assertTrue("${it.id} ancho ${it.widthMm}", it.widthMm!! in 5.0..60.0)
                org.junit.Assert.assertTrue("${it.id} alto ${it.heightMm}", it.heightMm!! in 3.0..40.0)
            } else org.junit.Assert.assertTrue("${it.id} sin área ni aspecto", it.aspectRatio != null)
        }
        // Las tres principales del usuario están marcadas y en este orden.
        assertEquals(listOf("canon_eos_r5", "insta360_luna_ultra", "sony_fx3"), db.cameras.filter { it.priority != null }.sortedBy { it.priority }.map { it.id })
        // El perfil del S26 Ultra trae la medición de la lente 5.
        val s26 = db.phoneProfiles.first { it.buildModel == "SM-S948B" }
        assertEquals(71.8, s26.lenses.first { it.camera2Id == "5" }.measuredHFovDeg!!, 1e-9)
    }

    /** Cámaras de foto agregadas el 2026-11-13: cada una con su modo nativo publicado y todos los modos con URL de la fuente. */
    @Test
    fun photoCamerasCarryTheirPublishedSensor() {
        val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())
        val native = mapOf(
            "sony_a7r_v" to (35.7 to 23.8), "sony_a6700" to (23.3 to 15.5),
            "canon_eos_r6_mark_ii" to (36.0 to 24.0), "canon_eos_r7" to (22.3 to 14.8),
            "nikon_z8" to (35.9 to 23.9), "nikon_z50ii" to (23.5 to 15.7),
            "fujifilm_x_t5" to (23.5 to 15.7), "fujifilm_gfx100s_ii" to (43.8 to 32.9),
            "om_system_om1_ii" to (17.3 to 13.0), "panasonic_lumix_g9_ii" to (17.3 to 13.0), "panasonic_lumix_s5_ii" to (35.6 to 23.8),
            "canon_eos_r5_mark_ii" to (36.0 to 24.0),
        )
        native.forEach { (id, wh) ->
            val cam = db.cameras.first { it.id == id }
            assertEquals("$id modos incompletos", 0, cam.incompleteModes)
            val published = cam.modes.filter { !it.derived }
            assertEquals("$id debe tener un solo modo publicado", 1, published.size)
            assertEquals(wh.first, published.first().widthMm!!, 1e-9)
            assertEquals(wh.second, published.first().heightMm!!, 1e-9)
            cam.modes.forEach { org.junit.Assert.assertNotNull("$id ${it.id} sin URL", it.source?.url) }
            // Ningún modo derivado puede ser mayor que el sensor publicado.
            cam.modes.forEach { org.junit.Assert.assertTrue("$id ${it.id}", it.widthMm!! <= wh.first + 0.05 && it.heightMm!! <= wh.second + 0.05) }
        }
        // Los dos zooms de ejemplo del kit.
        assertEquals(listOf(24.0, 28.0, 35.0, 50.0, 70.0), db.lensSets.first { it.id == "zoom_24_70_ejemplo" }.focalsMm)
        assertEquals(listOf(70.0, 85.0, 100.0, 135.0, 200.0), db.lensSets.first { it.id == "zoom_70_200_ejemplo" }.focalsMm)
        // Lentes Canon RF L: cada uno con su ficha oficial y sus focales dentro del rango del zoom.
        mapOf("canon_rf_15_35_f2_8l" to (15.0 to 35.0), "canon_rf_24_105_f4l" to (24.0 to 105.0), "canon_rf_70_200_f2_8l" to (70.0 to 200.0), "canon_rf_50_f1_2l" to (50.0 to 50.0)).forEach { (id, range) ->
            val set = db.lensSets.first { it.id == id }
            org.junit.Assert.assertNotNull("$id sin URL", set.source?.url)
            assertEquals(range.first, set.focalsMm.first(), 1e-9)
            assertEquals(range.second, set.focalsMm.last(), 1e-9)
        }
        // La R5 Mark II trae video: 8K DCI usa todo el ancho del sensor y el recorte de 1.6x queda en 22.5 mm.
        val r5ii = db.cameras.first { it.id == "canon_eos_r5_mark_ii" }
        assertEquals(36.0, r5ii.modes.first { it.id == "video_8k_dci" }.widthMm!!, 1e-9)
        assertEquals(22.5, r5ii.modes.first { it.id == "video_4k_dci_crop" }.widthMm!!, 1e-9)
        assertEquals(listOf("c_log2", "c_log3", "hdr_pq", "hlg"), r5ii.colorProfiles.map { it.id })
    }
}

class PhotoSaverTest {
    @Test
    fun asciiJsonEscapesNonAsciiAndStaysValid() {
        val meta = org.json.JSONObject().put("label", "36×24 mm · 50 mm")
        val out = PhotoSaver.asciiJson(meta)
        org.junit.Assert.assertTrue(out.all { it.code < 128 })
        assertEquals("36×24 mm · 50 mm", org.json.JSONObject(out).getString("label"))
    }
}

class GpsRationalTest {
    @Test
    fun santiagoLatitudeAsRational() {
        // 33.43720° = 33° 26' 14.0"
        assertEquals("33/1,26/1,13920/1000", PhotoSaver.gpsRational(-33.4372))
    }
}


class FirmwareModesTest {
    private val db = CameraDbParser.parse(java.io.File("src/main/assets/camaras.json").readText())
    private fun cam(id: String) = db.cameras.first { it.id == id }

    @Test
    fun versionsCompareBySegments() {
        org.junit.Assert.assertTrue(FirmwareVersion.compare("7.02", "7.00") > 0)
        org.junit.Assert.assertTrue(FirmwareVersion.compare("1.1.22", "1.1.8") > 0)
        org.junit.Assert.assertTrue(FirmwareVersion.compare("2.04", "7.00") < 0)
        assertEquals(0, FirmwareVersion.compare("3.0", "3.00"))
    }

    @Test
    fun fx3DciFourKAppearsOnlyFromItsFirmware() {
        val fx3 = cam("sony_fx3")
        assertEquals("7.02", fx3.latestFirmware)
        assertEquals(true, fx3.modesFor(null).any { it.id == "video_dci_4k" })
        assertEquals(true, fx3.modesFor("7.00").any { it.id == "video_dci_4k" })
        assertEquals(false, fx3.modesFor("1.00").any { it.id == "video_dci_4k" })
        // El modo original sigue en el firmware de lanzamiento.
        assertEquals(true, fx3.modesFor("1.00").any { it.id == "video_4k_ff" })
        assertEquals("S-Log3", fx3.colorProfiles.first { it.kind == "log" }.name)
    }

    @Test
    fun lunaUltraHasVerticalFourKAndILog() {
        val luna = cam("insta360_luna_ultra")
        val v = luna.modes.first { it.id == "video_4k_9_16" }
        assertEquals(false, v.hasArea)
        assertEquals(9.0 / 16.0, v.aspect, 1e-9)
        assertEquals("2160x3840", v.resolution)
        assertEquals("I-Log 10 bit", luna.colorProfiles.first { it.kind == "log" }.name)
        luna.modes.forEach { org.junit.Assert.assertNotNull("${it.id} sin fuente", it.source) }
    }

    @Test
    fun canonR5HasLogByFirmwareAndExtraPhotoAspects() {
        val r5 = cam("canon_eos_r5")
        assertEquals("2.2.1", r5.latestFirmware)
        assertEquals(listOf("Canon Log", "Canon Log 3", "HDR PQ"), r5.colorProfilesFor(null).map { it.name })
        // Canon Log 3 llegó con el firmware 1.30: no existe en el 1.2.0.
        assertEquals(listOf("Canon Log", "HDR PQ"), r5.colorProfilesFor("1.2.0").map { it.name })
        val four3 = r5.modes.first { it.id == "foto_4_3" }
        assertEquals(4.0 / 3.0, four3.aspect, 0.01)
        assertEquals(31.99, four3.widthMm!!, 1e-9)
    }

    @Test
    fun modesCarryFrameRatesWhenKnown() {
        assertEquals(listOf("30", "25", "24"), cam("insta360_luna_ultra").modes.first { it.id == "video_4k_9_16" }.fps)
        assertEquals(true, cam("canon_eos_r5").modes.first { it.id == "video_4k_dci" }.fps.contains("119.88"))
        // Con Movie cropping no hay alta velocidad (manual de Canon).
        assertEquals(false, cam("canon_eos_r5").modes.first { it.id == "video_4k_dci_crop" }.fps.contains("119.88"))
        assertEquals(true, cam("sony_fx3").modes.first { it.id == "foto_ff_3_2_L" }.fps.isEmpty())
    }

    @Test
    fun fx3ListsItsPictureProfilesFromSonysSheet() {
        val pp = cam("sony_fx3").pictureProfiles
        assertEquals(true, pp.containsAll(listOf("PP1", "PP6", "PP10", "PP11")))
        assertEquals(false, pp.contains("PP7"))      // la ficha de Sony lista PP1-PP6 y PP10-PP11
        assertEquals(true, cam("canon_eos_r5").pictureProfiles.isEmpty())
    }
}
