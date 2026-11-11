// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.integrity

import io.github.butchecassidy.pocketbox.data.scan.tags.BinaryReader
import java.util.Locale

/** Something found inside an audio file that isn't music, ordinary tags, album art or lyrics. */
enum class IntegrityIssue(val message: String) {
    EMBEDDED_FILE("Contains an embedded file"),
    PROGRAM_CODE("Contains program code or an archive"),
    SCRIPT_IN_TAGS("A text tag contains web or script code"),
    NOT_A_PICTURE("The album-art slot holds something that isn't a picture"),
    DATA_AFTER_PICTURE("Extra data is tucked in after the album-art image"),
    DATA_BEFORE_AUDIO("Unrecognised data sits between the tags and the music"),
    DATA_AFTER_AUDIO("The end of the file isn't music: data was added after it, or the file is incomplete"),
    HIDDEN_IN_PADDING("Data is hidden in space that should be empty"),
    APPLICATION_DATA("Contains another program's private data"),
    UNKNOWN_BLOCK("Contains an unrecognised block of data"),
    EXTRA_STREAM("Contains a stream that isn't music, such as video"),
    OVERSIZED("Contains an unusually large block that couldn't be fully checked"),
    DAMAGED("The file's structure is damaged, so it couldn't be fully checked"),
}

/** One finding: what it is and where in the file (technical, for the details view). */
data class IntegrityFinding(val issue: IntegrityIssue, val where: String) {
    /** Stored as one line per finding in the held-files table. */
    fun encode(): String = "${issue.name}|${where.replace('\n', ' ')}"

    companion object {
        fun decodeAll(stored: String): List<IntegrityFinding> = stored.lineSequence().mapNotNull { line ->
            val issue = IntegrityIssue.entries.firstOrNull { it.name == line.substringBefore('|') } ?: return@mapNotNull null
            IntegrityFinding(issue, line.substringAfter('|', ""))
        }.toList()

        fun encodeAll(findings: List<IntegrityFinding>): String = findings.joinToString("\n") { it.encode() }
    }
}

/** Collects findings, without duplicates and with a cap so a hostile file can't flood the list. */
internal class Report {
    private val found = LinkedHashSet<IntegrityFinding>()
    val findings: List<IntegrityFinding> get() = found.toList()

    fun add(issue: IntegrityIssue, where: String) {
        if (found.size < MAX_FINDINGS) found += IntegrityFinding(issue, where)
    }

    /** Looks for program code or archives inside a region that should only hold data or nothing. */
    fun scanForCode(bytes: ByteArray, where: String) {
        CodeSignatures.find(bytes)?.let { add(IntegrityIssue.PROGRAM_CODE, "$it in $where") }
    }

    /** The same, for a region of the file read in chunks (capped, so huge regions stay fast). */
    fun scanForCode(reader: BinaryReader, start: Long, end: Long, where: String) {
        var at = start
        val stop = minOf(end, start + MAX_SCAN_BYTES)
        while (at < stop) {
            val chunk = reader.peek(at, minOf(CHUNK.toLong(), stop - at + OVERLAP).toInt())
            if (chunk.isEmpty()) return
            CodeSignatures.find(chunk)?.let { return add(IntegrityIssue.PROGRAM_CODE, "$it in $where") }
            at += CHUNK
        }
    }

    /** True when every byte of the region is zero (the normal content of padding). */
    fun isAllZero(reader: BinaryReader, start: Long, end: Long): Boolean {
        var at = start
        while (at < end) {
            val chunk = reader.peek(at, minOf(CHUNK.toLong(), end - at).toInt())
            if (chunk.isEmpty()) return true
            if (chunk.any { it.toInt() != 0 }) return false
            at += chunk.size
        }
        return true
    }

    /** Empty padding is fine; anything else in it is reported (and checked for code). */
    fun checkPadding(reader: BinaryReader, start: Long, end: Long, where: String) {
        if (end - start > MAX_SCAN_BYTES) return add(IntegrityIssue.OVERSIZED, "$where (${sizeLabel(end - start)})")
        if (!isAllZero(reader, start, end)) {
            add(IntegrityIssue.HIDDEN_IN_PADDING, where)
            scanForCode(reader, start, end, where)
        }
    }

    /** Checks bytes found in an album-art slot: must be a known image, with nothing hidden after it. */
    fun checkPicture(data: ByteArray, where: String) {
        val end = ImageEnds.endOf(data)
        if (end == null) {
            add(IntegrityIssue.NOT_A_PICTURE, where)
            scanForCode(data, where)
            return
        }
        if (end < data.size && (end until data.size).any { data[it].toInt() != 0 }) {
            add(IntegrityIssue.DATA_AFTER_PICTURE, "$where (${sizeLabel((data.size - end).toLong())} after the image)")
            scanForCode(data.copyOfRange(end, data.size), "data after the image in $where")
        }
    }

    /** Text tags may be long (lyrics), but never code meant for a browser or a server. */
    fun checkText(key: String, value: String) {
        if (value.length > MAX_TEXT_CHARS) add(IntegrityIssue.OVERSIZED, "text tag $key (${value.length} characters)")
        if (scriptMarkers.any { value.contains(it, ignoreCase = true) }) add(IntegrityIssue.SCRIPT_IN_TAGS, "text tag $key")
    }

    companion object {
        private const val MAX_FINDINGS = 20
        private const val CHUNK = 1024 * 1024
        private const val OVERLAP = 64L
        private const val MAX_TEXT_CHARS = 1024 * 1024
        const val MAX_SCAN_BYTES = 64L * 1024 * 1024

        /** Size above which an unrecognised block is reported even if nothing inside it looks like code. */
        const val LARGE_BLOCK = 64L * 1024
        private val scriptMarkers = listOf("<script", "<?php", "javascript:", "<iframe")

        fun sizeLabel(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes bytes"
        }
    }
}

/** Recognises the start of programs and archives by their fixed "magic" bytes. */
internal object CodeSignatures {
    private class Signature(val bytes: ByteArray, val label: String)

    private fun sig(label: String, vararg values: Int) = Signature(ByteArray(values.size) { values[it].toByte() }, label)
    private fun ascii(label: String, text: String) = Signature(text.toByteArray(Charsets.ISO_8859_1), label)

    private val signatures = listOf(
        sig("a Linux or Android program", 0x7F, 'E'.code, 'L'.code, 'F'.code),
        ascii("Android app code", "dex\n0"),
        sig("a ZIP archive (could hold an app)", 'P'.code, 'K'.code, 3, 4),
        sig("a macOS program", 0xCF, 0xFA, 0xED, 0xFE),
        sig("a macOS program", 0xCE, 0xFA, 0xED, 0xFE),
        sig("a Java or macOS program", 0xCA, 0xFE, 0xBA, 0xBE),
        sig("a RAR archive", 'R'.code, 'a'.code, 'r'.code, '!'.code, 0x1A, 0x07),
        sig("a 7-Zip archive", '7'.code, 'z'.code, 0xBC, 0xAF, 0x27, 0x1C),
        ascii("a PDF document", "%PDF-"),
        ascii("web script code", "<script"),
        ascii("server script code", "<?php"),
    )

    /** Describes the first program or archive found in [data], or null if there is none. */
    fun find(data: ByteArray): String? {
        for (i in data.indices) {
            val b = data[i]
            if (b == 'M'.code.toByte() && isWindowsProgram(data, i)) return "a Windows program"
            for (s in signatures) {
                if (b == s.bytes[0] && matches(data, i, s.bytes)) return s.label
            }
        }
        return null
    }

    private fun matches(data: ByteArray, at: Int, magic: ByteArray): Boolean {
        if (at + magic.size > data.size) return false
        for (k in magic.indices) if (data[at + k] != magic[k]) return false
        return true
    }

    /** "MZ" alone is too common; a real Windows program also has "PE\0\0" where its header points. */
    private fun isWindowsProgram(data: ByteArray, at: Int): Boolean {
        if (at + 0x40 > data.size || data[at + 1] != 'Z'.code.toByte()) return false
        val peOffset = (data[at + 0x3C].toInt() and 0xFF) or ((data[at + 0x3D].toInt() and 0xFF) shl 8)
        val pe = at + peOffset
        return peOffset in 0x40..4096 && matches(data, pe, byteArrayOf('P'.code.toByte(), 'E'.code.toByte(), 0, 0))
    }
}

/** Finds where an image really ends, so anything appended after it can be spotted. */
internal object ImageEnds {
    /** End offset of the image in [data], or null if [data] isn't a JPEG, PNG, GIF, WebP or BMP image. */
    fun endOf(data: ByteArray): Int? = when {
        data.size >= 3 && u8(data, 0) == 0xFF && u8(data, 1) == 0xD8 && u8(data, 2) == 0xFF -> jpegEnd(data)
        data.size >= 8 && data.copyOfRange(0, 8).contentEquals(pngMagic) -> pngEnd(data)
        data.size >= 6 && (ascii(data, 0, "GIF87a") || ascii(data, 0, "GIF89a")) -> gifEnd(data)
        data.size >= 12 && ascii(data, 0, "RIFF") && ascii(data, 8, "WEBP") -> (8 + le32(data, 4)).toIntOrNull(data)
        data.size >= 6 && ascii(data, 0, "BM") -> le32(data, 2).toIntOrNull(data)
        else -> null
    }

    private val pngMagic = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

    /** Walks JPEG segments (skipping embedded thumbnails) to the real end-of-image marker. */
    private fun jpegEnd(data: ByteArray): Int? {
        var pos = 2
        while (pos + 1 < data.size) {
            if (u8(data, pos) != 0xFF) return null
            var marker = u8(data, pos + 1)
            while (marker == 0xFF && pos + 2 < data.size) marker = u8(data, ++pos + 1)
            when {
                marker == 0xD9 -> return pos + 2
                marker in 0xD0..0xD7 || marker == 0x01 -> pos += 2
                else -> {
                    if (pos + 3 >= data.size) return null
                    pos += 2 + ((u8(data, pos + 2) shl 8) or u8(data, pos + 3))
                    if (marker == 0xDA) pos = skipEntropyData(data, pos)
                }
            }
        }
        return null
    }

    /** Image data after a start-of-scan runs until the next marker that isn't a stuffed byte or restart. */
    private fun skipEntropyData(data: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < data.size) {
            if (u8(data, i) == 0xFF) {
                val next = u8(data, i + 1)
                if (next != 0x00 && next !in 0xD0..0xD7 && next != 0xFF) return i
            }
            i++
        }
        return data.size
    }

    private fun pngEnd(data: ByteArray): Int? {
        var pos = 8
        while (pos + 12 <= data.size) {
            val length = be32(data, pos)
            if (length < 0 || length > data.size) return null
            val end = pos + 12 + length.toInt()
            if (ascii(data, pos + 4, "IEND")) return end.takeIf { it <= data.size }
            pos = end
        }
        return null
    }

    /** GIFs end with a ';' (0x3B) trailer; the last one in the file, ignoring zero fill. */
    private fun gifEnd(data: ByteArray): Int? {
        var i = data.size - 1
        while (i > 0 && data[i].toInt() == 0) i--
        return if (u8(data, i) == 0x3B) i + 1 else null
    }

    private fun Long.toIntOrNull(data: ByteArray): Int? = takeIf { it in 1..data.size.toLong() }?.toInt()
    private fun u8(data: ByteArray, at: Int) = data[at].toInt() and 0xFF
    private fun le32(data: ByteArray, at: Int): Long = (0..3).fold(0L) { acc, k -> acc or (u8(data, at + k).toLong() shl (8 * k)) }
    private fun be32(data: ByteArray, at: Int): Long = (0..3).fold(0L) { acc, k -> (acc shl 8) or u8(data, at + k).toLong() }
    private fun ascii(data: ByteArray, at: Int, text: String) =
        at + text.length <= data.size && text.indices.all { data[at + it] == text[it].code.toByte() }
}
