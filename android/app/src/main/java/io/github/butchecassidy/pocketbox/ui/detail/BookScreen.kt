// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.Audiobook
import io.github.butchecassidy.pocketbox.domain.model.BookProgress
import io.github.butchecassidy.pocketbox.domain.model.Bookmark
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.LocalActions
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.components.TrackRow
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/**
 * One audiobook: where you are in it, a "Continue at …" button, your bookmarks to jump to instead,
 * and the chapters. Playing always runs the chapters in order from the chosen spot, never shuffled.
 */
@Composable
fun BookScreen(bookKey: String) {
    val container = LocalAppContainer.current
    val actions = LocalActions.current
    val book by remember(bookKey) { container.books.book(bookKey) }.collectAsStateWithLifecycle(null)
    val bookmarks by remember(bookKey) { container.books.bookmarks(bookKey) }.collectAsStateWithLifecycle(emptyList())
    val player by container.player.state.collectAsStateWithLifecycle()
    val current = book ?: return DetailScaffold("") {}
    val ids = current.chapters.map { it.id }
    val playAt = { trackId: Long, positionMs: Long ->
        val index = ids.indexOf(trackId)
        if (index >= 0) container.queue.playBook(ids, index, positionMs)
    }
    DetailScaffold(current.title) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = listPadding()) {
            item(key = "header") { BookHeader(current) }
            item(key = "play") {
                ListenButtons(
                    book = current,
                    onContinue = { progress -> playAt(progress.trackId, progress.positionMs) },
                    // The saved spot moves to the start as soon as playback begins there.
                    onFromStart = { playAt(ids.first(), 0) },
                )
            }
            if (bookmarks.isNotEmpty()) {
                item(key = "bookmarks") { SectionHeader(stringResource(R.string.bookmarks)) }
                items(bookmarks, key = { "bookmark-${it.id}" }) { bookmark ->
                    BookmarkRow(
                        bookmark,
                        chapterTitle = current.chapters.firstOrNull { it.id == bookmark.trackId }?.title.orEmpty(),
                        onPlay = { playAt(bookmark.trackId, bookmark.positionMs) },
                        onDelete = { container.appScope.launch { container.books.deleteBookmark(bookmark.id) } },
                    )
                }
            }
            item(key = "chapters") { SectionHeader(stringResource(R.string.chapters)) }
            itemsIndexed(current.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                TrackRow(
                    chapter,
                    onClick = { playAt(chapter.id, 0) },
                    onMenu = { actions.openSong(chapter) },
                    subtitle = Format.duration(chapter.durationMs),
                    trackNumber = index + 1,
                    isCurrent = chapter.id == player.currentTrackId,
                )
            }
        }
    }
}

@Composable
private fun BookHeader(book: Audiobook) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtImage(
            book.art,
            LocalSizes.current.gridMinCell * 2,
            book.key,
            Modifier.fillMaxWidth(HEADER_ART_FRACTION).widthIn(max = HEADER_ART_MAX).aspectRatio(1f),
            contentDescription = stringResource(R.string.album_art),
        )
        Text(
            book.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.two).semantics { heading() },
        )
        Text(
            book.author,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(spacing.half),
        )
        Text(
            stringResource(
                R.string.separator_dot,
                pluralStringResource(R.plurals.chapter_count, book.chapters.size, book.chapters.size),
                Format.totalDuration(book.durationMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BookProgressLine(book, Modifier.fillMaxWidth().padding(top = spacing.two))
    }
}

/** "3 h 12 min left" over a progress bar; "Not started" or "Finished" without one. */
@Composable
fun BookProgressLine(book: Audiobook, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(modifier) {
        Text(
            when {
                book.isFinished -> stringResource(R.string.audiobook_finished)
                book.progress == null -> stringResource(R.string.audiobook_not_started)
                else -> stringResource(R.string.audiobook_left, Format.totalDuration(book.durationMs - book.listenedMs))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (book.progress != null && book.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (book.listenedMs.toFloat() / book.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = spacing.half),
            )
        }
    }
}

/** Continue (or start, or listen again) plus Start over once there is progress to throw away. */
@Composable
private fun ListenButtons(book: Audiobook, onContinue: (BookProgress) -> Unit, onFromStart: () -> Unit) {
    val spacing = LocalSpacing.current
    val progress = book.progress?.takeIf { book.progressChapter >= 0 && !book.isFinished }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.one),
        horizontalArrangement = Arrangement.spacedBy(spacing.one),
    ) {
        Button(onClick = { if (progress != null) onContinue(progress) else onFromStart() }, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_play_arrow_filled), null)
            Text(
                when {
                    progress != null -> stringResource(R.string.audiobook_continue, Format.duration(book.listenedMs))
                    book.isFinished -> stringResource(R.string.audiobook_again)
                    else -> stringResource(R.string.audiobook_start)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = spacing.one),
            )
        }
        if (progress != null) {
            FilledTonalButton(onClick = onFromStart) { Text(stringResource(R.string.audiobook_start_over), maxLines = 1) }
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, chapterTitle: String, onPlay: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.audiobook_spot, chapterTitle, Format.duration(bookmark.positionMs)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(DateUtils.getRelativeTimeSpanString(bookmark.createdAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_bookmark), null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { PocketIconButton(R.drawable.ic_delete, stringResource(R.string.delete_bookmark), onDelete) },
        modifier = Modifier.clickable(onClick = onPlay),
    )
}
