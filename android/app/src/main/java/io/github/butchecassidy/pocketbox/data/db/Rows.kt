// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.db

import androidx.room.Embedded
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.ArtSource
import io.github.butchecassidy.pocketbox.domain.model.Artist
import io.github.butchecassidy.pocketbox.domain.model.AudioCodec
import io.github.butchecassidy.pocketbox.domain.model.AudioQuality
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import io.github.butchecassidy.pocketbox.domain.model.QualityClass
import io.github.butchecassidy.pocketbox.domain.model.ReleaseType
import io.github.butchecassidy.pocketbox.domain.model.ReplayGain
import io.github.butchecassidy.pocketbox.domain.model.Track

/** Every track column except the sort-key blobs, which lists never need in memory. */
const val TRACK_COLUMNS = "tracks.id, tracks.documentUri, tracks.title, tracks.artist, tracks.album, " +
    "tracks.albumArtist, tracks.albumKey, tracks.artistKey, tracks.genre, tracks.composer, tracks.year, " +
    "tracks.trackNumber, tracks.discNumber, tracks.fileName, tracks.folderPath, tracks.fileSize, " +
    "tracks.durationMs, tracks.codec, tracks.sampleRate, tracks.bitDepth, tracks.bitrate, tracks.bitrateMode, " +
    "tracks.channels, tracks.rgTrackGain, tracks.rgTrackPeak, tracks.rgAlbumGain, tracks.rgAlbumPeak, " +
    "tracks.artUri, tracks.artSource, tracks.artVersion, tracks.lrcUri, tracks.hasEmbeddedLyrics, " +
    "tracks.titleLetter, tracks.dateAdded, tracks.isAudiobook"

data class TrackRow(
    val id: Long,
    val documentUri: String,
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
    val fileSize: Long,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val bitrate: Int?,
    val bitrateMode: Int,
    val channels: Int?,
    val rgTrackGain: Float?,
    val rgTrackPeak: Float?,
    val rgAlbumGain: Float?,
    val rgAlbumPeak: Float?,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long,
    val lrcUri: String?,
    val hasEmbeddedLyrics: Boolean,
    val titleLetter: String,
    val dateAdded: Long,
    val isAudiobook: Boolean,
) {
    fun toTrack() = Track(
        id = id,
        uri = documentUri,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        albumKey = albumKey,
        artistKey = artistKey,
        genre = genre,
        composer = composer,
        year = year,
        trackNumber = trackNumber,
        discNumber = discNumber,
        fileName = fileName,
        folderPath = folderPath,
        quality = AudioQuality(
            codec = AudioCodec.fromName(codec),
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            bitrateBps = bitrate,
            bitrateMode = BitrateMode.entries.getOrElse(bitrateMode) { BitrateMode.UNKNOWN },
            channels = channels,
            fileSizeBytes = fileSize,
            durationMs = durationMs,
        ),
        replayGain = ReplayGain(rgTrackGain, rgTrackPeak, rgAlbumGain, rgAlbumPeak),
        art = artRef(artUri, artSource, artVersion),
        lyricsUri = lrcUri,
        hasEmbeddedLyrics = hasEmbeddedLyrics,
        dateAddedMs = dateAdded,
        sortLetter = titleLetter,
        isAudiobook = isAudiobook,
    )
}

/** An album row joined with the user's Album / Single / EP override, if any. */
data class AlbumRow(
    @Embedded val album: AlbumEntity,
    val overrideType: Int?,
) {
    fun toAlbum(): Album {
        val auto = ReleaseType.fromOrdinal(album.autoReleaseType) ?: ReleaseType.ALBUM
        val override = ReleaseType.fromOrdinal(overrideType)
        return Album(
            key = album.albumKey,
            title = album.title,
            albumArtist = album.albumArtist,
            artistKey = album.artistKey,
            year = album.year,
            trackCount = album.trackCount,
            discCount = album.discCount,
            durationMs = album.durationMs,
            art = artRef(album.artUri, album.artSource, album.artVersion),
            qualityClass = album.bestQuality?.let { QualityClass.entries.getOrNull(it) },
            releaseType = override ?: auto,
            isTypeOverridden = override != null,
            dateAddedMs = album.dateAdded,
        )
    }
}

fun ArtistEntity.toArtist() = Artist(
    key = artistKey,
    name = name,
    albumCount = albumCount,
    trackCount = trackCount,
    art = artRef(artUri, artSource, artVersion),
)

internal fun artRef(uri: String?, source: Int?, version: Long): ArtRef? {
    if (uri == null) return null
    val artSource = ArtSource.entries.getOrNull(source ?: -1) ?: return null
    return ArtRef(uri, artSource, version)
}

/** What the scanner needs to know about an already-indexed file to decide whether to re-read it. */
data class ScanIndexRow(
    val id: Long,
    val documentUri: String,
    val lastModified: Long,
    val fileSize: Long,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long,
    val lrcUri: String?,
    val dateAdded: Long,
)

/** Per-track fields the album rebuild needs to resolve album artists. */
data class GroupingRow(
    val id: Long,
    val albumKey: String,
    val artist: String,
    val albumArtist: String,
    val albumArtistTag: String?,
    val albumArtistSortTag: String?,
    val isCompilation: Boolean,
)

/** One album's totals, computed by SQL during the rebuild. */
data class AlbumAggregate(
    val albumKey: String,
    val title: String,
    val albumArtist: String,
    val artistKey: String,
    val albumSortTag: String?,
    val albumArtistSortTag: String?,
    val year: Int?,
    val trackCount: Int,
    val discCount: Int,
    val durationMs: Long,
    val bestQuality: Int?,
    val releaseTypeTag: String?,
    val dateAdded: Long,
)

/** The first picture found for an album, in disc/track order. */
data class AlbumArtRow(val albumKey: String, val artUri: String, val artSource: Int, val artVersion: Long)

/** A folder that directly contains songs. */
data class FolderRow(val parentUri: String, val folderPath: String, val trackCount: Int)

/** A genre / year / composer value and how many songs have it. */
data class FacetRow(val value: String, val trackCount: Int)

data class DocumentIdRow(val id: Long, val documentId: String)

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val durationMs: Long,
    val artUri: String?,
    val artSource: Int?,
    val artVersion: Long?,
)
