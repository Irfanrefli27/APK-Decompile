package dev.apkdecompiler

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/** Decoder AXML (AndroidManifest.xml biner) mandiri, tanpa bergantung pada jadx. */
object ManifestDecoder {
    private const val RES_XML = 0x0003
    private const val RES_STRING_POOL = 0x0001
    private const val RES_START_NS = 0x0100
    private const val RES_END_NS = 0x0101
    private const val RES_START_TAG = 0x0102
    private const val RES_END_TAG = 0x0103
    private const val RES_CDATA = 0x0104

    fun decode(apk: File): String {
        val bytes = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: throw IllegalStateException("AndroidManifest.xml tidak ada di APK")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        return parse(bytes)
    }

    private fun u16(b: ByteBuffer, i: Int) = b.getShort(i).toInt() and 0xFFFF

    private fun parse(data: ByteArray): String {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (data.size < 16 || u16(b, 0) != RES_XML) throw IllegalStateException("Bukan binary XML")

        val total = minOf(b.getInt(4), data.size)
        var pos = u16(b, 2)
        var strings: List<String> = emptyList()
        val nsPrefix = HashMap<String, String>() // uri -> prefix
        val pendingNs = ArrayList<Pair<String, String>>()
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        var depth = 0
        var open = false // start tag terakhir belum ditutup '>'
        val names = ArrayDeque<String>()

        while (pos + 8 <= total) {
            val type = u16(b, pos)
            val headerSize = u16(b, pos + 2)
            val size = b.getInt(pos + 4)
            if (size <= 0 || pos + size > data.size) break
            val ext = pos + headerSize

            when (type) {
                RES_STRING_POOL -> strings = readStringPool(b, pos)
                RES_START_NS -> {
                    val prefix = str(strings, b.getInt(ext))
                    val uri = str(strings, b.getInt(ext + 4))
                    nsPrefix[uri] = prefix
                    pendingNs += prefix to uri
                }
                RES_END_NS -> Unit
                RES_START_TAG -> {
                    if (open) { sb.append(">\n"); open = false }
                    val name = str(strings, b.getInt(ext + 4))
                    sb.append(indent(depth)).append('<').append(name)
                    for ((prefix, uri) in pendingNs) {
                        sb.append('\n').append(indent(depth + 1))
                            .append("xmlns:").append(prefix).append("=\"").append(esc(uri)).append('"')
                    }
                    pendingNs.clear()

                    val attrStart = u16(b, ext + 8)
                    val attrSize = u16(b, ext + 10)
                    val attrCount = u16(b, ext + 12)
                    val base = ext + attrStart
                    for (i in 0 until attrCount) {
                        val a = base + i * attrSize
                        val ns = b.getInt(a)
                        val an = str(strings, b.getInt(a + 4))
                        val raw = b.getInt(a + 8)
                        val dataType = b.get(a + 15).toInt() and 0xFF
                        val value = b.getInt(a + 16)
                        val prefix = if (ns >= 0) nsPrefix[str(strings, ns)] else null
                        val qn = if (!prefix.isNullOrEmpty()) "$prefix:$an" else an
                        val v = if (raw >= 0) str(strings, raw) else typed(strings, dataType, value)
                        sb.append('\n').append(indent(depth + 1)).append(qn).append("=\"").append(esc(v)).append('"')
                    }
                    open = true
                    names.addLast(name)
                    depth++
                }
                RES_END_TAG -> {
                    depth = maxOf(0, depth - 1)
                    val name = if (names.isNotEmpty()) names.removeLast() else ""
                    if (open) { sb.append(" />\n"); open = false }
                    else sb.append(indent(depth)).append("</").append(name).append(">\n")
                }
                RES_CDATA -> {
                    val text = str(strings, b.getInt(ext)).trim()
                    if (text.isNotEmpty()) {
                        if (open) { sb.append(">\n"); open = false }
                        sb.append(indent(depth)).append(esc(text)).append('\n')
                    }
                }
            }
            pos += size
        }
        return sb.toString()
    }

    private fun typed(strings: List<String>, type: Int, v: Int): String = when (type) {
        0x03 -> str(strings, v)
        0x12 -> if (v != 0) "true" else "false"
        0x10 -> v.toString()
        0x11 -> "0x" + Integer.toHexString(v)
        0x01 -> "@" + (if ((v ushr 24) == 0x01) "android:" else "") + "0x" + Integer.toHexString(v)
        0x02 -> "?0x" + Integer.toHexString(v)
        0x04 -> java.lang.Float.intBitsToFloat(v).toString()
        0x1c, 0x1d, 0x1e, 0x1f -> "#" + "%08x".format(v)
        else -> "0x" + Integer.toHexString(v)
    }

    private fun str(list: List<String>, i: Int) = if (i in list.indices) list[i] else ""
    private fun indent(n: Int) = "    ".repeat(n)
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun readStringPool(b: ByteBuffer, pos: Int): List<String> {
        val headerSize = u16(b, pos + 2)
        val count = b.getInt(pos + 8)
        val flags = b.getInt(pos + 16)
        val stringsStart = b.getInt(pos + 20)
        val utf8 = (flags and 0x100) != 0
        val out = ArrayList<String>(count)
        for (i in 0 until count) {
            val off = b.getInt(pos + headerSize + i * 4)
            var p = pos + stringsStart + off
            if (utf8) {
                var l = b.get(p).toInt() and 0xFF; p += 1          // panjang UTF-16 (dilewati)
                if ((l and 0x80) != 0) p += 1
                var n = b.get(p).toInt() and 0xFF; p += 1           // panjang byte UTF-8
                if ((n and 0x80) != 0) { n = ((n and 0x7F) shl 8) or (b.get(p).toInt() and 0xFF); p += 1 }
                val arr = ByteArray(n) { k -> b.get(p + k) }
                out += String(arr, Charsets.UTF_8)
            } else {
                var l = u16(b, p); p += 2
                if ((l and 0x8000) != 0) { l = ((l and 0x7FFF) shl 16) or u16(b, p); p += 2 }
                val chars = CharArray(l) { k -> b.getChar(p + k * 2) }
                out += String(chars)
            }
        }
        return out
    }
}
