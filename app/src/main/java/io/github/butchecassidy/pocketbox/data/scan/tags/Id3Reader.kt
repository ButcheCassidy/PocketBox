// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Reads ID3v2.2 / 2.3 / 2.4 tags and the old 128-byte ID3v1 tag. */
object Id3Reader {
    private const val HEADER_SIZE = 10
    private const val MAX_TEXT_FRAME = 64 * 1024
    private const val MAX_LYRICS_FRAME = 1024 * 1024
    private const val MAX_UNSYNC_TAG = 32 * 1024 * 1024

    private val textFrames = mapOf(
        "TIT2" to RawTags.TITLE, "TT2" to RawTags.TITLE,
        "TPE1" to RawTags.ARTIST, "TP1" to RawTags.ARTIST,
        "TALB" to RawTags.ALBUM, "TAL" to RawTags.ALBUM,
        "TPE2" to RawTags.ALBUM_ARTIST, "TP2" to RawTags.ALBUM_ARTIST,
        "TCON" to RawTags.GENRE, "TCO" to RawTags.GENRE,
        "TCOM" to RawTags.COMPOSER, "TCM" to RawTags.COMPOSER,
        "TDRC" to RawTags.DATE, "TYER" to RawTags.DATE, "TYE" to RawTags.DATE,
        "TRCK" to RawTags.TRACK_NUMBER, "TRK" to RawTags.TRACK_NUMBER,
        "TPOS" to RawTags.DISC_NUMBER, "TPA" to RawTags.DISC_NUMBER,
        "TSOT" to RawTags.TITLE_SORT, "TST" to RawTags.TITLE_SORT,
        "TSOP" to RawTags.ARTIST_SORT, "TSP" to RawTags.ARTIST_SORT,
        "TSOA" to RawTags.ALBUM_SORT, "TSA" to RawTags.ALBUM_SORT,
        "TSO2" to RawTags.ALBUM_ARTIST_SORT, "TS2" to RawTags.ALBUM_ARTIST_SORT,
        "TCMP" to RawTags.COMPILATION, "TCP" to RawTags.COMPILATION,
    )
    private val userTextFrames = setOf("TXXX", "TXX")
    private val lyricsFrames = setOf("USLT", "ULT")
    private val pictureFrames = setOf("APIC", "PIC")

    /** Total byte length of an ID3v2 tag starting at [at] (header + body + footer), or 0 if none. */
    fun tagLength(reader: BinaryReader, at: Long): Long {
        val header = reader.peek(at, HEADER_SIZE)
        if (header.size < HEADER_SIZE || !header.startsWithAscii("ID3")) return 0
        val major = header[3].toInt()
        if (major !in 2..4) return 0
        val sizeBytes = header.copyOfRange(6, 10)
        if (sizeBytes.any { it.toInt() and 0x80 != 0 }) return 0
        val body = sizeBytes.fold(0L) { acc, b -> (acc shl 7) or (b.toLong() and 0x7F) }
        val footer = if (major == 4 && header[5].toInt() and 0x10 != 0) HEADER_SIZE else 0
        return HEADER_SIZE + body + footer
    }

    /** Parses an ID3v2 tag at [at] into [tags]. Returns the tag's total length (0 if there is none). */
    fun read(reader: BinaryReader, at: Long, tags: RawTags): Long {
        val length = tagLength(reader, at)
        if (length == 0L) return 0
        reader.position = at + 3
        val major = reader.u8()
        reader.u8() // revision
        val flags = reader.u8()
        val bodySize = reader.syncsafe32().toLong()
        val bodyStart = at + HEADER_SIZE
        val bodyEnd = minOf(bodyStart + bodySize, reader.size)

        if (flags and 0x80 != 0 && major < 4) {
            // Whole-tag unsynchronisation (v2.2/2.3): undo it in memory, then parse the clean copy.
            if (bodyEnd - bodyStart > MAX_UNSYNC_TAG) return length
            reader.position = bodyStart
            val clean = removeUnsync(reader.readFully((bodyEnd - bodyStart).toInt()))
            val inner = BinaryReader(ArrayByteSource(clean))
            readFrames(inner, 0, clean.size.toLong(), major, flags, tags)
        } else {
            readFrames(reader, bodyStart, bodyEnd, major, flags, tags)
        }
        return length
    }

    private fun readFrames(reader: BinaryReader, start: Long, end: Long, major: Int, flags: Int, tags: RawTags) {
        reader.position = start
        if (flags and 0x40 != 0 && major >= 3) {
            val extSize = if (major == 3) reader.u32be() + 4 else reader.syncsafe32().toLong()
            reader.position = start + extSize
        }
        val idLength = if (major == 2) 3 else 4
        val frameHeader = if (major == 2) 6 else 10
        while (reader.position + frameHeader <= end) {
            val frameStart = reader.position
            val id = String(reader.readFully(idLength), Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break // padding or garbage
            val size = when (major) {
                2 -> reader.u24be().toLong()
                3 -> reader.u32be()
                else -> readV4FrameSize(reader, frameStart, end)
            }
            val formatFlags = if (major >= 3) {
                reader.u8()
                reader.u8()
            } else {
                0
            }
            val bodyStart = reader.position
            if (size <= 0 || bodyStart + size > end) break
            runCatching { readFrame(reader, id, size, major, formatFlags, tags) }
            reader.position = bodyStart + size
        }
    }

    /** ID3v2.4 sizes are syncsafe, but some old writers used plain integers; detect that case. */
    private fun readV4FrameSize(reader: BinaryReader, frameStart: Long, end: Long): Long {
        val raw = reader.peek(reader.position, 4)
        reader.skip(4)
        if (raw.size < 4) return -1
        val plain = raw.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        if (raw.any { it.toInt() and 0x80 != 0 }) return plain
        val syncsafe = raw.fold(0L) { acc, b -> (acc shl 7) or (b.toLong() and 0x7F) }
        if (syncsafe == plain) return syncsafe
        // Prefer whichever size lands exactly on another frame header (or the end of the tag).
        val afterSyncsafe = frameStart + 10 + syncsafe
        return if (afterSyncsafe == end || looksLikeFrameId(reader.peek(afterSyncsafe, 4))) syncsafe else plain
    }

    private fun looksLikeFrameId(bytes: ByteArray): Boolean =
        bytes.size == 4 && bytes.all { it.toInt().toChar().let { c -> c in 'A'..'Z' || c in '0'..'9' } } ||
            bytes.isNotEmpty() && bytes[0].toInt() == 0

    private fun readFrame(reader: BinaryReader, id: String, size: Long, major: Int, formatFlags: Int, tags: RawTags) {
        val isText = id in textFrames || id in userTextFrames
        val isLyrics = id in lyricsFrames
        val isPicture = id in pictureFrames
        if (!isText && !isLyrics && !isPicture) return

        if (isPicture && !tags.wantsPicture) return tags.offerPicture(0, null)
        if (isLyrics && !tags.wantsLyrics) return tags.markLyrics()

        val limit = when {
            isPicture -> BinaryReader.MAX_SINGLE_READ.toLong()
            isLyrics -> MAX_LYRICS_FRAME.toLong()
            else -> MAX_TEXT_FRAME.toLong()
        }
        if (size > limit) return

        var body = reader.readFully(size.toInt())
        if (major == 4) {
            if (formatFlags and 0x0C != 0) return // compressed or encrypted: not supported, skip safely
            var offset = 0
            if (formatFlags and 0x40 != 0) offset += 1 // group id
            if (formatFlags and 0x01 != 0) offset += 4 // data length indicator
            body = body.copyOfRange(minOf(offset, body.size), body.size)
            if (formatFlags and 0x02 != 0) body = removeUnsync(body)
        } else if (major == 3) {
            if (formatFlags and 0xC0 != 0) return // compressed or encrypted
            if (formatFlags and 0x20 != 0) body = body.copyOfRange(minOf(1, body.size), body.size)
        }
        if (body.isEmpty()) return

        when {
            id in textFrames -> decodeTextList(body).forEach { value ->
                val key = textFrames.getValue(id)
                tags.add(key, if (key == RawTags.GENRE) Id3Genres.resolve(value) else value)
            }
            id in userTextFrames -> {
                val encoding = body[0].toInt()
                val descEnd = findTerminator(body, 1, encoding)
                val description = decode(body, 1, descEnd, encoding)
                val valueStart = descEnd + terminatorLength(encoding)
                decodeTextList(byteArrayOf(body[0]) + body.copyOfRange(minOf(valueStart, body.size), body.size))
                    .forEach { tags.add(description, it) }
            }
            isLyrics -> {
                if (body.size < 4) return
                val encoding = body[0].toInt()
                val descEnd = findTerminator(body, 4, encoding)
                val textStart = minOf(descEnd + terminatorLength(encoding), body.size)
                tags.add(RawTags.LYRICS, decode(body, textStart, body.size, encoding))
            }
            isPicture -> parsePicture(body, id, tags)
        }
    }

    private fun parsePicture(body: ByteArray, id: String, tags: RawTags) {
        val encoding = body[0].toInt()
        val typeIndex = if (id == "PIC") {
            4 // 1 byte encoding + 3 byte image format
        } else {
            findTerminator(body, 1, 0) + 1 // MIME type is always Latin-1, null-terminated
        }
        if (typeIndex >= body.size) return
        val pictureType = body[typeIndex].toInt() and 0xFF
        val descEnd = findTerminator(body, typeIndex + 1, encoding)
        val dataStart = descEnd + terminatorLength(encoding)
        if (dataStart >= body.size) return
        tags.offerPicture(pictureType, body.copyOfRange(dataStart, body.size))
    }

    /** Reads the 128-byte ID3v1 tag at the end of a file, if any, without overriding ID3v2 values. */
    fun readV1(reader: BinaryReader, tags: RawTags): Boolean {
        if (reader.size < 128) return false
        val tag = reader.peek(reader.size - 128, 128)
        if (!tag.startsWithAscii("TAG")) return false
        fun field(from: Int, length: Int) =
            String(tag, from, length, Charsets.ISO_8859_1).substringBefore('\u0000').trim()
        if (!tags.has(RawTags.TITLE)) tags.add(RawTags.TITLE, field(3, 30))
        if (!tags.has(RawTags.ARTIST)) tags.add(RawTags.ARTIST, field(33, 30))
        if (!tags.has(RawTags.ALBUM)) tags.add(RawTags.ALBUM, field(63, 30))
        if (!tags.has(RawTags.DATE)) tags.add(RawTags.DATE, field(93, 4))
        if (!tags.has(RawTags.TRACK_NUMBER) && tag[125].toInt() == 0 && tag[126].toInt() != 0) {
            tags.add(RawTags.TRACK_NUMBER, (tag[126].toInt() and 0xFF).toString())
        }
        if (!tags.has(RawTags.GENRE)) Id3Genres.byIndex(tag[127].toInt() and 0xFF)?.let { tags.add(RawTags.GENRE, it) }
        return true
    }

    internal fun removeUnsync(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i] == 0xFF.toByte() && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun charset(encoding: Int): Charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun terminatorLength(encoding: Int) = if (encoding == 1 || encoding == 2) 2 else 1

    /** Index of the string terminator starting at [from] (or the end of the data). */
    private fun findTerminator(data: ByteArray, from: Int, encoding: Int): Int {
        if (terminatorLength(encoding) == 1) {
            for (i in from until data.size) if (data[i].toInt() == 0) return i
        } else {
            var i = from
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
        }
        return data.size
    }

    private fun decode(data: ByteArray, from: Int, to: Int, encoding: Int): String {
        if (from >= to) return ""
        return String(data, from, to - from, charset(encoding)).replace("\uFEFF", "")
    }

    /** A text frame: encoding byte, then one or more null-separated values. */
    private fun decodeTextList(body: ByteArray): List<String> =
        decode(body, 1, body.size, body[0].toInt())
            .split('\u0000')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

/** The standard ID3v1 genre list (0–79) and the "(17)Rock" style references to it. */
internal object Id3Genres {
    private val names = (
        "Blues|Classic Rock|Country|Dance|Disco|Funk|Grunge|Hip-Hop|Jazz|Metal|New Age|Oldies|Other|Pop|R&B|" +
            "Rap|Reggae|Rock|Techno|Industrial|Alternative|Ska|Death Metal|Pranks|Soundtrack|Euro-Techno|Ambient|" +
            "Trip-Hop|Vocal|Jazz+Funk|Fusion|Trance|Classical|Instrumental|Acid|House|Game|Sound Clip|Gospel|" +
            "Noise|AlternRock|Bass|Soul|Punk|Space|Meditative|Instrumental Pop|Instrumental Rock|Ethnic|Gothic|" +
            "Darkwave|Techno-Industrial|Electronic|Pop-Folk|Eurodance|Dream|Southern Rock|Comedy|Cult|Gangsta|" +
            "Top 40|Christian Rap|Pop/Funk|Jungle|Native American|Cabaret|New Wave|Psychadelic|Rave|Showtunes|" +
            "Trailer|Lo-Fi|Tribal|Acid Punk|Acid Jazz|Polka|Retro|Musical|Rock & Roll|Hard Rock"
        ).split('|')
    private val reference = Regex("""^\((\d{1,3})\)(.*)$""")

    fun byIndex(index: Int): String? = names.getOrNull(index)

    fun resolve(value: String): String {
        reference.find(value)?.let { match ->
            val rest = match.groupValues[2].trim()
            return rest.ifEmpty { byIndex(match.groupValues[1].toInt()) ?: value }
        }
        return value.toIntOrNull()?.let(::byIndex) ?: value
    }
}
