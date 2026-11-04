package app.mado.data

import java.io.ByteArrayOutputStream

/**
 * El PdfDocument de Android guarda cada imagen sin pérdida (Flate): las fotos reales pesan varios MB y, para que el archivo sea
 * liviano, hay que bajarlas de tamaño y se ven borrosas al ampliar. Aquí se dibujan imágenes diminutas de marcador y, al terminar,
 * se reemplazan en el archivo por la foto en JPEG (DCTDecode): calidad alta con unos cientos de KB por foto.
 *
 * Es una reescritura mínima del PDF que escribe Android (objetos en texto, `/Length` directo y tabla xref clásica). Si el archivo no
 * tiene esa forma, [swap] devuelve null y quien llama rehace el PDF con imágenes sin pérdida.
 */
object PdfJpeg {
    class Jpeg(val bytes: ByteArray)

    private val OBJ = Regex("""(\d+) 0 obj\n""")

    /** Devuelve el PDF con las imágenes cuyo ancho esté en [jpegs] reemplazadas por su JPEG, o null si no se pudo leer el archivo. */
    fun swap(src: ByteArray, jpegs: Map<Int, Jpeg>): ByteArray? {
        return try {
            val text = String(src, Charsets.ISO_8859_1)             // 1 byte = 1 carácter: los índices coinciden con los de [src]
            val first = OBJ.find(text) ?: return null
            val out = ByteArrayOutputStream(src.size)
            out.write(src, 0, first.range.first)
            val offsetOf = java.util.TreeMap<Int, Int>()             // número de objeto → posición (Android no los escribe en orden)
            var pos = first.range.first
            while (true) {
                val m = OBJ.find(text, pos)?.takeIf { it.range.first == pos } ?: break
                val number = m.groupValues[1].toInt()
                if (offsetOf.containsKey(number)) return null
                val bodyStart = m.range.last + 1
                val streamMark = ">> stream\n"
                val window = text.substring(bodyStart, minOf(text.length, bodyStart + 6000))
                val endObjInWindow = window.indexOf("endobj\n")
                val sp = window.indexOf(streamMark)
                val objEnd: Int
                var replacement: ByteArray? = null
                if (sp >= 0 && (endObjInWindow < 0 || sp < endObjInWindow)) {
                    val dict = window.substring(0, sp + 2)
                    val len = Regex("""/Length (\d+)""").find(dict)?.groupValues?.get(1)?.toInt() ?: return null
                    val dataStart = bodyStart + sp + streamMark.length
                    val tail = "\nendstream\nendobj\n"
                    if (!text.startsWith(tail, dataStart + len)) return null
                    objEnd = dataStart + len + tail.length
                    if ("/Subtype /Image" in dict) {
                        val w = Regex("""/Width (\d+)""").find(dict)?.groupValues?.get(1)?.toInt()
                        jpegs[w]?.let { jp -> replacement = imageObject(m.groupValues[1], jp.bytes, jpegSize(jp.bytes) ?: return null) }
                    }
                } else {
                    if (endObjInWindow < 0) return null
                    objEnd = bodyStart + endObjInWindow + "endobj\n".length
                }
                offsetOf[number] = out.size()
                if (replacement != null) out.write(replacement!!) else out.write(src, pos, objEnd - pos)
                pos = objEnd
            }
            if (offsetOf.isEmpty() || offsetOf.firstKey() != 1 || offsetOf.lastKey() != offsetOf.size) return null      // deben estar todos, del 1 al último
            val offsets = offsetOf.values.toList()
            val trailerAt = text.indexOf("xref\n", pos).takeIf { it >= 0 } ?: return null
            val trailer = text.substring(text.indexOf("trailer", trailerAt))
            val root = Regex("""/Root (\d+ 0 R)""").find(trailer)?.groupValues?.get(1) ?: return null
            val info = Regex("""/Info (\d+ 0 R)""").find(trailer)?.groupValues?.get(1)
            val xrefOffset = out.size()
            val sb = StringBuilder("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { sb.append("%010d 00000 n \n".format(java.util.Locale.ROOT, it)) }
            sb.append("trailer\n<</Size ${offsets.size + 1}\n/Root $root\n")
            if (info != null) sb.append("/Info $info\n")
            sb.append(">>\nstartxref\n$xrefOffset\n%%EOF\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            out.toByteArray()
        } catch (_: Exception) { null }
    }

    private fun imageObject(num: String, jpeg: ByteArray, size: Pair<Int, Int>): ByteArray {
        val head = "$num 0 obj\n<</Type /XObject\n/Subtype /Image\n/Width ${size.first}\n/Height ${size.second}\n/ColorSpace /DeviceRGB\n" +
            "/BitsPerComponent 8\n/Filter /DCTDecode\n/Length ${jpeg.size}>> stream\n"
        val b = ByteArrayOutputStream(jpeg.size + 300)
        b.write(head.toByteArray(Charsets.ISO_8859_1)); b.write(jpeg); b.write("\nendstream\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        return b.toByteArray()
    }

    /** Ancho y alto de un JPEG leyendo su marcador SOF; null si no se encuentra. */
    internal fun jpegSize(d: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < d.size) {
            if (d[i] != 0xFF.toByte()) { i++; continue }
            val marker = d[i + 1].toInt() and 0xFF
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((d[i + 5].toInt() and 0xFF) shl 8) or (d[i + 6].toInt() and 0xFF)
                val w = ((d[i + 7].toInt() and 0xFF) shl 8) or (d[i + 8].toInt() and 0xFF)
                return w to h
            }
            val len = ((d[i + 2].toInt() and 0xFF) shl 8) or (d[i + 3].toInt() and 0xFF)
            i += 2 + len
        }
        return null
    }
}
