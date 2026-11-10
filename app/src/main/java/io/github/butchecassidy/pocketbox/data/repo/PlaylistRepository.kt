// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.butchecassidy.pocketbox.data.db.PlaylistEntity
import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.data.db.artRef
import io.github.butchecassidy.pocketbox.data.lyrics.LyricsRepository
import io.github.butchecassidy.pocketbox.data.scan.AudioFiles
import io.github.butchecassidy.pocketbox.domain.model.Playlist
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.domain.playlist.LibraryPath
import io.github.butchecassidy.pocketbox.domain.playlist.M3u
import io.github.butchecassidy.pocketbox.domain.playlist.M3uExportItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Result of importing a playlist file: how many entries matched songs in the library. */
data class ImportResult(val playlistId: Long, val name: String, val matched: Int, val skipped: Int)

/**
 * Playlists, including `.m3u` / `.m3u8` import and export. Imported entries are only ever matched
 * against songs already indexed from the user's granted folders; nothing else is opened.
 */
class PlaylistRepository(
    context: Context,
    db: PocketBoxDatabase,
    private val audioFiles: AudioFiles,
    private val io: CoroutineDispatcher,
) {
    private val resolver = context.applicationContext.contentResolver
    private val dao = db.userDataDao()
    private val libraryDao = db.libraryDao()
    private val scanDao = db.scanDao()

    val playlists: Flow<List<Playlist>> = dao.playlists().map { rows ->
        rows.map { Playlist(it.id, it.name, it.trackCount, it.durationMs, artRef(it.artUri, it.artSource, it.artVersion ?: 0)) }
    }.flowOn(io)

    fun name(id: Long): Flow<String?> = dao.playlist(id).map { it?.name }

    fun tracks(id: Long): Flow<List<Track>> = dao.playlistTracks(id).map { rows -> rows.map { it.toTrack() } }.flowOn(io)

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long = withContext(io) {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = cleanName(name), createdAt = now, updatedAt = now))
        if (trackIds.isNotEmpty()) dao.appendToPlaylist(id, trackIds, now)
        id
    }

    suspend fun rename(id: Long, name: String) = withContext(io) { dao.renamePlaylist(id, cleanName(name), System.currentTimeMillis()) }

    suspend fun delete(id: Long) = withContext(io) { dao.deletePlaylist(id) }

    suspend fun add(id: Long, trackIds: List<Long>) = withContext(io) { dao.appendToPlaylist(id, trackIds, System.currentTimeMillis()) }

    /** Saves a new order (after drag-to-reorder or removing entries). */
    suspend fun setOrder(id: Long, trackIds: List<Long>) = withContext(io) { dao.replaceEntries(id, trackIds, System.currentTimeMillis()) }

    suspend fun import(uri: Uri): ImportResult? = withContext(io) {
        val bytes = audioFiles.readSmallFile(uri, MAX_PLAYLIST_BYTES) ?: return@withContext null
        val entries = M3u.parse(LyricsRepository.decodeText(bytes))
        val baseDir = storageDocumentId(uri)?.let(LibraryPath::parentOf)
        val roots = scanDao.foldersOnce().map { it.rootDocumentId }
        val paths = entries.mapNotNull { entry ->
            LibraryPath.resolve(entry.path, baseDir)?.takeIf { path -> roots.any { LibraryPath.isInside(path, it) } }
        }
        val idByPath = paths.distinct().chunked(SQL_CHUNK)
            .flatMap { libraryDao.idsByDocumentIds(it) }
            .associate { it.documentId to it.id }
        val trackIds = paths.mapNotNull { idByPath[it] }
        val name = displayName(uri)?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported playlist"
        val id = create(name, trackIds)
        ImportResult(id, cleanName(name), trackIds.size, entries.size - trackIds.size)
    }

    /** Writes the playlist to [destination] (from the system "save as" picker) as UTF-8 extended M3U. */
    suspend fun export(id: Long, tracks: List<Track>, destination: Uri): Boolean = withContext(io) {
        val destinationDir = storageDocumentId(destination)?.let(LibraryPath::parentOf)
        val items = tracks.map { track ->
            val documentId = libraryDao.documentId(track.id)
            val path = documentId?.let { doc ->
                destinationDir?.let { LibraryPath.relativize(it, doc) } ?: LibraryPath.toAbsolute(doc)
            } ?: track.fileName
            M3uExportItem(path, track.artist, track.title, track.durationMs)
        }
        runCatching {
            resolver.openOutputStream(destination, "wt")?.use { it.write(M3u.write(items).toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false).also { if (it) dao.touchPlaylist(id, System.currentTimeMillis()) }
    }

    /** `volume:path` for files on shared storage; null for other providers, whose IDs are opaque. */
    private fun storageDocumentId(uri: Uri): String? = runCatching {
        if (uri.authority != EXTERNAL_STORAGE_AUTHORITY) return@runCatching null
        DocumentsContract.getDocumentId(uri)
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun cleanName(name: String) = name.replace(Regex("[\\p{Cntrl}]"), " ").trim().take(MAX_NAME).ifEmpty { "Playlist" }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val MAX_PLAYLIST_BYTES = 4 * 1024 * 1024
        const val SQL_CHUNK = 900
        const val MAX_NAME = 100
    }
}
