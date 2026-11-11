// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

/**
 * Identifies an audio file by its first bytes (never by its name) and hands it to the right reader.
 * Returns null for formats Pocket Box can't parse itself; the scanner then asks Android instead.
 */
object AudioFileReader {
    fun read(source: ByteSource, options: ReadOptions = ReadOptions.SCAN, observer: TagObserver? = null): ParsedAudio? {
        val reader = BinaryReader(source)
        val tags = RawTags(options, observer)
        val head = reader.peek(0, 12)
        if (head.size < 12) return null

        val stream = when {
            head.startsWithAscii("fLaC") -> FlacReader.read(reader, 0, tags)
            head.startsWithAscii("OggS") -> OggReader.read(reader, tags)
            head.startsWithAscii("ftyp", 4) -> Mp4Reader.read(reader, tags)
            (head.startsWithAscii("RIFF") || head.startsWithAscii("RF64")) && head.startsWithAscii("WAVE", 8) ->
                PcmContainerReader.readWav(reader, tags)
            head.startsWithAscii("FORM") && (head.startsWithAscii("AIFF", 8) || head.startsWithAscii("AIFC", 8)) ->
                PcmContainerReader.readAiff(reader, tags)
            else -> readId3Prefixed(reader, tags) ?: return null
        }
        return ParsedAudio(stream, tags)
    }

    /** MP3 (with or without ID3v2), or a FLAC file that a tagger prefixed with ID3. */
    private fun readId3Prefixed(reader: BinaryReader, tags: RawTags): StreamInfo? {
        var audioStart = 0L
        // Some files carry more than one ID3v2 tag in a row; read them all.
        for (i in 0 until 4) {
            val length = Id3Reader.read(reader, audioStart, tags)
            if (length == 0L) break
            audioStart += length
        }
        val next = reader.peek(audioStart, 4)
        if (next.startsWithAscii("fLaC")) return FlacReader.read(reader, audioStart, tags)
        if (audioStart == 0L && !Mp3Reader.startsWithFrame(next)) return null
        val hasV1 = Id3Reader.readV1(reader, tags)
        return Mp3Reader.read(reader, audioStart, hasV1)
    }
}
