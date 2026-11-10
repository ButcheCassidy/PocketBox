// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import io.github.butchecassidy.pocketbox.data.db.AlbumRow
import io.github.butchecassidy.pocketbox.data.db.FavoriteEntity
import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.data.db.ReleaseOverrideEntity
import io.github.butchecassidy.pocketbox.data.db.TrackRow
import io.github.butchecassidy.pocketbox.data.db.toArtist
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.AlbumSort
import io.github.butchecassidy.pocketbox.domain.model.Artist
import io.github.butchecassidy.pocketbox.domain.model.Facet
import io.github.butchecassidy.pocketbox.domain.model.MusicFolder
import io.github.butchecassidy.pocketbox.domain.model.ReleaseType
import io.github.butchecassidy.pocketbox.domain.model.SongSort
import io.github.butchecassidy.pocketbox.domain.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The library as domain models. All mapping runs off the main thread. */
class LibraryRepository(db: PocketBoxDatabase, private val cpu: CoroutineDispatcher) {
    private val dao = db.libraryDao()
    private val userDao = db.userDataDao()

    // ---- Songs ----

    fun songs(sort: SongSort): Flow<List<Track>> = when (sort) {
        SongSort.TITLE -> dao.songsByTitle()
        SongSort.ARTIST -> dao.songsByArtist()
        SongSort.RECENTLY_ADDED -> dao.songsByDateAdded()
    }.tracks()

    val songCount: Flow<Int> = dao.songCount()

    fun track(id: Long): Flow<Track?> = dao.track(id).map { it?.toTrack() }.flowOn(cpu)

    /** Tracks in exactly the order of [ids]; ids that no longer exist are dropped. */
    suspend fun tracksByIds(ids: List<Long>): List<Track> = withContext(cpu) {
        val byId = ids.distinct().chunked(SQL_CHUNK).flatMap { dao.tracksByIds(it) }.associateBy { it.id }
        ids.mapNotNull { byId[it]?.toTrack() }
    }

    // ---- Albums, singles and EPs ----

    fun albums(sort: AlbumSort): Flow<List<Album>> = releases(sort, listOf(ReleaseType.ALBUM))

    fun singlesAndEps(sort: AlbumSort): Flow<List<Album>> = releases(sort, listOf(ReleaseType.SINGLE, ReleaseType.EP))

    private fun releases(sort: AlbumSort, types: List<ReleaseType>): Flow<List<Album>> {
        val ordinals = types.map { it.ordinal }
        return when (sort) {
            AlbumSort.ARTIST_YEAR -> dao.albumsByArtistYear(ordinals)
            AlbumSort.TITLE -> dao.albumsByTitle(ordinals)
            AlbumSort.YEAR -> dao.albumsByYear(ordinals)
            AlbumSort.RECENTLY_ADDED -> dao.albumsByDateAdded(ordinals)
        }.albums()
    }

    fun album(key: String): Flow<Album?> = dao.album(key).map { it?.toAlbum() }.flowOn(cpu)

    fun albumTracks(key: String): Flow<List<Track>> = dao.albumTracks(key).tracks()

    suspend fun albumsByKeys(keys: List<String>): List<Album> = withContext(cpu) {
        keys.distinct().chunked(SQL_CHUNK).flatMap { dao.albumsByKeys(it) }.map { it.toAlbum() }
    }

    fun recentlyAdded(limit: Int = SHELF_SIZE): Flow<List<Album>> = dao.recentlyAddedAlbums(limit).albums()
    fun recentlyPlayed(limit: Int = SHELF_SIZE): Flow<List<Album>> = dao.recentlyPlayedAlbums(limit).albums()
    fun mostPlayed(limit: Int = SHELF_SIZE): Flow<List<Album>> = dao.mostPlayedAlbums(limit).albums()

    /** The user's per-release classification; null restores the automatic one. */
    suspend fun setReleaseType(albumKey: String, type: ReleaseType?) = withContext(cpu) {
        if (type == null) userDao.clearReleaseOverride(albumKey)
        else userDao.setReleaseOverride(ReleaseOverrideEntity(albumKey, type.ordinal))
    }

    // ---- Artists ----

    val artists: Flow<List<Artist>> = dao.artists().map { list -> list.map { it.toArtist() } }.flowOn(cpu)

    fun artist(key: String): Flow<Artist?> = dao.artist(key).map { it?.toArtist() }.flowOn(cpu)

    suspend fun artistsByKeys(keys: List<String>): List<Artist> = withContext(cpu) {
        keys.distinct().chunked(SQL_CHUNK).flatMap { dao.artistsByKeys(it) }.map { it.toArtist() }
    }

    fun artistReleases(key: String): Flow<List<Album>> = dao.albumsForArtist(key).albums()

    fun artistTracks(key: String): Flow<List<Track>> = dao.artistTracks(key).tracks()

    // ---- Folders and facets ----

    val folders: Flow<List<MusicFolder>> = dao.folders().map { rows ->
        rows.map { MusicFolder(it.parentUri, it.folderPath.substringAfterLast('/'), it.folderPath, it.trackCount) }
    }.flowOn(cpu)

    fun folderTracks(parentUri: String): Flow<List<Track>> = dao.folderTracks(parentUri).tracks()

    val genres: Flow<List<Facet>> = dao.genres().map { rows -> rows.map { Facet(it.value, it.trackCount) } }
    val years: Flow<List<Facet>> = dao.years().map { rows -> rows.map { Facet(it.value, it.trackCount) } }
    val composers: Flow<List<Facet>> = dao.composers().map { rows -> rows.map { Facet(it.value, it.trackCount) } }

    fun genreTracks(genre: String): Flow<List<Track>> = dao.genreTracks(genre).tracks()
    fun yearTracks(year: Int): Flow<List<Track>> = dao.yearTracks(year).tracks()
    fun composerTracks(composer: String): Flow<List<Track>> = dao.composerTracks(composer).tracks()

    // ---- Favourites ----

    val favoriteIds: Flow<Set<Long>> = userDao.favoriteIds().map { it.toSet() }
    val favorites: Flow<List<Track>> = userDao.favoriteTracks().tracks()

    suspend fun setFavorite(trackId: Long, favorite: Boolean) = withContext(cpu) {
        if (favorite) userDao.addFavorite(FavoriteEntity(trackId, System.currentTimeMillis()))
        else userDao.removeFavorite(trackId)
    }

    private fun Flow<List<TrackRow>>.tracks(): Flow<List<Track>> = map { rows -> rows.map { it.toTrack() } }.flowOn(cpu)
    private fun Flow<List<AlbumRow>>.albums(): Flow<List<Album>> = map { rows -> rows.map { it.toAlbum() } }.flowOn(cpu)

    private companion object {
        const val SQL_CHUNK = 900
        const val SHELF_SIZE = 20
    }
}
