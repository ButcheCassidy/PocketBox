// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import io.github.butchecassidy.pocketbox.data.repo.HistoryRepository
import io.github.butchecassidy.pocketbox.data.repo.LibraryRepository
import io.github.butchecassidy.pocketbox.data.repo.PlaylistRepository
import io.github.butchecassidy.pocketbox.domain.model.AlbumSort
import io.github.butchecassidy.pocketbox.domain.model.SongSort
import io.github.butchecassidy.pocketbox.domain.model.Track
import kotlinx.coroutines.flow.first

/**
 * The library as the system media browser sees it (lock screen, Bluetooth head units, connected
 * devices). Only trusted controllers may browse; see PlaybackService.
 */
class LibraryBrowseTree(
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val history: HistoryRepository,
) {
    val root: MediaItem = MediaItems.browsable(ROOT, "Pocket Box")

    suspend fun item(id: String): MediaItem? {
        if (id == ROOT) return root
        MediaItems.trackId(id)?.let { trackId ->
            return library.tracksByIds(listOf(trackId)).firstOrNull()?.let { MediaItems.playable(it, manual = false) }
        }
        return children(ROOT).firstOrNull { it.mediaId == id }
    }

    /** Children of a node, already paged by Media3's caller. */
    suspend fun children(id: String): List<MediaItem> = when {
        id == ROOT -> listOf(
            MediaItems.browsable(ALBUMS, "Albums"),
            MediaItems.browsable(ARTISTS, "Artists"),
            MediaItems.browsable(PLAYLISTS, "Playlists"),
            MediaItems.browsable(SONGS, "Songs"),
            MediaItems.browsable(GENRES, "Genres"),
            MediaItems.browsable(RECENT, "Recently played"),
        )
        id == ALBUMS -> library.albums(AlbumSort.ARTIST_YEAR).first().map {
            MediaItems.browsable(ALBUM_PREFIX + it.key, it.title, it.albumArtist, it.art)
        }
        id == ARTISTS -> library.artists.first().map {
            MediaItems.browsable(ARTIST_PREFIX + it.key, it.name, null, it.art)
        }
        id == PLAYLISTS -> playlists.playlists.first().map {
            MediaItems.browsable(PLAYLIST_PREFIX + it.id, it.name, null, it.art)
        }
        id == GENRES -> library.genres.first().map {
            MediaItems.browsable(GENRE_PREFIX + Uri.encode(it.value), it.value)
        }
        id == SONGS -> tracks(library.songs(SongSort.TITLE).first())
        id == RECENT -> tracks(history.recentlyPlayedTracks().first())
        id.startsWith(ALBUM_PREFIX) -> tracks(library.albumTracks(id.removePrefix(ALBUM_PREFIX)).first())
        id.startsWith(ARTIST_PREFIX) -> tracks(library.artistTracks(id.removePrefix(ARTIST_PREFIX)).first())
        id.startsWith(PLAYLIST_PREFIX) ->
            id.removePrefix(PLAYLIST_PREFIX).toLongOrNull()?.let { tracks(playlists.tracks(it).first()) }.orEmpty()
        id.startsWith(GENRE_PREFIX) -> tracks(library.genreTracks(Uri.decode(id.removePrefix(GENRE_PREFIX))).first())
        else -> emptyList()
    }

    private fun tracks(list: List<Track>) = list.map { MediaItems.playable(it, manual = false) }

    companion object {
        const val ROOT = "root"
        private const val ALBUMS = "albums"
        private const val ARTISTS = "artists"
        private const val PLAYLISTS = "playlists"
        private const val SONGS = "songs"
        private const val GENRES = "genres"
        private const val RECENT = "recent"
        private const val ALBUM_PREFIX = "album:"
        private const val ARTIST_PREFIX = "artist:"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val GENRE_PREFIX = "genre:"
    }
}
