// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.integrity

import io.github.butchecassidy.pocketbox.data.scan.tags.BinaryReader
import io.github.butchecassidy.pocketbox.data.scan.tags.MalformedAudioException
import io.github.butchecassidy.pocketbox.data.scan.tags.startsWithAscii

/** Structure checks for FLAC, Ogg, MP4/M4A, WAV and AIFF. Pictures and text are checked separately. */
internal object ContainerChecks {

    // ---- FLAC ----

    fun flac(reader: BinaryReader, at: Long, report: Report) {
        var pos = at + 4
        var blocks = 0
        while (true) {
            val header = reader.peek(pos, 4)
            if (header.size < 4) throw MalformedAudioException("FLAC metadata is cut off")
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toLong() and 0xFF) shl 16) or ((header[2].toLong() and 0xFF) shl 8) or (header[3].toLong() and 0xFF)
            val start = pos + 4
            val end = start + length
            if (end > reader.size) throw MalformedAudioException("FLAC block runs past the end of the file")
            when (type) {
                // Stream info, seek table, Vorbis comments, cue sheet and pictures are all normal.
                0, 3, 4, 5, 6 -> Unit
                1 -> report.checkPadding(reader, start, end, "FLAC padding")
                2 -> {
                    val id = String(reader.peek(start, 4), Charsets.ISO_8859_1)
                    report.add(IntegrityIssue.APPLICATION_DATA, "FLAC application block '$id' (${Report.sizeLabel(length)})")
                    report.scanForCode(reader, start, end, "a FLAC application block")
                }
                127 -> throw MalformedAudioException("invalid FLAC block type")
                else -> {
                    report.add(IntegrityIssue.UNKNOWN_BLOCK, "FLAC block type $type (${Report.sizeLabel(length)})")
                    report.scanForCode(reader, start, end, "FLAC block type $type")
                }
            }
            pos = end
            if (last || ++blocks >= MAX_FLAC_BLOCKS) break
        }
        val audioEnd = Footers.strip(reader, reader.size, report)
        val first = reader.peek(pos, FLAC_HEAD_WINDOW)
        val firstFrame = (0 until first.size).firstOrNull { flacHeaderLength(first, it) != null }
            ?: throw MalformedAudioException("no FLAC audio frames found")
        if (firstFrame > 0) {
            report.add(IntegrityIssue.DATA_BEFORE_AUDIO, "${Report.sizeLabel(firstFrame.toLong())} after the FLAC metadata")
            report.scanForCode(first.copyOfRange(0, firstFrame), "data before the audio")
        }
        if (!flacEndsCleanly(reader, pos + firstFrame, audioEnd)) {
            report.add(IntegrityIssue.DATA_AFTER_AUDIO, "after the last FLAC frame")
            report.scanForCode(reader, maxOf(pos, audioEnd - FLAC_TAIL_WINDOW), audioEnd, "the end of the file")
        }
    }

    /**
     * Every FLAC frame ends with a CRC-16 of the whole frame. The audio ends cleanly when one of the
     * last frame headers has a CRC that matches the last two bytes of the file's audio.
     */
    private fun flacEndsCleanly(reader: BinaryReader, framesStart: Long, end: Long): Boolean {
        val windowStart = maxOf(framesStart, end - FLAC_TAIL_WINDOW)
        val bytes = reader.peek(windowStart, (end - windowStart).toInt())
        if (bytes.size < 2) return false
        val stored = ((bytes[bytes.size - 2].toInt() and 0xFF) shl 8) or (bytes[bytes.size - 1].toInt() and 0xFF)
        var tried = 0
        for (i in bytes.size - 2 downTo 0) {
            if (flacHeaderLength(bytes, i) == null) continue
            if (Crc.flac16(bytes, i, bytes.size - 2) == stored) return true
            if (++tried >= MAX_FLAC_CANDIDATES) break
        }
        return false
    }

    /** Length of a valid FLAC frame header at [at] (checked with its CRC-8), or null. */
    private fun flacHeaderLength(b: ByteArray, at: Int): Int? {
        if (at + 6 > b.size || b[at].toInt() and 0xFF != 0xFF || b[at + 1].toInt() and 0xFE != 0xF8) return null
        val blockCode = (b[at + 2].toInt() and 0xFF) shr 4
        val rateCode = b[at + 2].toInt() and 0x0F
        val b3 = b[at + 3].toInt() and 0xFF
        if (blockCode == 0 || rateCode == 15 || (b3 shr 4) > 10 || (b3 shr 1) and 7 == 3 || b3 and 1 != 0) return null
        var p = at + 4
        val lead = b[p].toInt() and 0xFF
        val numberLength = when {
            lead < 0x80 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            lead and 0xFC == 0xF8 -> 5
            lead and 0xFE == 0xFC -> 6
            lead == 0xFE -> 7
            else -> return null
        }
        if (p + numberLength > b.size) return null
        for (k in 1 until numberLength) if (b[p + k].toInt() and 0xC0 != 0x80) return null
        p += numberLength
        p += when (blockCode) { 6 -> 1; 7 -> 2; else -> 0 }
        p += when (rateCode) { 12 -> 1; 13, 14 -> 2; else -> 0 }
        if (p >= b.size || Crc.flac8(b, at, p) != b[p].toInt() and 0xFF) return null
        return p + 1 - at
    }

    // ---- Ogg ----

    private val oggAudioCodecs = listOf("\u0001vorbis", "OpusHead", "\u007FFLAC", "Speex   ")

    fun ogg(reader: BinaryReader, report: Report) {
        // The first pages announce every stream in the file; only audio (plus the Skeleton index) is expected.
        var pos = 0L
        var audioStreams = 0
        for (i in 0 until MAX_OGG_STREAMS) {
            val page = oggPage(reader, pos) ?: throw MalformedAudioException("Ogg page is damaged")
            if (!page.beginsStream) break
            val body = reader.peek(page.bodyStart, 8)
            when {
                oggAudioCodecs.any { body.startsWithAscii(it) } -> audioStreams++
                body.startsWithAscii("fishead") -> Unit
                body.startsWithAscii("\u0080theora") -> report.add(IntegrityIssue.EXTRA_STREAM, "Theora video stream")
                else -> report.add(IntegrityIssue.EXTRA_STREAM, "an unrecognised Ogg stream")
            }
            pos = page.end
        }
        if (audioStreams > 1) report.add(IntegrityIssue.EXTRA_STREAM, "$audioStreams audio streams in one file")
        val end = Footers.strip(reader, reader.size, report)
        if (!oggEndsCleanly(reader, end)) {
            report.add(IntegrityIssue.DATA_AFTER_AUDIO, "after the last Ogg page")
            report.scanForCode(reader, maxOf(0, end - OGG_TAIL_WINDOW), end, "the end of the file")
        }
    }

    private class OggPage(val beginsStream: Boolean, val bodyStart: Long, val end: Long)

    private fun oggPage(reader: BinaryReader, at: Long): OggPage? {
        val header = reader.peek(at, 27)
        if (header.size < 27 || !header.startsWithAscii("OggS") || header[4].toInt() != 0) return null
        val segments = header[26].toInt() and 0xFF
        val lacing = reader.peek(at + 27, segments)
        if (lacing.size < segments) return null
        val bodyStart = at + 27 + segments
        return OggPage(header[5].toInt() and 0x02 != 0, bodyStart, bodyStart + lacing.sumOf { it.toInt() and 0xFF })
    }

    /** Ogg pages carry their own length, so the pages near the end must chain exactly to the end. */
    private fun oggEndsCleanly(reader: BinaryReader, end: Long): Boolean {
        val windowStart = maxOf(0, end - OGG_TAIL_WINDOW)
        val bytes = reader.peek(windowStart, (end - windowStart).toInt())
        val reaches = IntArray(bytes.size + 1)
        for (i in bytes.size - 4 downTo 0) {
            if (!bytes.startsWithAscii("OggS", i)) continue
            val page = oggPage(reader, windowStart + i) ?: continue
            val next = (page.end - windowStart).toInt()
            reaches[i] = when {
                page.end == end -> 1
                next in 0 until bytes.size && reaches[next] > 0 -> reaches[next] + 1
                else -> 0
            }
            if (reaches[i] >= 2 || (windowStart == 0L && i == 0 && reaches[i] > 0)) return true
        }
        return false
    }

    // ---- MP4 / M4A ----

    private val normalTopLevelBoxes = setOf("ftyp", "moov", "mdat", "wide", "pdin", "sidx", "moof", "mfra", "styp", "meta", "udta", "prft")
    private val normalTrackTypes = setOf("soun", "text", "sbtl", "subt", "hint", "meta", "tmcd", "chap")

    fun mp4(reader: BinaryReader, report: Report) {
        var pos = 0L
        var boxes = 0
        while (pos < reader.size && boxes++ < MAX_BOXES) {
            if (reader.size - pos < 8) {
                if (!report.isAllZero(reader, pos, reader.size)) report.add(IntegrityIssue.DATA_AFTER_AUDIO, "after the last MP4 box")
                return
            }
            val box = mp4Box(reader, pos, reader.size)
            if (box == null) {
                report.add(IntegrityIssue.UNKNOWN_BLOCK, "${Report.sizeLabel(reader.size - pos)} that isn't part of the MP4 structure")
                report.scanForCode(reader, pos, reader.size, "data outside the MP4 structure")
                return
            }
            when (box.type) {
                "moov" -> mp4Tracks(reader, box.bodyStart, box.end, report)
                in normalTopLevelBoxes -> Unit
                "free", "skip" -> report.scanForCode(reader, box.bodyStart, box.end, "an MP4 '${box.type}' box")
                "uuid" -> {
                    if (box.end - box.bodyStart > Report.LARGE_BLOCK) report.add(IntegrityIssue.UNKNOWN_BLOCK, "MP4 'uuid' box (${Report.sizeLabel(box.end - box.bodyStart)})")
                    report.scanForCode(reader, box.bodyStart, box.end, "an MP4 'uuid' box")
                }
                else -> {
                    report.add(IntegrityIssue.UNKNOWN_BLOCK, "MP4 box '${box.type}' (${Report.sizeLabel(box.end - box.bodyStart)})")
                    report.scanForCode(reader, box.bodyStart, box.end, "MP4 box '${box.type}'")
                }
            }
            pos = box.end
        }
    }

    private class Box(val type: String, val bodyStart: Long, val end: Long)

    /** Reads a box header at [at]; null if it isn't a sensible box inside [limit]. */
    private fun mp4Box(reader: BinaryReader, at: Long, limit: Long): Box? {
        val header = reader.peek(at, 16)
        if (header.size < 8) return null
        val size32 = ((header[0].toLong() and 0xFF) shl 24) or ((header[1].toLong() and 0xFF) shl 16) or
            ((header[2].toLong() and 0xFF) shl 8) or (header[3].toLong() and 0xFF)
        val type = String(header, 4, 4, Charsets.ISO_8859_1)
        if (!type.all { it in ' '..'~' || it == '©' }) return null
        val (size, headerLength) = when (size32) {
            0L -> (limit - at) to 8
            1L -> {
                if (header.size < 16) return null
                (0 until 8).fold(0L) { acc, k -> (acc shl 8) or (header[8 + k].toLong() and 0xFF) } to 16
            }
            else -> size32 to 8
        }
        if (size < headerLength || at + size > limit) {
            // A cut-off final media box means an incomplete download, not something hidden.
            if (type == "mdat") throw MalformedAudioException("the audio data is cut off")
            return null
        }
        return Box(type, at + headerLength, at + size)
    }

    /** Looks at each track's handler: audio, chapters and text are normal; video or anything else is reported. */
    private fun mp4Tracks(reader: BinaryReader, start: Long, end: Long, report: Report) {
        forEachChild(reader, start, end) { trak ->
            if (trak.type != "trak") return@forEachChild
            forEachChild(reader, trak.bodyStart, trak.end) { mdia ->
                if (mdia.type != "mdia") return@forEachChild
                forEachChild(reader, mdia.bodyStart, mdia.end) { hdlr ->
                    if (hdlr.type != "hdlr") return@forEachChild
                    val handler = String(reader.peek(hdlr.bodyStart + 8, 4), Charsets.ISO_8859_1)
                    when {
                        handler in normalTrackTypes -> Unit
                        handler == "vide" -> report.add(IntegrityIssue.EXTRA_STREAM, "MP4 video track (could be chapter pictures)")
                        else -> report.add(IntegrityIssue.EXTRA_STREAM, "MP4 '$handler' track")
                    }
                }
            }
        }
    }

    private inline fun forEachChild(reader: BinaryReader, start: Long, end: Long, block: (Box) -> Unit) {
        var pos = start
        var count = 0
        while (pos + 8 <= end && count++ < MAX_BOXES) {
            val box = mp4Box(reader, pos, end) ?: return
            block(box)
            pos = box.end
        }
    }

    // ---- WAV (RIFF / RF64) and AIFF ----

    private val normalWaveChunks = setOf(
        "fmt ", "data", "fact", "LIST", "bext", "cue ", "smpl", "inst", "acid", "iXML", "axml", "ds64", "PEAK", "cart",
        "levl", "_PMX", "DISP", "MD5 ", "umid", "plst", "minf", "elm1", "regn", "ResU", "SyLp",
    )
    private val normalAiffChunks = setOf("COMM", "SSND", "FVER", "MARK", "INST", "COMT", "NAME", "AUTH", "(c) ", "ANNO", "CHAN", "AESD")
    private val id3Chunks = setOf("id3 ", "ID3 ")
    private val paddingChunks = setOf("JUNK", "junk", "PAD ", "FLLR")

    fun riff(reader: BinaryReader, report: Report, wave: Boolean) {
        val header = reader.peek(0, 12)
        val rf64 = header.startsWithAscii("RF64")
        var formEnd = 8 + u32(header, 4, littleEndian = wave)
        var dataSize64 = -1L
        var pos = 12L
        var chunks = 0
        while (pos + 8 <= minOf(formEnd, reader.size) && chunks++ < MAX_CHUNKS) {
            val chunkHeader = reader.peek(pos, 8)
            val id = String(chunkHeader, 0, 4, Charsets.ISO_8859_1)
            var size = u32(chunkHeader, 4, littleEndian = wave)
            if (rf64 && id == "ds64") {
                val ds64 = reader.peek(pos + 8, 16)
                if (ds64.size == 16) {
                    formEnd = 8 + u64le(ds64, 0)
                    dataSize64 = u64le(ds64, 8)
                }
            }
            if (rf64 && id == "data" && size == 0xFFFFFFFFL && dataSize64 >= 0) size = dataSize64
            val start = pos + 8
            val end = start + size
            if (end > reader.size) {
                if (id == "data" || id == "SSND") throw MalformedAudioException("the audio data is cut off")
                throw MalformedAudioException("chunk '$id' runs past the end of the file")
            }
            when {
                id in id3Chunks -> IntegrityInspector.id3(reader, start, report)
                id in paddingChunks -> report.scanForCode(reader, start, end, "a '$id' padding chunk")
                id == "APPL" -> {
                    report.add(IntegrityIssue.APPLICATION_DATA, "AIFF application chunk (${Report.sizeLabel(size)})")
                    report.scanForCode(reader, start, end, "an AIFF application chunk")
                }
                (wave && id in normalWaveChunks) || (!wave && id in normalAiffChunks) -> Unit
                else -> {
                    if (size > Report.LARGE_BLOCK) report.add(IntegrityIssue.UNKNOWN_BLOCK, "chunk '$id' (${Report.sizeLabel(size)})")
                    report.scanForCode(reader, start, end, "chunk '$id'")
                }
            }
            pos = end + (size and 1)
        }
        // Some taggers add an ID3 tag after the audio; anything else after the declared end is reported.
        var trailing = minOf(maxOf(pos, formEnd), reader.size)
        trailing += IntegrityInspector.id3(reader, trailing, report)
        val end = Footers.strip(reader, reader.size, report)
        if (trailing < end && !report.isAllZero(reader, trailing, end)) {
            report.add(IntegrityIssue.DATA_AFTER_AUDIO, "${Report.sizeLabel(end - trailing)} after the end of the ${if (wave) "WAV" else "AIFF"} data")
            report.scanForCode(reader, trailing, end, "data after the audio")
        }
    }

    private fun u32(b: ByteArray, at: Int, littleEndian: Boolean): Long = (0..3).fold(0L) { acc, k ->
        if (littleEndian) acc or ((b[at + k].toLong() and 0xFF) shl (8 * k)) else (acc shl 8) or (b[at + k].toLong() and 0xFF)
    }

    private fun u64le(b: ByteArray, at: Int): Long = (0..7).fold(0L) { acc, k -> acc or ((b[at + k].toLong() and 0xFF) shl (8 * k)) }

    private const val MAX_FLAC_BLOCKS = 1024
    private const val FLAC_HEAD_WINDOW = 64 * 1024
    private const val FLAC_TAIL_WINDOW = 256L * 1024
    private const val MAX_FLAC_CANDIDATES = 16
    private const val MAX_OGG_STREAMS = 16
    private const val OGG_TAIL_WINDOW = 128L * 1024
    private const val MAX_BOXES = 4096
    private const val MAX_CHUNKS = 4096
}

/** The two CRCs FLAC frames use (polynomials 0x07 and 0x8005, no reflection). */
internal object Crc {
    private val table8 = IntArray(256) { i ->
        var c = i
        repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }
        c and 0xFF
    }
    private val table16 = IntArray(256) { i ->
        var c = i shl 8
        repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x8005 else c shl 1 }
        c and 0xFFFF
    }

    fun flac8(b: ByteArray, from: Int, until: Int): Int {
        var crc = 0
        for (i in from until until) crc = table8[crc xor (b[i].toInt() and 0xFF)]
        return crc
    }

    fun flac16(b: ByteArray, from: Int, until: Int): Int {
        var crc = 0
        for (i in from until until) crc = ((crc shl 8) xor table16[(crc shr 8) xor (b[i].toInt() and 0xFF)]) and 0xFFFF
        return crc
    }
}
