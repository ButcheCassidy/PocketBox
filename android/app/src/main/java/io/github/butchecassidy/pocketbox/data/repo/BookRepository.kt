// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.repo

import io.github.butchecassidy.pocketbox.data.db.BookDao
import io.github.butchecassidy.pocketbox.domain.model.Audiobook
import io.github.butchecassidy.pocketbox.domain.model.BookProgress
import io.github.butchecassidy.pocketbox.domain.model.Bookmark
import io.github.butchecassidy.pocketbox.domain.sort.SortKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Audiobooks from the folders marked as audiobook folders, with saved progress and bookmarks. */
class BookRepository(private val dao: BookDao, private val cpu: CoroutineDispatcher, private val io: CoroutineDispatcher) {

    /** Books being listened to first (most recent first), then the rest by title. */
    val books: Flow<List<Audiobook>> = combine(dao.chapters(), dao.progress()) { rows, progress ->
        val saved = progress.associateBy { it.bookKey }
        rows.map { it.toTrack() }.groupBy { it.albumKey }.map { (key, chapters) ->
            val first = chapters.first()
            Audiobook(
                key = key,
                title = first.album,
                author = first.albumArtist,
                art = chapters.firstNotNullOfOrNull { it.art },
                chapters = chapters,
                progress = saved[key]?.let { BookProgress(it.trackId, it.positionMs, it.updatedAt) },
            )
        }.sortedWith(
            compareByDescending<Audiobook> { it.progress?.updatedAtMs ?: Long.MIN_VALUE }.thenBy(SortKeys.comparator) { it.title },
        )
    }.flowOn(cpu)

    fun book(key: String): Flow<Audiobook?> = books.map { list -> list.firstOrNull { it.key == key } }.distinctUntilChanged()

    fun bookmarks(bookKey: String): Flow<List<Bookmark>> = dao.bookmarks(bookKey)
        .map { rows -> rows.map { Bookmark(it.id, it.trackId, it.positionMs, it.createdAt) } }
        .flowOn(io)

    suspend fun saveProgress(bookKey: String, trackId: Long, positionMs: Long) = withContext(io) {
        dao.saveProgress(bookKey, trackId, positionMs.coerceAtLeast(0), System.currentTimeMillis())
    }

    suspend fun addBookmark(bookKey: String, trackId: Long, positionMs: Long) = withContext(io) {
        dao.addBookmark(bookKey, trackId, positionMs.coerceAtLeast(0), System.currentTimeMillis())
    }

    suspend fun deleteBookmark(id: Long) = withContext(io) { dao.deleteBookmark(id) }
}
