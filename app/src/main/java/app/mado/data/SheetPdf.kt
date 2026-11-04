package app.mado.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/**
 * Hoja de locaciones en PDF (A4, fondo blanco para imprimir o mandar): portada con la locación y sus notas, y los planos
 * agrupados por escena. El diseño lo resuelve la app con los datos que ya tiene; se elige plantilla y color de acento.
 * Usa la tipografía del sistema: el japonés sale con la fuente de respaldo del teléfono.
 */
object SheetPdf {
    enum class Template { LIST, LARGE }

    /** Colores de acento ofrecidos (naranja de Mado, azul, verde, grafito): oscuros, para que el texto blanco se lea. */
    val ACCENTS = listOf(0xFFFF5A1F.toInt(), 0xFF2F6FDE.toInt(), 0xFF2E8B57.toInt(), 0xFF333333.toInt())

    data class Labels(
        val shot: String, val address: String, val notes: String, val generated: String, val summary: (shots: Int, scenes: Int) -> String,
        val cameras: String, val page: String, val scene: String, val noPhoto: String, val brand: String = "Mado · Director's Viewfinder",
    )

    private const val W = 595; private const val H = 842; private const val M = 36f
    private const val CW = W - 2 * M
    private const val FOOT = 30f
    private val INK = 0xFF111111.toInt(); private val SOFT = 0xFF666666.toInt(); private val RULE = 0xFFDDDDDD.toInt()

    fun write(context: Context, sheet: LocationSheet, template: Template, accent: Int, labels: Labels, out: OutputStream) {
        // Las fotos van como JPEG de buena calidad en vez de imágenes sin pérdida (más nítidas y mucho más livianas).
        val doc = PdfDocument()
        val swapped = try {
            val writer = Writer(context, doc, sheet, template, accent, labels, jpegMode = true)
            writer.run()
            PdfJpeg.swap(java.io.ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray(), writer.jpegs)
        } finally { doc.close() }
        if (swapped != null) { out.write(swapped); return }
        // Si el archivo que escribe el teléfono no tiene la forma esperada, se rehace con imágenes sin pérdida, más chicas y pesadas.
        val plain = PdfDocument()
        try {
            Writer(context, plain, sheet, template, accent, labels, jpegMode = false).run()
            plain.writeTo(out)
        } finally { plain.close() }
    }

    private class Writer(val ctx: Context, val doc: PdfDocument, val sheet: LocationSheet, val template: Template, val accent: Int, val labels: Labels, val jpegMode: Boolean) {
        /** Fotos ya codificadas en JPEG, por el ancho de su imagen de marcador (ver [PdfJpeg]). */
        val jpegs = HashMap<Int, PdfJpeg.Jpeg>()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas = Canvas()
        var y = 0f
        val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; typeface = Typeface.DEFAULT }
        fun paint(size: Float, color: Int = INK, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun layout(text: String, p: TextPaint, width: Float): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(10)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.08f).build()

        /** Dibuja texto con salto de línea en (x, y) y devuelve su alto. */
        fun text(text: String, p: TextPaint, x: Float, top: Float, width: Float): Float {
            if (text.isEmpty()) return 0f
            val l = layout(text, p, width)
            c.save(); c.translate(x, top); l.draw(c); c.restore()
            return l.height.toFloat()
        }

        fun height(text: String, p: TextPaint, width: Float) = if (text.isEmpty()) 0f else layout(text, p, width).height.toFloat()

        fun startPage(withHeader: Boolean) {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            y = M
            if (withHeader) {
                text("${sheet.projectName} · ${sheet.place.name}", paint(9f, SOFT), M, y, CW - 80f)
                c.drawRect(M, y + 16f, W - M, y + 17.2f, Paint().apply { color = accent })
                y += 30f
            }
            // Pie: marca y número de página.
            val f = paint(8.5f, SOFT)
            c.drawText(labels.brand, M, H - 20f, f)
            val pg = "${labels.page} $pageNo"
            c.drawText(pg, W - M - f.measureText(pg), H - 20f, f)
        }

        fun finishPage() { page?.let { doc.finishPage(it) }; page = null }

        fun ensure(h: Float) { if (y + h > H - M - FOOT) startPage(true) }

        /** Foto lista para el PDF: su tamaño real en píxeles y una imagen diminuta de marcador que se dibuja en su lugar. */
        class Pic(val w: Int, val h: Int, val placeholder: Bitmap)

        fun pic(uri: String?, widthPx: Int): Pic? {
            if (!jpegMode) return bitmap(uri, if (widthPx > 1500) 900 else 560)?.let { Pic(it.width, it.height, it) }
            val bm = bitmap(uri, widthPx) ?: return null
            val bytes = java.io.ByteArrayOutputStream(bm.width * bm.height / 6).also { bm.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
            val w = bm.width; val h = bm.height
            bm.recycle()
            val key = 9000 + jpegs.size
            jpegs[key] = PdfJpeg.Jpeg(bytes)
            return Pic(w, h, Bitmap.createBitmap(key, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFFFFFF.toInt()); setHasAlpha(false) })
        }

        fun bitmap(uri: String?, widthPx: Int): Bitmap? {
            if (uri == null) return null
            return try {
                val u = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
                val raw = ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
                if (raw.width <= widthPx) raw else Bitmap.createScaledBitmap(raw, widthPx, (raw.height * widthPx.toFloat() / raw.width).toInt(), true).also { raw.recycle() }
            } catch (_: Exception) { null }
        }

        fun photo(bm: Pic?, x: Float, top: Float, width: Float, maxH: Float, center: Boolean = false): Float {
            if (bm == null) {
                c.drawRect(RectF(x, top, x + width, top + maxH * 0.6f), Paint().apply { color = 0xFFF2F2F2.toInt() })
                text(labels.noPhoto, paint(10f, SOFT), x + 8f, top + 8f, width - 16f)
                return maxH * 0.6f
            }
            var w = width; var h = bm.h * w / bm.w
            if (h > maxH) { h = maxH; w = bm.w * h / bm.h }
            val left = if (center) x + (width - w) / 2f else x
            c.drawBitmap(bm.placeholder, Rect(0, 0, bm.placeholder.width, bm.placeholder.height), RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawRect(RectF(left, top, left + w, top + h), Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = RULE })
            return h
        }

        /** Texto de un plano (número, hora, cámara, operador, notas, rumbo); devuelve el alto usado. */
        fun shotText(s: SheetShot, x: Float, top: Float, width: Float, draw: Boolean): Float {
            var t = top
            fun line(txt: String, p: TextPaint, gap: Float = 4f) { if (txt.isEmpty()) return; t += if (draw) text(txt, p, x, t, width) + gap else height(txt, p, width) + gap }
            val focal = s.focalMm?.let { (if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) + if (s.focalEquivalent) " mm eq" else " mm" }
            line(listOfNotNull("${labels.shot} ${s.shotNo}", focal).joinToString(" · "), paint(13f, INK, true), 5f)
            line(LocationSheets.localTime(s.time) + (s.headingDeg?.let { "  ·  %.0f° %s".format(java.util.Locale.ROOT, Math.floorMod(Math.round(it).toInt(), 360).toDouble(), COMPASS[app.mado.optics.OrientationMath.compassIndex(it)]) } ?: ""), paint(9.5f, SOFT), 5f)
            line(listOf(s.camera, s.lensSet).filter { it.isNotEmpty() }.joinToString(" · "), paint(10.5f))
            line(s.operator, paint(11f, INK, true))
            line(s.notes, paint(10.5f, accent))
            return t - top
        }

        fun sceneBar(name: String, count: Int?) {
            ensure(34f)
            c.drawRoundRect(RectF(M, y, W - M, y + 24f), 4f, 4f, Paint().apply { color = accent })
            val p = paint(12.5f, 0xFFFFFFFF.toInt(), true)
            c.drawText(name.ifEmpty { "—" }, M + 10f, y + 16.5f, p)
            if (count != null) { val q = paint(10f, 0xFFFFFFFF.toInt()); val s = "$count"; c.drawText(s, W - M - 10f - q.measureText(s), y + 16f, q) }
            y += 32f
        }

        fun cover() {
            startPage(false)
            c.drawRect(0f, 0f, W.toFloat(), 14f, Paint().apply { color = accent })
            y = 96f
            y += text(sheet.projectName, paint(30f, INK, true), M, y, CW) + 8f
            y += text(sheet.place.name, paint(20f, accent, true), M, y, CW) + 18f
            if (sheet.place.address.isNotBlank()) {
                y += text(labels.address, paint(9f, SOFT), M, y, CW) + 2f
                y += text(sheet.place.address, paint(13f), M, y, CW) + 14f
            }
            if (sheet.place.notes.isNotBlank()) {
                y += text(labels.notes, paint(9f, SOFT), M, y, CW) + 2f
                y += text(sheet.place.notes, paint(12f), M, y, CW) + 14f
            }
            c.drawRect(M, y, M + 60f, y + 2f, Paint().apply { color = accent }); y += 16f
            y += text(labels.summary(sheet.shotCount, sheet.scenes.size), paint(12f, INK, true), M, y, CW) + 4f
            if (sheet.cameras.isNotEmpty()) y += text("${labels.cameras}: ${sheet.cameras.joinToString(", ")}", paint(11f, SOFT), M, y, CW) + 4f
            y += text("${labels.generated}: ${java.time.LocalDate.now()}", paint(10f, SOFT), M, y, CW)
        }

        fun run() {
            cover()
            startPage(true)
            for (scene in sheet.scenes) {
                if (template == Template.LARGE) {
                    for (s in scene.shots) {
                        if (y > M + 40f) startPage(true)
                        sceneBar(scene.name, null)
                        val bm = pic(s.marcoUri ?: s.planoUri, 2000)
                        val h = photo(bm, M, y, CW, 430f, center = true)
                        y += h + 14f
                        y += shotText(s, M, y, CW, true)
                    }
                    continue
                }
                sceneBar(scene.name, scene.shots.size)
                for (s in scene.shots) {
                    val textH = shotText(s, 0f, 0f, CW - 224f, false)
                    val photoH = 150f
                    val rowH = maxOf(photoH, textH) + 14f
                    if (y + rowH > H - M - FOOT) { startPage(true); sceneBar(scene.name, null) }
                    val bm = pic(s.planoUri ?: s.marcoUri, 1000)
                    val h = photo(bm, M, y, 210f, photoH)
                    shotText(s, M + 224f, y, CW - 224f, true)
                    y += maxOf(h, textH) + 10f
                    c.drawRect(M, y, W - M, y + 0.6f, Paint().apply { color = RULE }); y += 8f
                }
                y += 6f
            }
            finishPage()
        }
    }

    private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
}
