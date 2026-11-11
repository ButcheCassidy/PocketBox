// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.integrity

import io.github.butchecassidy.pocketbox.data.scan.tags.ArrayByteSource
import io.github.butchecassidy.pocketbox.data.scan.tags.AudioFileReader
import io.github.butchecassidy.pocketbox.data.scan.tags.BinaryReader
import io.github.butchecassidy.pocketbox.data.scan.tags.ByteSource
import io.github.butchecassidy.pocketbox.data.scan.tags.Id3Reader
import io.github.butchecassidy.pocketbox.data.scan.tags.MalformedAudioException
import io.github.butchecassidy.pocketbox.data.scan.tags.Mp3Reader
import io.github.butchecassidy.pocketbox.data.scan.tags.ReadOptions
import io.github.butchecassidy.pocketbox.data.scan.tags.RawTags
import io.github.butchecassidy.pocketbox.data.scan.tags.TagObserver
import io.github.butchecassidy.pocketbox.data.scan.tags.startsWithAscii

/**
 * The integrity check run once, the first time a song is added. It reads the file's structure (never
 * plays or opens anything inside it) and reports whatever is there besides the music, ordinary tags,
 * album art and lyrics: embedded files, program code, data hidden after the picture or the music,
 * filled-in padding, other programs' blocks and extra streams such as video.
 *
 * "Balanced" strictness: well-known harmless extras (store IDs, encoder notes, ReplayGain, cue sheets,
 * seek tables, empty padding) are not reported, so normal ripped or purchased files pass.
 */
object IntegrityInspector {
    fun inspect(source: ByteSource): List<IntegrityFinding> {
        val report = Report()
        checkContent(source, report)
        try {
            checkStructure(BinaryReader(source), report)
        } catch (damaged: MalformedAudioException) {
            report.add(IntegrityIssue.DAMAGED, damaged.message ?: "file structure")
        }
        return report.findings
    }

    /** Every picture and text tag the normal readers find (in any format) goes through the report. */
    private fun checkContent(source: ByteSource, report: Report) {
        val observer = object : TagObserver {
            override fun onPicture(data: ByteArray) = report.checkPicture(data, "embedded album art")
            override fun onText(key: String, value: String) {
                if (key != RawTags.LYRICS) report.checkText(key, value)
            }
        }
        // A file the built-in readers can't parse is still checked structurally below.
        runCatching { AudioFileReader.read(source, ReadOptions.INSPECT, observer) }
    }

    private fun checkStructure(reader: BinaryReader, report: Report) {
        val head = reader.peek(0, 12)
        when {
            head.startsWithAscii("fLaC") -> ContainerChecks.flac(reader, 0, report)
            head.startsWithAscii("OggS") -> ContainerChecks.ogg(reader, report)
            head.startsWithAscii("ftyp", 4) -> ContainerChecks.mp4(reader, report)
            (head.startsWithAscii("RIFF") || head.startsWithAscii("RF64")) && head.startsWithAscii("WAVE", 8) ->
                ContainerChecks.riff(reader, report, wave = true)
            head.startsWithAscii("FORM") && (head.startsWithAscii("AIFF", 8) || head.startsWithAscii("AIFC", 8)) ->
                ContainerChecks.riff(reader, report, wave = false)
            head.startsWithAscii("ID3") || Mp3Reader.startsWithFrame(head) -> id3Prefixed(reader, report)
            // Formats Pocket Box only reads through Android (raw AAC, Matroska): check the file isn't a program.
            else -> report.scanForCode(head + reader.peek(head.size.toLong(), HEAD_PROBE), "the start of the file")
        }
    }

    /** MP3 (or FLAC) behind one or more ID3v2 tags. */
    private fun id3Prefixed(reader: BinaryReader, report: Report) {
        var audioStart = 0L
        repeat(MAX_LEADING_TAGS) {
            val length = id3(reader, audioStart, report)
            if (length == 0L) return@repeat
            audioStart += length
        }
        if (reader.peek(audioStart, 4).startsWithAscii("fLaC")) return ContainerChecks.flac(reader, audioStart, report)
        val end = Footers.strip(reader, reader.size, report)
        mpegAudio(reader, audioStart, end, report)
    }

    // ---- ID3v2 ----

    /** Frames that are normal in music files. Anything else is reported if it's big, and checked for code. */
    private val knownId3Frames = setOf(
        "COMM", "USLT", "SYLT", "APIC", "UFID", "PRIV", "POPM", "PCNT", "RVA2", "RVAD", "EQU2", "EQUA", "MCDI",
        "CHAP", "CTOC", "MLLT", "ETCO", "SYTC", "RBUF", "AENC", "LINK", "POSS", "OWNE", "COMR", "USER", "GRID",
        "SIGN", "SEEK", "ASPI", "IPLS", "RGAD", "NCON", "XRVA", "PCST", "GRP1", "MVNM", "MVIN", "ENCR",
        "COM", "ULT", "SLT", "PIC", "UFI", "POP", "CNT", "RVA", "EQU", "MCI", "MLL", "ETC", "STC", "REV",
        "CRA", "LNK", "BUF", "IPL",
    )
    private val embeddedObjectFrames = setOf("GEOB", "GEO")

    /** Checks the ID3v2 tag at [at] frame by frame; returns its length (0 if there is none). */
    internal fun id3(reader: BinaryReader, at: Long, report: Report): Long {
        val length = Id3Reader.tagLength(reader, at)
        if (length == 0L) return 0
        if (at + length > reader.size) throw MalformedAudioException("ID3 tag runs past the end of the file")
        val header = reader.peek(at, ID3_HEADER)
        val major = header[3].toInt()
        val flags = header[5].toInt() and 0xFF
        val bodySize = length - ID3_HEADER - if (major == 4 && flags and 0x10 != 0) ID3_HEADER else 0
        if (bodySize > BinaryReader.MAX_SINGLE_READ) {
            report.add(IntegrityIssue.OVERSIZED, "ID3 tag (${Report.sizeLabel(bodySize)})")
            return length
        }
        var body = reader.peek(at + ID3_HEADER, bodySize.toInt())
        if (flags and 0x80 != 0 && major < 4) body = Id3Reader.removeUnsync(body)
        id3Frames(body, major, flags, report)
        return length
    }

    private fun id3Frames(body: ByteArray, major: Int, tagFlags: Int, report: Report) {
        val tag = BinaryReader(ArrayByteSource(body))
        if (tagFlags and 0x40 != 0 && major >= 3 && body.size >= 4) {
            tag.position = if (major == 3) tag.u32be() + 4 else tag.syncsafe32().toLong()
        }
        val idLength = if (major == 2) 3 else 4
        val headerLength = if (major == 2) 6 else 10
        while (tag.position + headerLength <= body.size) {
            val frameStart = tag.position.toInt()
            if (body[frameStart].toInt() == 0) {
                // Padding: should be nothing but zeros up to the end of the tag.
                if ((frameStart until body.size).any { body[it].toInt() != 0 }) {
                    report.add(IntegrityIssue.HIDDEN_IN_PADDING, "ID3 tag padding")
                    report.scanForCode(body.copyOfRange(frameStart, body.size), "ID3 tag padding")
                }
                return
            }
            val id = String(tag.readFully(idLength), Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) {
                report.add(IntegrityIssue.UNKNOWN_BLOCK, "unreadable data inside the ID3 tag")
                report.scanForCode(body.copyOfRange(frameStart, body.size), "the ID3 tag")
                return
            }
            val size = when (major) {
                2 -> tag.u24be().toLong()
                3 -> tag.u32be()
                else -> v4FrameSize(body, tag)
            }
            val frameFlags = if (major >= 3) tag.u16be() else 0
            val dataStart = tag.position.toInt()
            if (size < 0 || dataStart + size > body.size) {
                report.add(IntegrityIssue.DAMAGED, "ID3 frame $id is cut off")
                return
            }
            id3Frame(id, frameFlags, major, body.copyOfRange(dataStart, dataStart + size.toInt()), report)
            tag.position = dataStart + size
        }
    }

    /** v2.4 sizes are syncsafe, but some writers used plain numbers; pick the one that lands on a frame. */
    private fun v4FrameSize(body: ByteArray, tag: BinaryReader): Long {
        val raw = tag.readFully(4)
        val plain = raw.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        if (raw.any { it.toInt() and 0x80 != 0 }) return plain
        val syncsafe = raw.fold(0L) { acc, b -> (acc shl 7) or (b.toLong() and 0x7F) }
        val next = (tag.position + 2 + syncsafe).toInt()
        val landsOnFrame = next == body.size || (next in body.indices && (body[next].toInt() == 0 || body[next].toInt().toChar() in 'A'..'Z'))
        return if (syncsafe == plain || landsOnFrame) syncsafe else plain
    }

    private fun id3Frame(id: String, frameFlags: Int, major: Int, data: ByteArray, report: Report) {
        val encrypted = if (major == 3) frameFlags and 0x0040 != 0 else major == 4 && frameFlags and 0x0004 != 0
        when {
            encrypted -> report.add(IntegrityIssue.UNKNOWN_BLOCK, "encrypted ID3 frame $id")
            id in embeddedObjectFrames -> {
                report.add(IntegrityIssue.EMBEDDED_FILE, "ID3 $id frame (${Report.sizeLabel(data.size.toLong())})")
                report.scanForCode(data, "the ID3 $id frame")
            }
            id == "PRIV" -> {
                if (data.size > Report.LARGE_BLOCK) report.add(IntegrityIssue.APPLICATION_DATA, "ID3 PRIV frame (${Report.sizeLabel(data.size.toLong())})")
                report.scanForCode(data, "an ID3 PRIV frame")
            }
            id.startsWith("T") || id.startsWith("W") || id in knownId3Frames -> Unit
            else -> {
                if (data.size > Report.LARGE_BLOCK) report.add(IntegrityIssue.UNKNOWN_BLOCK, "ID3 frame $id (${Report.sizeLabel(data.size.toLong())})")
                report.scanForCode(data, "ID3 frame $id")
            }
        }
    }

    // ---- MPEG audio ----

    /** Finds the first real frame, then checks the frames run right up to the end of the audio. */
    private fun mpegAudio(reader: BinaryReader, audioStart: Long, end: Long, report: Report) {
        val window = reader.peek(audioStart, minOf(HEAD_WINDOW.toLong(), end - audioStart).toInt())
        val first = (0..window.size - 4).firstOrNull { i -> chainLength(window, i, window.size) >= MIN_CHAIN }
            ?: throw MalformedAudioException("no MPEG audio frames found")
        if (first > HEAD_TOLERANCE && (0 until first).any { window[it].toInt() != 0 }) {
            report.add(IntegrityIssue.DATA_BEFORE_AUDIO, "${Report.sizeLabel(first.toLong())} before the first audio frame")
            report.scanForCode(window.copyOfRange(0, first), "data before the audio")
        }
        val framesStart = audioStart + first
        if (!mpegReachesEnd(reader, framesStart, end)) {
            report.add(IntegrityIssue.DATA_AFTER_AUDIO, "after the last MPEG frame")
            report.scanForCode(reader, maxOf(framesStart, end - TAIL_WINDOW), end, "the end of the file")
        }
    }

    /** How many frames chain one after another from [from] inside [bytes] (stops at [limit]). */
    private fun chainLength(bytes: ByteArray, from: Int, limit: Int): Int {
        var at = from
        var count = 0
        while (at + 4 <= limit && count < MIN_CHAIN) {
            at += Mp3Reader.frameLength(bytes, at) ?: return count
            count++
        }
        return if (at + 4 > limit) MIN_CHAIN else count
    }

    /**
     * True when MPEG frames chain exactly to [end] (a cut-off last frame or trailing zeros are fine).
     * Works backwards over the last part of the file, so it costs one small read, not the whole file.
     */
    private fun mpegReachesEnd(reader: BinaryReader, framesStart: Long, end: Long): Boolean {
        val windowStart = maxOf(framesStart, end - TAIL_WINDOW)
        val bytes = reader.peek(windowStart, (end - windowStart).toInt())
        var effectiveEnd = bytes.size
        while (effectiveEnd > 0 && bytes[effectiveEnd - 1].toInt() == 0) effectiveEnd--
        if (effectiveEnd == 0) return true
        // reach[i]: frames starting at i chain to the end; count[i]: how many frames that chain has.
        val count = IntArray(bytes.size + 1)
        for (i in effectiveEnd - 1 downTo 0) {
            val length = Mp3Reader.frameLength(bytes, i) ?: continue
            val next = i + length
            count[i] = when {
                next >= effectiveEnd - 3 -> 1
                count[next] > 0 -> count[next] + 1
                else -> 0
            }
            if (count[i] >= MIN_CHAIN) return true
        }
        return windowStart == framesStart && count[0] > 0
    }

    private const val ID3_HEADER = 10
    private const val MAX_LEADING_TAGS = 4
    private const val HEAD_PROBE = 4096
    private const val HEAD_WINDOW = 128 * 1024
    private const val HEAD_TOLERANCE = 4 * 1024
    private const val TAIL_WINDOW = 64L * 1024
    private const val MIN_CHAIN = 3
}

/** Tags that sit at the very end of a file (ID3v1, APEv2, Lyrics3, ID3v2 footer). */
internal object Footers {
    /** Returns where the audio really ends once trailing tags are removed; APE items are checked on the way. */
    fun strip(reader: BinaryReader, fileEnd: Long, report: Report): Long {
        var end = fileEnd
        repeat(MAX_FOOTERS) {
            end = when {
                end >= ID3V1 && reader.peek(end - ID3V1, 3).startsWithAscii("TAG") -> end - ID3V1
                end >= APE_FOOTER && reader.peek(end - APE_FOOTER, 8).startsWithAscii("APETAGEX") -> ape(reader, end, report)
                end >= LYRICS3_END && reader.peek(end - 9, 9).startsWithAscii("LYRICS200") -> lyrics3(reader, end)
                end >= ID3_FOOTER && reader.peek(end - ID3_FOOTER, 3).startsWithAscii("3DI") -> id3Footer(reader, end)
                else -> return end
            }
        }
        return end
    }

    private fun ape(reader: BinaryReader, end: Long, report: Report): Long {
        val footer = BinaryReader(ArrayByteSource(reader.peek(end - APE_FOOTER, APE_FOOTER)), 12)
        val size = footer.u32le()
        val itemCount = footer.u32le()
        val flags = footer.u32le()
        val itemsStart = end - size
        if (size < APE_FOOTER || itemsStart < 0) throw MalformedAudioException("APE tag size makes no sense")
        val tagStart = itemsStart - if (flags and APE_HAS_HEADER != 0L) APE_FOOTER else 0
        if (tagStart < 0) throw MalformedAudioException("APE tag runs past the start of the file")
        if (size - APE_FOOTER > BinaryReader.MAX_SINGLE_READ) {
            report.add(IntegrityIssue.OVERSIZED, "APE tag (${Report.sizeLabel(size)})")
            return tagStart
        }
        apeItems(reader.peek(itemsStart, (size - APE_FOOTER).toInt()), itemCount, report)
        return tagStart
    }

    /** Text items are normal; binary items may only be cover art, which must be a clean picture. */
    private fun apeItems(items: ByteArray, count: Long, report: Report) {
        val r = BinaryReader(ArrayByteSource(items))
        repeat(minOf(count, MAX_APE_ITEMS).toInt()) {
            if (r.remaining < 9) return
            val valueSize = r.u32le()
            val itemFlags = r.u32le()
            val keyStart = r.position.toInt()
            val keyEnd = (keyStart until items.size).firstOrNull { items[it].toInt() == 0 } ?: return
            val key = String(items, keyStart, keyEnd - keyStart, Charsets.ISO_8859_1)
            r.position = keyEnd + 1L
            if (valueSize > r.remaining) return report.add(IntegrityIssue.DAMAGED, "APE item '$key' is cut off")
            val value = r.readFully(valueSize.toInt())
            val binary = (itemFlags shr 1) and 3L == 1L
            if (!binary) return@repeat
            if (key.startsWith("Cover Art", ignoreCase = true)) {
                val nameEnd = value.indexOfFirst { it.toInt() == 0 }
                report.checkPicture(value.copyOfRange(nameEnd + 1, value.size), "APE item '$key'")
            } else {
                report.add(IntegrityIssue.EMBEDDED_FILE, "APE item '$key' (${Report.sizeLabel(valueSize)})")
                report.scanForCode(value, "APE item '$key'")
            }
        }
    }

    /** Lyrics3 v2: text fields with a six-digit size just before "LYRICS200". */
    private fun lyrics3(reader: BinaryReader, end: Long): Long {
        val digits = String(reader.peek(end - LYRICS3_END, 6), Charsets.ISO_8859_1)
        val size = digits.toLongOrNull() ?: throw MalformedAudioException("Lyrics3 size isn't a number")
        return (end - LYRICS3_END - size).takeIf { it >= 0 } ?: throw MalformedAudioException("Lyrics3 tag runs past the start")
    }

    private fun id3Footer(reader: BinaryReader, end: Long): Long {
        val footer = reader.peek(end - ID3_FOOTER, ID3_FOOTER)
        val size = (6..9).fold(0L) { acc, i -> (acc shl 7) or (footer[i].toLong() and 0x7F) }
        return (end - size - 2 * ID3_FOOTER).takeIf { it >= 0 } ?: throw MalformedAudioException("ID3 footer runs past the start")
    }

    private const val MAX_FOOTERS = 4
    private const val ID3V1 = 128L
    private const val APE_FOOTER = 32
    private const val APE_HAS_HEADER = 0x80000000L
    private const val MAX_APE_ITEMS = 1000L
    private const val LYRICS3_END = 15L
    private const val ID3_FOOTER = 10
}
