// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode

/** Reads MP4 / M4A audio: AAC, ALAC, FLAC-in-MP4 and Opus-in-MP4 stream info, plus iTunes-style tags. */
object Mp4Reader {
    private const val MAX_DEPTH = 12
    private const val MAX_ATOMS = 20_000
    private const val MAX_ITEM = 1024 * 1024
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "udta", "ilst")
    private val textItems = mapOf(
        "©nam" to RawTags.TITLE,
        "©ART" to RawTags.ARTIST,
        "aART" to RawTags.ALBUM_ARTIST,
        "©alb" to RawTags.ALBUM,
        "©gen" to RawTags.GENRE,
        "©wrt" to RawTags.COMPOSER,
        "©day" to RawTags.DATE,
        "sonm" to RawTags.TITLE_SORT,
        "soar" to RawTags.ARTIST_SORT,
        "soal" to RawTags.ALBUM_SORT,
        "soaa" to RawTags.ALBUM_ARTIST_SORT,
        "©lyr" to RawTags.LYRICS,
    )

    private class Atom(val type: String, val start: Long, val bodyStart: Long, val end: Long)

    /** Mutable state gathered while walking the atom tree. */
    private class State {
        var stream: StreamInfo? = null
        var mdhdDurationMs: Long? = null
        var mvhdDurationMs: Long? = null
        var inSoundTrack = false
        var mdatBytes = 0L
        var maxBitrate = 0L
        var avgBitrate = 0L
        var atoms = 0
    }

    fun read(reader: BinaryReader, tags: RawTags): StreamInfo {
        val state = State()
        walk(reader, 0, reader.size, "", 0, tags, state)
        val base = state.stream ?: throw MalformedAudioException("No audio track")
        val durationMs = state.mdhdDurationMs ?: state.mvhdDurationMs
        val stated = state.avgBitrate.takeIf { it > 0 }?.toInt()
        val mode = when {
            base.codec.isLossless == true -> BitrateMode.AVERAGE
            stated != null && state.maxBitrate == state.avgBitrate -> BitrateMode.CONSTANT
            stated != null && state.maxBitrate > state.avgBitrate -> BitrateMode.VARIABLE
            else -> BitrateMode.AVERAGE
        }
        val bitrate = if (mode == BitrateMode.CONSTANT) stated else averageBitrate(state.mdatBytes, durationMs) ?: stated
        return base.copy(durationMs = durationMs, bitrateBps = bitrate, bitrateMode = mode)
    }

    private fun readAtom(reader: BinaryReader, at: Long, parentEnd: Long): Atom? {
        if (at + 8 > parentEnd) return null
        reader.position = at
        var size = reader.u32be()
        val type = reader.fourCc()
        var header = 8L
        if (size == 1L) {
            size = reader.u64be()
            header = 16
        } else if (size == 0L) {
            size = parentEnd - at
        }
        if (size < header || at + size > parentEnd) return null
        return Atom(type, at, at + header, at + size)
    }

    private fun walk(
        reader: BinaryReader,
        from: Long,
        to: Long,
        parent: String,
        depth: Int,
        tags: RawTags,
        state: State,
    ) {
        if (depth > MAX_DEPTH) return
        var at = from
        while (true) {
            if (++state.atoms > MAX_ATOMS) return
            val atom = readAtom(reader, at, to) ?: return
            when {
                parent == "ilst" -> runCatching { readItem(reader, atom, tags) }
                atom.type in containers -> {
                    if (atom.type == "trak") state.inSoundTrack = false
                    walk(reader, atom.bodyStart, atom.end, atom.type, depth + 1, tags, state)
                }
                atom.type == "meta" ->
                    walk(reader, metaChildrenStart(reader, atom), atom.end, "meta", depth + 1, tags, state)
                atom.type == "mdat" -> state.mdatBytes += atom.end - atom.bodyStart
                atom.type == "mvhd" -> state.mvhdDurationMs = readDuration(reader, atom)
                atom.type == "mdhd" -> if (state.stream == null) state.mdhdDurationMs = readDuration(reader, atom)
                atom.type == "hdlr" -> readHandler(reader, atom, state)
                atom.type == "stsd" ->
                    if (state.inSoundTrack && state.stream == null) readSampleDescription(reader, atom, state)
            }
            at = atom.end
        }
    }

    /** 'meta' is a full box in MP4 but a plain container in some QuickTime files. */
    private fun metaChildrenStart(reader: BinaryReader, atom: Atom): Long {
        val probe = reader.peek(atom.bodyStart + 4, 4)
        return if (probe.startsWithAscii("hdlr")) atom.bodyStart else atom.bodyStart + 4
    }

    private fun readDuration(reader: BinaryReader, atom: Atom): Long? {
        reader.position = atom.bodyStart
        val version = reader.u8()
        reader.skip(3)
        val (timescale, duration) = if (version == 1) {
            reader.skip(16)
            reader.u32be() to reader.u64be()
        } else {
            reader.skip(8)
            reader.u32be() to reader.u32be()
        }
        if (timescale <= 0 || duration <= 0 || duration == 0xFFFFFFFFL) return null
        return duration * 1000 / timescale
    }

    private fun readHandler(reader: BinaryReader, atom: Atom, state: State) {
        reader.position = atom.bodyStart + 8
        val handler = reader.fourCc()
        if (handler == "soun" && state.stream == null) state.inSoundTrack = true
    }

    private fun readSampleDescription(reader: BinaryReader, atom: Atom, state: State) {
        val entry = readAtom(reader, atom.bodyStart + 8, atom.end) ?: return
        reader.position = entry.bodyStart + 8
        val version = reader.u16be()
        reader.skip(6)
        var channels = reader.u16be()
        var bitDepth = reader.u16be()
        reader.skip(4)
        var sampleRate = (reader.u32be() ushr 16).toInt()
        var childrenStart = entry.bodyStart + 28
        when (version) {
            1 -> childrenStart += 16
            2 -> {
                reader.skip(4)
                sampleRate = Double.fromBits(reader.u64be()).toInt()
                channels = reader.u32be().toInt()
                reader.skip(4)
                bitDepth = reader.u32be().toInt()
                childrenStart += 36
            }
        }
        val codec = when (entry.type) {
            "mp4a" -> AudioCodec.AAC
            "alac" -> AudioCodec.ALAC
            "fLaC" -> AudioCodec.FLAC
            "Opus" -> AudioCodec.OPUS
            ".mp3" -> AudioCodec.MP3
            else -> AudioCodec.UNKNOWN
        }
        var info = StreamInfo(
            codec = codec,
            sampleRateHz = sampleRate.takeIf { it > 0 },
            bitDepth = if (codec.isLossless == true) bitDepth.takeIf { it > 0 } else null,
            channels = channels.takeIf { it > 0 },
        )
        state.stream = readCodecBoxes(reader, childrenStart, entry.end, info, state, depth = 0)
    }

    /** Codec configuration boxes inside a sample entry (QuickTime files wrap them in a 'wave' box). */
    private fun readCodecBoxes(
        reader: BinaryReader,
        from: Long,
        to: Long,
        initial: StreamInfo,
        state: State,
        depth: Int,
    ): StreamInfo {
        var info = initial
        var at = from
        while (depth <= 2) {
            val child = readAtom(reader, at, to) ?: break
            info = when (child.type) {
                "esds" -> readEsds(reader, child, info, state)
                "alac" -> readAlacConfig(reader, child, info)
                "dfLa" -> readDfLa(reader, child, info)
                "wave" -> readCodecBoxes(reader, child.bodyStart, child.end, info, state, depth + 1)
                else -> info
            }
            at = child.end
        }
        return info
    }

    private fun readEsds(reader: BinaryReader, atom: Atom, info: StreamInfo, state: State): StreamInfo {
        reader.position = atom.bodyStart + 4
        var result = info
        while (reader.position < atom.end) {
            val tag = reader.u8()
            var length = 0
            for (i in 0 until 4) {
                val b = reader.u8()
                length = (length shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }
            when (tag) {
                0x03 -> { // ES descriptor
                    reader.skip(2)
                    val flags = reader.u8()
                    if (flags and 0x80 != 0) reader.skip(2)
                    if (flags and 0x40 != 0) reader.skip(reader.u8().toLong())
                    if (flags and 0x20 != 0) reader.skip(2)
                }
                0x04 -> { // decoder config
                    val objectType = reader.u8()
                    reader.skip(4)
                    state.maxBitrate = reader.u32be()
                    state.avgBitrate = reader.u32be()
                    if (objectType == 0x6B || objectType == 0x69) result = result.copy(codec = AudioCodec.MP3)
                }
                0x05 -> { // AudioSpecificConfig
                    val objectType = reader.u8() ushr 3
                    if (objectType == 5 || objectType == 29) result = result.copy(codec = AudioCodec.HE_AAC)
                    return result
                }
                else -> reader.skip(length.toLong())
            }
        }
        return result
    }

    /** ALAC's own config holds the true sample rate (the sample entry can't store rates above 65535 Hz). */
    private fun readAlacConfig(reader: BinaryReader, atom: Atom, info: StreamInfo): StreamInfo {
        if (atom.end - atom.bodyStart < 28) return info
        reader.position = atom.bodyStart + 4 + 5
        val bitDepth = reader.u8()
        reader.skip(3)
        val channels = reader.u8()
        reader.skip(10)
        val sampleRate = reader.u32be().toInt()
        return info.copy(
            bitDepth = bitDepth.takeIf { it > 0 } ?: info.bitDepth,
            channels = channels.takeIf { it > 0 } ?: info.channels,
            sampleRateHz = sampleRate.takeIf { it > 0 } ?: info.sampleRateHz,
        )
    }

    /** FLAC-in-MP4: the dfLa box carries a normal STREAMINFO block. */
    private fun readDfLa(reader: BinaryReader, atom: Atom, info: StreamInfo): StreamInfo {
        reader.position = atom.bodyStart + 4 + 4 + 10
        val packed = reader.u64be()
        return info.copy(
            sampleRateHz = (packed ushr 44).toInt().takeIf { it > 0 },
            channels = ((packed ushr 41) and 0x7).toInt() + 1,
            bitDepth = ((packed ushr 36) and 0x1F).toInt() + 1,
        )
    }

    private fun readItem(reader: BinaryReader, item: Atom, tags: RawTags) {
        var name: String? = null
        var at = item.bodyStart
        while (true) {
            val child = readAtom(reader, at, item.end) ?: return
            val length = child.end - child.bodyStart
            when (child.type) {
                "name" -> if (length in 5..MAX_ITEM) {
                    reader.position = child.bodyStart + 4
                    name = String(reader.readFully((length - 4).toInt()), Charsets.UTF_8)
                }
                "data" -> readData(reader, item.type, name, child, length, tags)
            }
            at = child.end
        }
    }

    private fun readData(reader: BinaryReader, itemType: String, name: String?, data: Atom, length: Long, tags: RawTags) {
        if (length < 8) return
        val payload = length - 8
        reader.position = data.bodyStart
        val dataType = (reader.u32be() and 0xFFFFFF).toInt()
        reader.skip(4)
        when (itemType) {
            "covr" -> {
                if (!tags.wantsPicture || payload > BinaryReader.MAX_SINGLE_READ) return tags.offerPicture(0, null)
                tags.offerPicture(RawTags.FRONT_COVER, reader.readFully(payload.toInt()))
            }
            "trkn", "disk" -> if (payload >= 6) {
                reader.skip(2)
                val number = reader.u16be()
                val total = reader.u16be()
                if (itemType == "trkn") {
                    if (number > 0) tags.add(RawTags.TRACK_NUMBER, number.toString())
                    if (total > 0) tags.add(RawTags.TRACK_TOTAL, total.toString())
                } else if (number > 0) {
                    tags.add(RawTags.DISC_NUMBER, number.toString())
                }
            }
            "cpil" -> if (payload >= 1) tags.add(RawTags.COMPILATION, reader.u8().toString())
            "gnre" -> if (payload >= 2) Id3Genres.byIndex(reader.u16be() - 1)?.let { tags.add(RawTags.GENRE, it) }
            "©lyr" -> if (!tags.wantsLyrics) tags.markLyrics() else readText(reader, payload, dataType)
                ?.let { tags.add(RawTags.LYRICS, it) }
            "----" -> if (name != null) readText(reader, payload, dataType)?.let { tags.add(name, it) }
            else -> textItems[itemType]?.let { key -> readText(reader, payload, dataType)?.let { tags.add(key, it) } }
        }
    }

    private fun readText(reader: BinaryReader, payload: Long, dataType: Int): String? {
        if (payload <= 0 || payload > MAX_ITEM) return null
        val bytes = reader.readFully(payload.toInt())
        return when (dataType) {
            1, 0 -> String(bytes, Charsets.UTF_8)
            2 -> String(bytes, Charsets.UTF_16BE)
            else -> null
        }
    }
}
