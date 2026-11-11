// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.scan

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.withTransaction
import io.github.butchecassidy.pocketbox.data.db.AlbumEntity
import io.github.butchecassidy.pocketbox.data.db.ArtistEntity
import io.github.butchecassidy.pocketbox.data.db.ExcludedFolderEntity
import io.github.butchecassidy.pocketbox.data.db.FolderEntity
import io.github.butchecassidy.pocketbox.data.db.HeldFileEntity
import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.data.db.ScanIndexRow
import io.github.butchecassidy.pocketbox.data.db.TrackEntity
import io.github.butchecassidy.pocketbox.data.prefs.SettingsRepository
import io.github.butchecassidy.pocketbox.data.scan.integrity.IntegrityFinding
import io.github.butchecassidy.pocketbox.domain.model.ArtSource
import io.github.butchecassidy.pocketbox.domain.model.LibraryRoot
import io.github.butchecassidy.pocketbox.domain.release.ReleaseClassifier
import io.github.butchecassidy.pocketbox.domain.sort.SortKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the scanner is doing, for the onboarding progress screen and Settings. */
sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val filesFound: Int, val filesRead: Int, val filesToRead: Int) : ScanState
    /** [heldBack]: new files this scan left out because the integrity check found something in them. */
    data class Finished(val trackCount: Int, val added: Int, val removed: Int, val heldBack: Int) : ScanState
}

/** A file waiting for the user's decision, with what the integrity check found in it. */
data class HeldFile(val documentUri: String, val fileName: String, val folderPath: String, val findings: List<IntegrityFinding>)

/**
 * Indexes the user's music folders into Room. Incremental: a file is only re-read when its size or
 * modification time changed. Files are read in parallel batches on the IO dispatcher and written in
 * transactions. A folder that can't be read is reported, never treated as "all songs deleted".
 *
 * The first time a file is added it goes through the integrity check. A file holding anything besides
 * music, tags, album art and lyrics is held back (listed in [heldFiles]) until the user adds it anyway.
 */
class LibraryScanner(
    private val db: PocketBoxDatabase,
    private val folderAccess: FolderAccess,
    private val audioFiles: AudioFiles,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    private val scanDao = db.scanDao()

    // Reading files is I/O through the storage provider; a few at a time is fastest without starving the UI.
    private val readers = io.limitedParallelism(PARALLEL_READS)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    private val lostFolderIds = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Granted folders, each flagged if access was lost (revoked, moved or deleted). */
    val roots: Flow<List<LibraryRoot>> = combine(scanDao.folders(), lostFolderIds) { folders, lost ->
        folders.map { LibraryRoot(it.id, it.treeUri, it.displayName, isAccessible = it.id !in lost) }
    }

    val excludedFolders: Flow<List<ExcludedFolderEntity>> = scanDao.allExcludedFolders()

    val heldFiles: Flow<List<HeldFile>> = scanDao.heldFilesWaiting().map { rows ->
        rows.map { HeldFile(it.documentUri, it.fileName, it.folderPath, IntegrityFinding.decodeAll(it.findings)) }
    }

    /** The user has seen the "songs held back" notice for the last scan. */
    fun acknowledgeHeldBack() {
        _state.update { if (it is ScanState.Finished) it.copy(heldBack = 0) else it }
    }

    /** The user trusts this file: add it without checking again (unless it changes first). */
    suspend fun addHeldFileAnyway(documentUri: String) {
        withContext(io) { scanDao.approveHeldFile(documentUri) }
        scan()
    }

    /** Adds a folder from the system picker (or re-grants a lost one) and returns false if Android refused. */
    suspend fun addFolder(treeUri: Uri): Boolean = withContext(io) {
        if (!folderAccess.persist(treeUri)) return@withContext false
        val existing = scanDao.foldersOnce().firstOrNull { it.treeUri == treeUri.toString() }
        if (existing == null) {
            scanDao.insertFolder(
                FolderEntity(
                    treeUri = treeUri.toString(),
                    rootDocumentId = folderAccess.rootDocumentId(treeUri),
                    displayName = folderAccess.displayName(treeUri),
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
        true
    }

    /**
     * Forgets a folder: its songs leave the library and the access grant is handed back. Waits for a
     * running scan, so the scan can't write songs into a folder that no longer exists.
     */
    suspend fun removeFolder(folderId: Long) = mutex.withLock { removeFolderNow(folderId) }

    private suspend fun removeFolderNow(folderId: Long) = withContext(io) {
        val folder = scanDao.foldersOnce().firstOrNull { it.id == folderId } ?: return@withContext
        scanDao.deleteFolder(folderId)
        folderAccess.release(folder.treeUri.toUri())
        lostFolderIds.value -= folderId
        rebuildAlbumsAndArtists()
    }

    suspend fun excludeSubfolder(folderId: Long, documentId: String, displayPath: String) = withContext(io) {
        scanDao.insertExcludedFolder(ExcludedFolderEntity(folderId = folderId, documentId = documentId, displayPath = displayPath))
    }

    suspend fun includeSubfolder(excludedId: Long) = withContext(io) { scanDao.deleteExcludedFolder(excludedId) }

    /** Lists the subfolders of a library folder, for the "exclude subfolders" picker. */
    suspend fun subfolders(folderId: Long, parentDocumentId: String?): List<DocumentEntry> = withContext(io) {
        val folder = scanDao.foldersOnce().firstOrNull { it.id == folderId } ?: return@withContext emptyList()
        val tree = folder.treeUri.toUri()
        folderAccess.listChildren(tree, parentDocumentId ?: folder.rootDocumentId).orEmpty()
            .filter { it.isDirectory }
            .sortedWith(compareBy(SortKeys.comparator) { it.name })
    }

    /** Re-checks every folder's access without scanning; used at startup and after playback errors. */
    suspend fun refreshAccess() = withContext(io) {
        lostFolderIds.value = scanDao.foldersOnce()
            .filterNot { folderAccess.isAccessible(it.treeUri.toUri()) }
            .map { it.id }
            .toSet()
    }

    /** Applies the "hide songs shorter than" setting without a rescan. */
    suspend fun applyMinimumDuration(seconds: Int) = withContext(io) {
        scanDao.applyMinimumDuration(seconds * 1000L)
        rebuildAlbumsAndArtists()
    }

    /** Scans all folders. Only one scan runs at a time; a second request waits and then rescans. */
    suspend fun scan() = mutex.withLock {
        withContext(io) {
            val minDurationMs = settings.current().minTrackSeconds * 1000L
            val folders = scanDao.foldersOnce()
            var found = 0
            var read = 0
            var toRead = 0
            var added = 0
            var removed = 0
            var heldBack = 0
            val lost = mutableSetOf<Long>()
            _state.value = ScanState.Running(0, 0, 0)

            for (folder in folders) {
                val tree = folder.treeUri.toUri()
                if (!folderAccess.isAccessible(tree)) {
                    lost += folder.id
                    continue
                }
                val walk = walk(folder, tree) { count ->
                    found += count
                    _state.value = ScanState.Running(found, read, toRead)
                }
                val index = scanDao.scanIndex(folder.id).associateBy { it.documentUri }
                val held = scanDao.heldFiles(folder.id).associateBy { it.documentUri }
                val changed = walk.files.filter { file ->
                    val old = index[file.documentUri]
                    val waiting = held[file.documentUri]?.let { !it.approved && it.isSameFile(file) } == true
                    !waiting && (old == null || old.lastModified != file.lastModified || old.fileSize != file.fileSize)
                }
                toRead += changed.size
                _state.value = ScanState.Running(found, read, toRead)

                // Unchanged files: only refresh sidecar art/lyrics pointers if they moved.
                updateSidecars(walk.files, index)

                changed.chunked(BATCH).forEach { batch ->
                    ensureActive()
                    val now = System.currentTimeMillis()
                    val outcomes = coroutineScope {
                        batch.map { file ->
                            async(readers) { readFile(file, index[file.documentUri], held[file.documentUri], now, minDurationMs) }
                        }.awaitAll()
                    }.filterNotNull()
                    val tracks = outcomes.filterIsInstance<FileOutcome.Added>()
                    val heldNow = outcomes.filterIsInstance<FileOutcome.HeldBack>().map { it.file }
                    db.withTransaction {
                        scanDao.upsertTracks(tracks.map { it.track })
                        scanDao.upsertHeldFiles(heldNow)
                        scanDao.deleteHeldFiles(tracks.map { it.track.documentUri }.filter { it in held })
                    }
                    added += tracks.count { index[it.track.documentUri] == null }
                    heldBack += heldNow.count { held[it.documentUri] == null }
                    read += batch.size
                    _state.value = ScanState.Running(found, read, toRead)
                }

                // Only remove songs when every folder was listed successfully, so a hiccup never wipes the library.
                if (walk.complete) {
                    val present = walk.files.mapTo(HashSet()) { it.documentUri }
                    val gone = index.values.filter { it.documentUri !in present }.map { it.id }
                    gone.chunked(BATCH).forEach { scanDao.deleteTracks(it) }
                    removed += gone.size
                    held.keys.filter { it !in present }.chunked(BATCH).forEach { scanDao.deleteHeldFiles(it) }
                }
            }
            lostFolderIds.value = lost
            rebuildAlbumsAndArtists()
            _state.value = ScanState.Finished(scanDao.trackCount(), added, removed, heldBack)
        }
    }

    private class WalkResult(val files: List<FileContext>, val complete: Boolean)

    /** Breadth-first walk of one granted folder, one provider query per directory. */
    private suspend fun walk(folder: FolderEntity, tree: Uri, onFound: (Int) -> Unit): WalkResult {
        val excluded = scanDao.excludedFolders(folder.id).mapTo(HashSet()) { it.documentId }
        data class Dir(val documentId: String, val path: String, val parentId: String?, val name: String, val depth: Int)

        val files = ArrayList<FileContext>()
        val queue = ArrayDeque(listOf(Dir(folder.rootDocumentId, folder.displayName, null, folder.displayName, 0)))
        var complete = true
        var directories = 0
        while (queue.isNotEmpty() && directories++ < MAX_DIRECTORIES) {
            currentCoroutineContext().ensureActive()
            val dir = queue.removeFirst()
            val children = folderAccess.listChildren(tree, dir.documentId)
            if (children == null) {
                complete = false
                continue
            }
            val parentUri = folderAccess.documentUri(tree, dir.documentId).toString()
            val art = children.filter { !it.isDirectory && it.name.lowercase(Locale.ROOT) in folderArtNames }
                .minByOrNull { folderArtNames.indexOf(it.name.lowercase(Locale.ROOT)) }
                ?.let { SidecarFile(folderAccess.documentUri(tree, it.documentId).toString(), it.lastModified) }
            val lyrics = children.filter { !it.isDirectory && it.name.lowercase(Locale.ROOT).endsWith(".lrc") && it.size <= MAX_LRC_BYTES }
                .associateBy { it.name.substringBeforeLast('.').lowercase(Locale.ROOT) }
            // Files in "CD1", "Disc 2" … belong to the album in the folder above.
            val albumFolderId = if (discFolder.matches(dir.name) && dir.parentId != null) dir.parentId else dir.documentId

            var count = 0
            for (child in children) {
                if (child.isDirectory) {
                    if (child.documentId !in excluded && dir.depth < MAX_DEPTH && !child.name.startsWith(".")) {
                        queue.addLast(Dir(child.documentId, dir.path + "/" + child.name, dir.documentId, child.name, dir.depth + 1))
                    }
                    continue
                }
                if (!isAudio(child)) continue
                val lrc = lyrics[child.name.substringBeforeLast('.').lowercase(Locale.ROOT)]
                files += FileContext(
                    folderId = folder.id,
                    documentUri = folderAccess.documentUri(tree, child.documentId).toString(),
                    documentId = child.documentId,
                    parentUri = parentUri,
                    folderPath = dir.path,
                    albumFolderId = albumFolderId,
                    fileName = child.name,
                    fileSize = child.size,
                    lastModified = child.lastModified,
                    folderArt = art,
                    lyricsFile = lrc?.let { SidecarFile(folderAccess.documentUri(tree, it.documentId).toString(), it.lastModified) },
                )
                count++
            }
            if (count > 0) onFound(count)
        }
        return WalkResult(files, complete && queue.isEmpty())
    }

    private sealed interface FileOutcome {
        class Added(val track: TrackEntity) : FileOutcome
        class HeldBack(val file: HeldFileEntity) : FileOutcome
    }

    private fun HeldFileEntity.isSameFile(file: FileContext) = fileSize == file.fileSize && lastModified == file.lastModified

    /**
     * Reads one new or changed file. A file seen for the first time is integrity-checked first, unless
     * the user already chose "Add anyway" for this exact version. Null when the file can't be read.
     */
    private fun readFile(file: FileContext, old: ScanIndexRow?, held: HeldFileEntity?, now: Long, minDurationMs: Long): FileOutcome? {
        val uri = file.documentUri.toUri()
        val trusted = held != null && held.approved && held.isSameFile(file)
        if (old == null && !trusted) {
            val findings = audioFiles.inspect(uri) ?: return null
            if (findings.isNotEmpty()) {
                return FileOutcome.HeldBack(
                    HeldFileEntity(
                        documentUri = file.documentUri,
                        folderId = file.folderId,
                        fileName = file.fileName,
                        folderPath = file.folderPath,
                        fileSize = file.fileSize,
                        lastModified = file.lastModified,
                        findings = IntegrityFinding.encodeAll(findings),
                        heldAt = now,
                    ),
                )
            }
        }
        val parsed = audioFiles.read(uri) ?: return null
        val track = TrackFactory.build(
            file = file,
            stream = parsed.stream,
            tags = parsed.tags,
            existingId = old?.id ?: 0,
            dateAdded = old?.dateAdded ?: now,
            minDurationMs = minDurationMs,
        )
        return FileOutcome.Added(track)
    }

    private suspend fun updateSidecars(files: List<FileContext>, index: Map<String, ScanIndexRow>) {
        val updates = files.mapNotNull { file ->
            val old = index[file.documentUri] ?: return@mapNotNull null
            if (old.lastModified != file.lastModified || old.fileSize != file.fileSize) return@mapNotNull null
            val embedded = old.artUri == file.documentUri
            val artUri = if (embedded) old.artUri else file.folderArt?.uri
            val artVersion = if (embedded) old.artVersion else file.folderArt?.lastModified ?: 0
            val artSource = when {
                embedded -> old.artSource
                file.folderArt != null -> ArtSource.FOLDER.ordinal
                else -> null
            }
            val lrc = file.lyricsFile?.uri
            if (artUri == old.artUri && artVersion == old.artVersion && lrc == old.lrcUri) null
            else SidecarUpdate(old.id, artUri, artSource, artVersion, lrc)
        }
        if (updates.isEmpty()) return
        db.withTransaction {
            updates.forEach { scanDao.updateSidecars(it.id, it.artUri, it.artSource, it.artVersion, it.lrcUri) }
        }
    }

    private class SidecarUpdate(val id: Long, val artUri: String?, val artSource: Int?, val artVersion: Long, val lrcUri: String?)

    /**
     * Resolves album artists (album-artist tag → compilation → several artists means Various Artists →
     * the artist), then rebuilds the albums and artists tables in one transaction.
     */
    suspend fun rebuildAlbumsAndArtists() = withContext(io) {
        db.withTransaction {
            scanDao.groupingRows().groupBy { it.albumKey }.forEach { (_, rows) ->
                val tagged = rows.mapNotNull { it.albumArtistTag }
                val resolved = when {
                    tagged.isNotEmpty() -> tagged.groupingBy { it }.eachCount().maxBy { it.value }.key
                    rows.any { it.isCompilation } -> TrackFactory.VARIOUS_ARTISTS
                    rows.map { TrackFactory.normalize(it.artist) }.distinct().size > 1 -> TrackFactory.VARIOUS_ARTISTS
                    else -> rows.first().artist
                }
                val sortTag = rows.firstNotNullOfOrNull { it.albumArtistSortTag }.takeIf { resolved != TrackFactory.VARIOUS_ARTISTS }
                rows.filter { it.albumArtist != resolved }.forEach { row ->
                    scanDao.updateAlbumArtist(row.id, resolved, TrackFactory.artistKey(resolved), SortKeys.key(resolved, sortTag))
                }
            }
        }

        val art = scanDao.albumArtRows().groupBy { it.albumKey }.mapValues { it.value.first() }
        val albums = scanDao.albumAggregates().map { a ->
            val picture = art[a.albumKey]
            AlbumEntity(
                albumKey = a.albumKey,
                title = a.title,
                albumArtist = a.albumArtist,
                artistKey = a.artistKey,
                albumArtistSortKey = SortKeys.key(a.albumArtist, a.albumArtistSortTag.takeIf { a.albumArtist != TrackFactory.VARIOUS_ARTISTS }),
                albumSortKey = SortKeys.key(a.title, a.albumSortTag),
                year = a.year,
                trackCount = a.trackCount,
                discCount = a.discCount,
                durationMs = a.durationMs,
                artUri = picture?.artUri,
                artSource = picture?.artSource,
                artVersion = picture?.artVersion ?: 0,
                bestQuality = a.bestQuality,
                autoReleaseType = ReleaseClassifier.classify(a.releaseTypeTag, a.trackCount, a.durationMs).ordinal,
                dateAdded = a.dateAdded,
            )
        }
        val artists = albums.groupBy { it.artistKey }.map { (key, list) ->
            val ordered = list.sortedWith { x, y ->
                compareValues(x.year ?: Int.MAX_VALUE, y.year ?: Int.MAX_VALUE).takeIf { it != 0 }
                    ?: SortKeys.compareKeys(x.albumSortKey, y.albumSortKey)
            }
            val withArt = ordered.firstOrNull { it.artUri != null }
            ArtistEntity(
                artistKey = key,
                name = ordered.first().albumArtist,
                sortKey = ordered.first().albumArtistSortKey,
                albumCount = list.size,
                trackCount = list.sumOf { it.trackCount },
                artUri = withArt?.artUri,
                artSource = withArt?.artSource,
                artVersion = withArt?.artVersion ?: 0,
            )
        }
        scanDao.replaceAlbumsAndArtists(albums, artists)
    }

    private fun isAudio(entry: DocumentEntry): Boolean {
        val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in audioExtensions || (extension.isEmpty() && entry.mimeType.startsWith("audio/"))
    }

    private companion object {
        const val BATCH = 64
        const val PARALLEL_READS = 4
        const val MAX_DEPTH = 32
        const val MAX_DIRECTORIES = 50_000
        const val MAX_LRC_BYTES = 1024 * 1024
        val audioExtensions = setOf(
            "mp3", "mp2", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus", "flac", "wav", "aif", "aiff", "aifc", "mka",
        )
        val folderArtNames = listOf(
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            "front.jpg", "front.jpeg", "front.png", "front.webp",
        )
        val discFolder = Regex("^(cd|disc|disk)\\s*\\d{1,2}$", RegexOption.IGNORE_CASE)
    }
}
