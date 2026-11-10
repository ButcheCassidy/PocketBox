// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.ArtSource
import io.github.butchecassidy.pocketbox.domain.model.Track

/** Conversions between library tracks and Media3 media items, and the IDs used in the browse tree. */
object MediaItems {
    private const val TRACK_PREFIX = "t:"
    const val EXTRA_MANUAL = "pocketbox.manual"
    const val EXTRA_ART_URI = "pocketbox.art.uri"
    const val EXTRA_ART_SOURCE = "pocketbox.art.source"
    const val EXTRA_ART_VERSION = "pocketbox.art.version"

    // Artwork is addressed with a private scheme so no other app ever tries to open the real file.
    private const val ART_SCHEME = "pocketbox-art"

    fun trackMediaId(trackId: Long) = "$TRACK_PREFIX$trackId"

    fun trackId(mediaId: String?): Long? =
        mediaId?.takeIf { it.startsWith(TRACK_PREFIX) }?.substring(TRACK_PREFIX.length)?.toLongOrNull()

    /** What a controller sends: just the ID (and whether the user queued it by hand). */
    fun request(trackId: Long, manual: Boolean = false): MediaItem = MediaItem.Builder()
        .setMediaId(trackMediaId(trackId))
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(manualExtras(manual)).build())
        .build()

    fun isManual(item: MediaItem): Boolean =
        item.mediaMetadata.extras?.getBoolean(EXTRA_MANUAL) == true ||
            item.requestMetadata.extras?.getBoolean(EXTRA_MANUAL) == true

    /** The full, playable item built by the service from the library. */
    fun playable(track: Track, manual: Boolean): MediaItem = MediaItem.Builder()
        .setMediaId(trackMediaId(track.id))
        .setUri(track.uri)
        .setMediaMetadata(metadata(track, manual, isPlayable = true))
        .build()

    fun metadata(track: Track, manual: Boolean = false, isPlayable: Boolean = true): MediaMetadata {
        val extras = manualExtras(manual)
        track.art?.let {
            extras.putString(EXTRA_ART_URI, it.uri)
            extras.putInt(EXTRA_ART_SOURCE, it.source.ordinal)
            extras.putLong(EXTRA_ART_VERSION, it.version)
        }
        return MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setAlbumArtist(track.albumArtist)
            .setGenre(track.genre)
            .setComposer(track.composer)
            .setTrackNumber(track.trackNumber)
            .setDiscNumber(track.discNumber)
            .setRecordingYear(track.year)
            .setDurationMs(track.durationMs.takeIf { it > 0 })
            .setArtworkUri(track.art?.let(::artworkUri))
            .setIsPlayable(isPlayable)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
    }

    /** Art reference carried in an item's extras, for the queue and mini player. */
    fun artRef(metadata: MediaMetadata): ArtRef? {
        val extras = metadata.extras ?: return null
        val uri = extras.getString(EXTRA_ART_URI) ?: return null
        val source = ArtSource.entries.getOrNull(extras.getInt(EXTRA_ART_SOURCE, -1)) ?: return null
        return ArtRef(uri, source, extras.getLong(EXTRA_ART_VERSION))
    }

    fun artworkUri(art: ArtRef): Uri = Uri.Builder()
        .scheme(ART_SCHEME)
        .authority("art")
        .appendQueryParameter("u", art.uri)
        .appendQueryParameter("s", art.source.ordinal.toString())
        .appendQueryParameter("v", art.version.toString())
        .build()

    fun parseArtworkUri(uri: Uri): ArtRef? {
        if (uri.scheme != ART_SCHEME) return null
        val target = uri.getQueryParameter("u") ?: return null
        val source = ArtSource.entries.getOrNull(uri.getQueryParameter("s")?.toIntOrNull() ?: -1) ?: return null
        return ArtRef(target, source, uri.getQueryParameter("v")?.toLongOrNull() ?: 0)
    }

    /** A folder-like node in the system media browser. */
    fun browsable(id: String, title: String, subtitle: String? = null, art: ArtRef? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art?.let(::artworkUri))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()

    private fun manualExtras(manual: Boolean) = Bundle().apply { putBoolean(EXTRA_MANUAL, manual) }
}
