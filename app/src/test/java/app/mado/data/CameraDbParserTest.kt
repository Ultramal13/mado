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
            org.junit.Assert.assertTrue("${it.id} ancho ${it.widthMm}", it.widthMm in 5.0..60.0)
            org.junit.Assert.assertTrue("${it.id} alto ${it.heightMm}", it.heightMm in 3.0..40.0)
        }
        // Las tres principales del usuario están marcadas y en este orden.
        assertEquals(listOf("canon_eos_r5", "insta360_luna_ultra", "sony_fx3"), db.cameras.filter { it.priority != null }.sortedBy { it.priority }.map { it.id })
        // El perfil del S26 Ultra trae la medición de la lente 5.
        val s26 = db.phoneProfiles.first { it.buildModel == "SM-S948B" }
        assertEquals(71.8, s26.lenses.first { it.camera2Id == "5" }.measuredHFovDeg!!, 1e-9)
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
