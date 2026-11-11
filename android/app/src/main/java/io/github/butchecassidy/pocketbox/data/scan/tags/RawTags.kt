// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan.tags

import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import java.util.Locale

/**
 * What a reader should load besides the normal tags and stream info. [inspectPictures] makes readers
 * load every embedded picture (not just the cover) so the integrity check can look at each one.
 */
data class ReadOptions(val wantPicture: Boolean = false, val wantLyrics: Boolean = false, val inspectPictures: Boolean = false) {
    companion object {
        val SCAN = ReadOptions()
        val PICTURE = ReadOptions(wantPicture = true)
        val LYRICS = ReadOptions(wantLyrics = true)
        val INSPECT = ReadOptions(inspectPictures = true)
    }
}

/** Sees every picture and text value a reader finds, before any trimming (used by the integrity check). */
interface TagObserver {
    fun onPicture(data: ByteArray)
    fun onText(key: String, value: String)
}

/** An embedded picture. [type] follows the ID3/FLAC picture types (3 = front cover). */
class EmbeddedPicture(val type: Int, val data: ByteArray)

/**
 * Tags collected from any format, under one set of canonical (Vorbis-style) names, with hard caps on
 * value length and count so a hostile file can't exhaust memory.
 */
class RawTags(private val options: ReadOptions = ReadOptions.SCAN, private val observer: TagObserver? = null) {
    private val values = LinkedHashMap<String, MutableList<String>>()

    var hasPicture: Boolean = false
        private set
    var hasLyrics: Boolean = false
        private set
    var picture: EmbeddedPicture? = null
        private set

    val wantsPicture: Boolean get() = options.inspectPictures || (options.wantPicture && picture?.type != FRONT_COVER)
    val wantsLyrics: Boolean get() = options.wantLyrics

    fun add(rawKey: String, rawValue: String) {
        val key = canonicalKey(rawKey)
        observer?.onText(key, rawValue)
        if (key == LYRICS) hasLyrics = true
        if (key == LYRICS && !options.wantLyrics) return
        val limit = if (key == LYRICS) MAX_LYRICS_LENGTH else MAX_VALUE_LENGTH
        // Strip byte-order marks and the NUL padding many taggers leave behind.
        val value = rawValue.replace("\uFEFF", "").trim { it.isWhitespace() || it == '\u0000' }.take(limit)
        if (value.isEmpty()) return
        val list = values.getOrPut(key) { ArrayList(1) }
        if (list.size < MAX_VALUES_PER_KEY) list += value
    }

    /** Marks that the file has lyrics without loading them (scanning). */
    fun markLyrics() {
        hasLyrics = true
    }

    /** Marks that the file has a picture; keeps it if pictures were requested, preferring the front cover. */
    fun offerPicture(type: Int, data: ByteArray?) {
        hasPicture = true
        if (data == null || data.isEmpty()) return
        observer?.onPicture(data)
        if (!options.wantPicture) return
        val current = picture
        if (current == null || (type == FRONT_COVER && current.type != FRONT_COVER)) {
            picture = EmbeddedPicture(type, data)
        }
    }

    fun first(key: String): String? = values[key]?.firstOrNull()
    fun all(key: String): List<String> = values[key].orEmpty()
    fun has(key: String): Boolean = values.containsKey(key)

    companion object {
        const val TITLE = "TITLE"
        const val ARTIST = "ARTIST"
        const val ALBUM = "ALBUM"
        const val ALBUM_ARTIST = "ALBUMARTIST"
        const val GENRE = "GENRE"
        const val COMPOSER = "COMPOSER"
        const val DATE = "DATE"
        const val TRACK_NUMBER = "TRACKNUMBER"
        const val TRACK_TOTAL = "TRACKTOTAL"
        const val DISC_NUMBER = "DISCNUMBER"
        const val TITLE_SORT = "TITLESORT"
        const val ARTIST_SORT = "ARTISTSORT"
        const val ALBUM_SORT = "ALBUMSORT"
        const val ALBUM_ARTIST_SORT = "ALBUMARTISTSORT"
        const val RELEASE_TYPE = "RELEASETYPE"
        const val COMPILATION = "COMPILATION"
        const val LYRICS = "LYRICS"
        const val RG_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
        const val RG_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
        const val RG_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
        const val RG_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"
        const val FRONT_COVER = 3

        private const val MAX_VALUE_LENGTH = 4 * 1024
        private const val MAX_LYRICS_LENGTH = 512 * 1024
        private const val MAX_VALUES_PER_KEY = 16

        private val aliases = mapOf(
            "ALBUM_ARTIST" to ALBUM_ARTIST,
            "ALBUM ARTIST" to ALBUM_ARTIST,
            "BAND" to ALBUM_ARTIST,
            "YEAR" to DATE,
            "TOTALTRACKS" to TRACK_TOTAL,
            "DISC" to DISC_NUMBER,
            "MUSICBRAINZ_ALBUMTYPE" to RELEASE_TYPE,
            "MUSICBRAINZ_ALBUM_TYPE" to RELEASE_TYPE,
            "RELEASE_TYPE" to RELEASE_TYPE,
            "UNSYNCEDLYRICS" to LYRICS,
            "UNSYNCED_LYRICS" to LYRICS,
            "SYNCEDLYRICS" to LYRICS,
            "ALBUMARTIST_SORT" to ALBUM_ARTIST_SORT,
        )

        /** Upper-case, spaces to underscores, then common aliases folded together. */
        fun canonicalKey(raw: String): String {
            val upper = raw.trim().uppercase(Locale.ROOT)
            return aliases[upper] ?: aliases[upper.replace(' ', '_')] ?: upper.replace(' ', '_')
        }
    }
}

/** Stream facts read from the file's own headers. Null means the file doesn't say. */
data class StreamInfo(
    val codec: AudioCodec,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateBps: Int? = null,
    val bitrateMode: BitrateMode = BitrateMode.UNKNOWN,
    val channels: Int? = null,
    val durationMs: Long? = null,
)

/** Result of reading one audio file. */
class ParsedAudio(val stream: StreamInfo, val tags: RawTags)

/** Average bitrate in bits per second from a byte count and duration, or null if either is unknown. */
internal fun averageBitrate(audioBytes: Long, durationMs: Long?): Int? {
    if (durationMs == null || durationMs <= 0 || audioBytes <= 0) return null
    return (audioBytes * 8_000 / durationMs).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
