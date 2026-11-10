// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.model

/** How a release is filed: the Albums tab or the Singles & EPs tab. */
enum class ReleaseType(val label: String) {
    ALBUM("Album"),
    SINGLE("Single"),
    EP("EP"),
    ;

    val isSingleOrEp: Boolean get() = this != ALBUM

    companion object {
        fun fromOrdinal(value: Int?): ReleaseType? = entries.getOrNull(value ?: -1)
    }
}

/** Where a picture comes from: inside the audio file, or an image file next to it. */
enum class ArtSource { EMBEDDED, FOLDER }

/** Points at album art without loading it. [version] changes when the file changes. */
data class ArtRef(val uri: String, val source: ArtSource, val version: Long) {
    /** Stable cache key for this exact picture. */
    val cacheKey: String get() = "${source.ordinal}|$version|$uri"
}

/** ReplayGain values read from tags, in dB (gain) and linear amplitude (peak). */
data class ReplayGain(
    val trackGainDb: Float?,
    val trackPeak: Float?,
    val albumGainDb: Float?,
    val albumPeak: Float?,
) {
    companion object {
        val NONE = ReplayGain(null, null, null, null)
    }
}

/** One song in the library. */
data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val albumKey: String,
    val artistKey: String,
    val genre: String?,
    val composer: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val fileName: String,
    val folderPath: String,
    val quality: AudioQuality,
    val replayGain: ReplayGain,
    val art: ArtRef?,
    val lyricsUri: String?,
    val hasEmbeddedLyrics: Boolean,
    val dateAddedMs: Long,
    /** The letter header this song files under in the A–Z list ("#" for numbers and symbols). */
    val sortLetter: String,
) {
    val durationMs: Long get() = quality.durationMs
}

/** An album, single or EP: tracks sharing an album title and album artist. */
data class Album(
    val key: String,
    val title: String,
    val albumArtist: String,
    val artistKey: String,
    val year: Int?,
    val trackCount: Int,
    val discCount: Int,
    val durationMs: Long,
    val art: ArtRef?,
    val qualityClass: QualityClass?,
    val releaseType: ReleaseType,
    val isTypeOverridden: Boolean,
    val dateAddedMs: Long,
)

/** An album artist (or the artist, when no album artist is tagged). */
data class Artist(
    val key: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    val art: ArtRef?,
)

/** A folder inside one of the user's music folders that directly contains songs. */
data class MusicFolder(
    val uri: String,
    val name: String,
    val path: String,
    val trackCount: Int,
)

/** A music folder the user granted access to in onboarding or Settings. */
data class LibraryRoot(
    val id: Long,
    val treeUri: String,
    val name: String,
    val isAccessible: Boolean,
)

/** A user playlist. */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val durationMs: Long,
    val art: ArtRef?,
)

/** A browsable value such as a genre, year or composer, with how many songs it has. */
data class Facet(val value: String, val trackCount: Int)
