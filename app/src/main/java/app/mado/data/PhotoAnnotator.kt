package app.mado.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import app.mado.optics.Framing
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Datos de la barra inferior (estilo de visor de producción): izquierda cámara, lente del teléfono y fecha; centro brújula y posición;
 * derecha juego de lentes, ángulos H/V y la focal en grande. Los textos opcionales (`orientation`, `position`) se omiten si no hay dato.
 */
data class BarInfo(
    val camera: String, val mode: String, val phoneLine: String, val stamp: String,
    val heading: String?, val attitude: String?, val position: String?,
    val lensSet: String, val angles: String, val bigFocal: String,
)

/** Un recuadro por focal del kit; `current` = el que se está usando; `detail` = texto de su etiqueta (la actual lleva la medida). */
data class AnnotFrame(val label: String, val frame: Framing.Frame, val current: Boolean, val detail: String = label)

/**
 * Copias de la foto con los datos a la vista, siempre aparte de la foto limpia:
 *  - [overview]: la foto con los recuadros de todo el kit (la focal actual en naranja, con su medida) y una barra de datos.
 *  - [plano]: la foto recortada exactamente al recuadro actual (para storyboard o shotlist) y la misma barra de datos.
 * La barra es blanca con texto negro y lleva el logo; el texto se ajusta al ancho (se achica o se corta con «…»).
 */
object PhotoAnnotator {
    private const val ORANGE = 0xFFFF5A1F.toInt()
    private const val INK = 0xFF111111.toInt()
    private const val INK_SOFT = 0xFF555555.toInt()
    private const val PAPER = Color.WHITE
    private const val NEUTRAL = 0xFFB8B8B4.toInt()

    // ---------- Vista general: foto + todos los recuadros ----------
    fun overview(jpeg: ByteArray, rotationDeg: Int, frames: List<AnnotFrame>, info: BarInfo): ByteArray {
        val photo = decodeRotated(jpeg, rotationDeg, sample = 2)
        val w = photo.width; val h = photo.height
        val unit = w / 38f                                   // marcos y etiquetas
        val barUnit = w / 52f                                // barra de datos: letra más chica, como un pie de foto
        val bar = barHeight(barUnit)
        val out = Bitmap.createBitmap(w, (h + bar).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)

        val landscape = w >= h                               // los recuadros se calculan con el lado largo en horizontal
        val widest = frames.maxOfOrNull { maxOf(it.frame.widthFrac, it.frame.heightFrac) } ?: 1.0
        val scale = (1.0 / maxOf(1.0, widest)).toFloat()
        val iw = w * scale; val ih = h * scale
        val ix = (w - iw) / 2f; val iy = (h - ih) / 2f
        if (scale < 0.999f) c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = NEUTRAL })
        c.drawBitmap(photo, null, RectF(ix, iy, ix + iw, iy + ih), Paint(Paint.FILTER_BITMAP_FLAG))

        // Un recuadro por focal, sin oscurecer lo de afuera; la actual en naranja con su medida.
        val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = unit * 0.8f }
        frames.sortedBy { it.current }.forEach { af ->
            val fr = af.frame
            val fw = (if (landscape) fr.widthFrac else fr.heightFrac).toFloat() * iw
            val fh = (if (landscape) fr.heightFrac else fr.widthFrac).toFloat() * ih
            val l = w / 2f - fw / 2f; val t = h / 2f - fh / 2f
            val line = Paint().apply {
                color = if (af.current) ORANGE else Color.argb(230, 255, 255, 255)
                style = Paint.Style.STROKE; strokeWidth = if (af.current) unit / 5f else unit / 11f
            }
            c.drawRect(l, t, l + fw, t + fh, line)
            val text = if (af.current) af.detail else af.label
            val tw = tagText.measureText(text) + unit * 0.7f
            val th = unit * 1.1f
            c.drawRect(l, t, l + tw, t + th, Paint().apply { color = if (af.current) ORANGE else Color.argb(170, 0, 0, 0) })
            tagText.color = if (af.current) Color.BLACK else Color.WHITE
            c.drawText(text, l + unit * 0.35f, t + th * 0.78f, tagText)
        }
        drawInfoBar(c, w.toFloat(), h.toFloat(), bar, barUnit, info)
        photo.recycle()
        return encode(out)
    }

    // ---------- Plano: la foto recortada al encuadre actual ----------
    fun plano(jpeg: ByteArray, rotationDeg: Int, frames: List<AnnotFrame>, info: BarInfo): ByteArray {
        val cur = frames.firstOrNull { it.current }?.frame ?: return overview(jpeg, rotationDeg, frames, info)
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, it) }
        val rotated90 = rotationDeg % 180 != 0
        val W = if (rotated90) size.outHeight else size.outWidth      // medidas de la foto ya girada
        val H = if (rotated90) size.outWidth else size.outHeight
        val landscape = W >= H
        val fwFrac = (if (landscape) cur.widthFrac else cur.heightFrac).toFloat()
        val fhFrac = (if (landscape) cur.heightFrac else cur.widthFrac).toFloat()

        val body: Bitmap
        if (fwFrac <= 1f && fhFrac <= 1f) {
            // Recorte real a resolución completa (si la foto no es enorme): se recorta antes de girar para gastar menos memoria.
            val sample = if (size.outWidth.toLong() * size.outHeight > 13_500_000L) 2 else 1
            val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("no se pudo leer la foto")
            val Ws = W / sample; val Hs = H / sample
            val cw = (Ws * fwFrac).roundToInt().coerceIn(1, Ws); val ch = (Hs * fhFrac).roundToInt().coerceIn(1, Hs)
            val cx = (Ws - cw) / 2; val cy = (Hs - ch) / 2          // en coordenadas de la foto ya girada
            val r = when (((rotationDeg % 360) + 360) % 360) {      // mismo rectángulo en coordenadas de la foto sin girar
                90 -> Rect(cy, src.height - (cx + cw), cy + ch, src.height - cx)
                270 -> Rect(src.width - (cy + ch), cx, src.width - cy, cx + cw)
                180 -> Rect(src.width - (cx + cw), src.height - (cy + ch), src.width - cx, src.height - cy)
                else -> Rect(cx, cy, cx + cw, cy + ch)
            }
            val piece = Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
            if (piece !== src) src.recycle()
            body = if (rotationDeg % 360 == 0) piece else Bitmap.createBitmap(piece, 0, 0, piece.width, piece.height, Matrix().apply { postRotate(rotationDeg.toFloat()) }, true).also { piece.recycle() }
        } else {
            // El encuadre es más ancho que la foto (padding): lienzo del tamaño del recuadro, foto al centro.
            val photo = decodeRotated(jpeg, rotationDeg, sample = 2)
            val cw = (photo.width * fwFrac).roundToInt(); val ch = (photo.height * fhFrac).roundToInt()
            body = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val cv = Canvas(body); cv.drawColor(NEUTRAL)
            cv.drawBitmap(photo, (cw - photo.width) / 2f, (ch - photo.height) / 2f, null)
            photo.recycle()
        }

        val w = body.width; val h = body.height
        val unit = w / 52f
        val bar = barHeight(unit)
        val out = Bitmap.createBitmap(w, (h + bar).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)
        c.drawBitmap(body, 0f, 0f, null)
        drawInfoBar(c, w.toFloat(), h.toFloat(), bar, unit, info)
        body.recycle()
        return encode(out)
    }

    // ---------- Utilidades ----------
    private fun decodeRotated(jpeg: ByteArray, rotationDeg: Int, sample: Int): Bitmap {
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("no se pudo leer la foto")
        return if (rotationDeg % 360 == 0) src else Bitmap.createBitmap(
            src, 0, 0, src.width, src.height, Matrix().apply { postRotate(rotationDeg.toFloat()) }, true,
        ).also { src.recycle() }
    }

    private fun encode(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream(1 shl 20)
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    private fun barHeight(unit: Float) = unit * 5.9f

    /** Reduce el texto hasta que quepa; si aun así no cabe, lo corta con «…». */
    private fun fit(p: Paint, text: String, maxW: Float, minSize: Float): String {
        while (p.measureText(text) > maxW && p.textSize > minSize) p.textSize *= 0.95f
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Barra blanca con texto negro: logo, cámara y lente a la izquierda, brújula y posición al centro, lentes y focal grande a la derecha. */
    private fun drawInfoBar(c: Canvas, w: Float, top: Float, barH: Float, unit: Float, info: BarInfo) {
        c.drawRect(0f, top, w, top + barH, Paint().apply { color = PAPER })
        c.drawRect(0f, top, w, top + unit / 14f, Paint().apply { color = 0xFFDDDDD8.toInt() })
        val margin = unit * 0.9f
        val logoR = minOf(barH * 0.30f, unit * 1.9f)
        drawLogo(c, margin + logoR, top + barH / 2f, logoR)
        val x0 = margin + 2 * logoR + unit * 0.9f
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val y1 = top + unit * 1.85f; val y2 = y1 + unit * 1.5f; val y3 = y2 + unit * 1.5f

        // Derecha: focal en grande, divisor y, a su izquierda, juego de lentes y ángulos H/V.
        p.typeface = bold; p.textSize = barH * 0.44f; p.color = INK
        val big = fit(p, info.bigFocal, w * 0.22f, barH * 0.2f)
        val bigW = p.measureText(big)
        c.drawText(big, w - margin - bigW, top + barH * 0.66f, p)
        val divX = w - margin - bigW - unit * 0.9f
        c.drawRect(divX, top + barH * 0.18f, divX + unit / 14f, top + barH * 0.82f, Paint().apply { color = 0xFFCFCFCA.toInt() })
        p.typeface = mono; p.textSize = unit * 0.85f; p.color = INK_SOFT
        val setTxt = fit(p, info.lensSet, w * 0.2f, unit * 0.5f)
        p.typeface = bold; p.textSize = unit * 0.95f; p.color = INK
        val angTxt = fit(p, info.angles, w * 0.2f, unit * 0.5f)
        p.typeface = mono; p.textSize = unit * 0.85f
        val stampTxt = fit(p, info.stamp, w * 0.2f, unit * 0.5f)
        val monoPaint = Paint().apply { typeface = mono; textSize = unit * 0.85f }
        p.typeface = bold; p.textSize = unit * 0.95f
        val blockW = maxOf(monoPaint.measureText(setTxt), monoPaint.measureText(stampTxt), p.measureText(angTxt))
        val blockX = divX - unit * 0.9f - blockW
        p.typeface = mono; p.textSize = unit * 0.85f; p.color = INK_SOFT
        c.drawText(setTxt, blockX, y1, p)
        p.typeface = bold; p.textSize = unit * 0.95f; p.color = INK
        c.drawText(angTxt, blockX, y2, p)
        p.typeface = mono; p.textSize = unit * 0.85f; p.color = ORANGE
        c.drawText(stampTxt, blockX, y3, p)

        // Izquierda y centro.
        val avail = blockX - unit * 1.0f - x0
        val leftW = avail * 0.50f
        val cx = x0 + leftW + unit * 1.8f
        val centerW = avail - leftW - unit * 1.8f
        // El nombre de la cámara es el dato principal: grande y en negrita; debajo el modo de grabación y la lente del teléfono.
        p.typeface = bold; p.textSize = unit * 1.9f; p.color = INK
        c.drawText(fit(p, info.camera, leftW, unit * 0.9f), x0, y1 + unit * 0.2f, p)
        p.typeface = mono; p.textSize = unit * 0.9f; p.color = INK
        c.drawText(fit(p, info.mode, leftW, unit * 0.5f), x0, y2 + unit * 0.1f, p)
        p.typeface = mono; p.textSize = unit * 0.8f; p.color = INK_SOFT
        c.drawText(fit(p, info.phoneLine, leftW, unit * 0.5f), x0, y3, p)
        info.heading?.let {
            p.typeface = bold; p.textSize = unit * 1.0f; p.color = INK
            c.drawText(fit(p, it, centerW, unit * 0.5f), cx, y1, p)
        }
        info.attitude?.let {
            p.typeface = mono; p.textSize = unit * 0.85f; p.color = INK_SOFT
            c.drawText(fit(p, it, centerW, unit * 0.5f), cx, y2, p)
        }
        info.position?.let {
            p.typeface = mono; p.textSize = unit * 0.85f; p.color = INK_SOFT
            c.drawText(fit(p, it, centerW, unit * 0.5f), cx, y3, p)
        }
    }

    /** Logo de Mado: anillo de cinco rayas con un recuadro naranja 3:2 (versión para fondo claro). */
    private fun drawLogo(c: Canvas, cx: Float, cy: Float, r: Float) {
        val ringR = r * 0.92f
        val stroke = ringR * 5f / 33f
        val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        val pnt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = INK }
        val span = 72f - 11.5f - Math.toDegrees((stroke / ringR).toDouble()).toFloat()
        for (k in 0 until 5) {
            val center = -90f + k * 72f
            c.drawArc(oval, center - span / 2f, span, false, pnt)
        }
        val rw = ringR * 30f / 33f; val rh = ringR * 20f / 33f
        c.drawRoundRect(RectF(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2), ringR * 3f / 33f, ringR * 3f / 33f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE })
    }
}
