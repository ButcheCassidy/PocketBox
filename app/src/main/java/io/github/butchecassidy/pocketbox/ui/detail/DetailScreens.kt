// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.LocalChromePadding
import io.github.butchecassidy.pocketbox.ui.components.AlbumCard
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.DragHandle
import io.github.butchecassidy.pocketbox.ui.components.EmptyState
import io.github.butchecassidy.pocketbox.ui.components.LocalActions
import io.github.butchecassidy.pocketbox.ui.components.LocalShowMessage
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.PocketTopBar
import io.github.butchecassidy.pocketbox.ui.components.QualityBadge
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.components.TrackRow
import io.github.butchecassidy.pocketbox.ui.components.reorderItem
import io.github.butchecassidy.pocketbox.ui.components.rememberReorderState
import io.github.butchecassidy.pocketbox.ui.library.NewPlaylistDialog
import io.github.butchecassidy.pocketbox.ui.navigation.FacetType
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.navigation.ShelfKind
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The frame every pushed screen shares: the same top bar, back button and margins. */
@Composable
private fun DetailScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigator = LocalNavigator.current
    Scaffold(
        topBar = { PocketTopBar(title, onBack = { navigator.back() }, actions = actions) },
        contentWindowInsets = WindowInsets(0),
    ) { padding -> content(padding) }
}

/** Bottom padding that keeps the last row clear of the mini player. */
@Composable
private fun listPadding() = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding() + LocalSpacing.current.two)

/** Play / Shuffle buttons used at the top of every song list. */
@Composable
private fun PlayShuffleRow(ids: List<Long>) {
    val queue = LocalAppContainer.current.queue
    val spacing = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.one),
        horizontalArrangement = Arrangement.spacedBy(spacing.one, Alignment.CenterHorizontally),
    ) {
        Button(onClick = { queue.play(ids, 0) }, enabled = ids.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_play_arrow_filled), null)
            Text(stringResource(R.string.play), Modifier.padding(start = spacing.one))
        }
        FilledTonalButton(onClick = { queue.shufflePlay(ids) }, enabled = ids.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_shuffle), null)
            Text(stringResource(R.string.shuffle), Modifier.padding(start = spacing.one))
        }
    }
}

/** Songs that play as a list: tapping one replaces the queue with this list, starting there. */
private fun LazyListScope.trackItems(tracks: List<Track>, onPlay: (Int) -> Unit, numbered: Boolean = false) {
    itemsIndexed(tracks, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
        val actions = LocalActions.current
        val current by LocalAppContainer.current.player.state.collectAsStateWithLifecycle()
        TrackRow(
            track,
            onClick = { onPlay(index) },
            onMenu = { actions.openSong(track) },
            trackNumber = if (numbered) track.trackNumber ?: (index + 1) else null,
            isCurrent = track.id == current.currentTrackId,
        )
    }
}

/** A plain song list with Play / Shuffle: favorites, folders, genres, history. */
@Composable
private fun TrackListScreen(title: String, tracks: List<Track>?, emptyText: String) {
    val queue = LocalAppContainer.current.queue
    DetailScaffold(title) { padding ->
        val list = tracks ?: return@DetailScaffold
        if (list.isEmpty()) {
            EmptyState(R.drawable.ic_music_note, title, emptyText, Modifier.padding(padding))
            return@DetailScaffold
        }
        val ids = list.map { it.id }
        LazyColumn(Modifier.padding(padding), contentPadding = listPadding()) {
            item(key = "play") { PlayShuffleRow(ids) }
            trackItems(list, onPlay = { queue.play(ids, it) })
        }
    }
}

@Composable
fun AlbumScreen(albumKey: String) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val actions = LocalActions.current
    val spacing = LocalSpacing.current
    val album by remember(albumKey) { container.library.album(albumKey) }.collectAsStateWithLifecycle(null)
    val tracks by remember(albumKey) { container.library.albumTracks(albumKey) }.collectAsStateWithLifecycle(emptyList())
    val current = album ?: return DetailScaffold("") {}
    val ids = tracks.map { it.id }
    DetailScaffold(current.title, actions = {
        PocketIconButton(R.drawable.ic_more_vert, stringResource(R.string.more_options), { actions.openRelease(current) })
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = listPadding()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage(
                        current.art,
                        LocalSizes.current.gridMinCell * 2,
                        current.key,
                        Modifier.fillMaxWidth(HEADER_ART_FRACTION).widthIn(max = HEADER_ART_MAX).aspectRatio(1f),
                        contentDescription = stringResource(R.string.album_art),
                    )
                    Text(
                        current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = spacing.two).semantics { heading() },
                    )
                    Text(
                        current.albumArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { navigator.go(Routes.artist(current.artistKey)) }.padding(spacing.half),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val details = listOfNotNull(current.year?.toString(), Format.totalDuration(current.durationMs)).joinToString(" · ")
                        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QualityBadge(current.qualityClass, Modifier.padding(start = spacing.one), long = true)
                    }
                }
            }
            item(key = "play") { PlayShuffleRow(ids) }
            // Disc 1, Disc 2 … only for multi-disc releases.
            val discs = tracks.groupBy { it.discNumber ?: 1 }
            discs.forEach { (disc, discTracks) ->
                if (discs.size > 1) item(key = "disc-$disc") { SectionHeader(stringResource(R.string.disc_number, disc)) }
                val offset = tracks.indexOf(discTracks.first())
                trackItems(discTracks, onPlay = { container.queue.play(ids, offset + it) }, numbered = true)
            }
        }
    }
}

@Composable
fun ArtistScreen(artistKey: String) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val actions = LocalActions.current
    val spacing = LocalSpacing.current
    val artist by remember(artistKey) { container.library.artist(artistKey) }.collectAsStateWithLifecycle(null)
    val releases by remember(artistKey) { container.library.artistReleases(artistKey) }.collectAsStateWithLifecycle(emptyList())
    val tracks by remember(artistKey) { container.library.artistTracks(artistKey) }.collectAsStateWithLifecycle(emptyList())
    val current = artist ?: return DetailScaffold("") {}
    val ids = tracks.map { it.id }
    val (singles, albums) = releases.partition { it.releaseType.isSingleOrEp }
    DetailScaffold(current.name) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = listPadding()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage(
                        current.art,
                        LocalSizes.current.gridMinCell,
                        current.key,
                        Modifier.size(LocalSizes.current.gridMinCell),
                        shape = CircleShape,
                    )
                    Text(
                        current.name,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(spacing.two).semantics { heading() },
                    )
                }
            }
            item(key = "play") { PlayShuffleRow(ids) }
            releaseShelf(R.string.albums_section, albums, "albums", { navigator.go(Routes.album(it.key)) }, { actions.openRelease(it) })
            releaseShelf(R.string.singles_section, singles, "singles", { navigator.go(Routes.album(it.key)) }, { actions.openRelease(it) })
            if (tracks.isNotEmpty()) item(key = "songs-header") { SectionHeader(stringResource(R.string.all_songs)) }
            trackItems(tracks, onPlay = { container.queue.play(ids, it) })
        }
    }
}

private fun LazyListScope.releaseShelf(title: Int, albums: List<Album>, key: String, onOpen: (Album) -> Unit, onMenu: (Album) -> Unit) {
    if (albums.isEmpty()) return
    item(key = "$key-shelf") {
        val spacing = LocalSpacing.current
        Column {
            SectionHeader(stringResource(title))
            LazyRow(contentPadding = PaddingValues(horizontal = spacing.screen - spacing.one)) {
                items(albums, key = { it.key }) { album ->
                    AlbumCard(album, { onOpen(album) }, { onMenu(album) }, Modifier.widthIn(max = LocalSizes.current.shelfItem + spacing.two))
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(playlistId: Long) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val actions = LocalActions.current
    val showMessage = LocalShowMessage.current
    val resources = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val name by remember(playlistId) { container.playlists.name(playlistId) }.collectAsStateWithLifecycle(null)
    val tracks by remember(playlistId) { container.playlists.tracks(playlistId) }.collectAsStateWithLifecycle(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val title = name ?: return DetailScaffold("") {}
    val ids = tracks.map { it.id }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) scope.launch {
            val ok = container.playlists.export(playlistId, tracks, uri)
            showMessage(resources.getString(if (ok) R.string.exported_playlist else R.string.export_failed))
        }
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, indexOffset = 1) { from, to ->
        val reordered = ids.toMutableList().apply { add(to, removeAt(from)) }
        scope.launch { container.playlists.setOrder(playlistId, reordered) }
    }
    DetailScaffold(title, actions = {
        Box {
            PocketIconButton(R.drawable.ic_more_vert, stringResource(R.string.more_options), { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menuOpen = false; renaming = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.export_playlist)) }, onClick = { menuOpen = false; exporter.launch("$title.m3u8") })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuOpen = false; deleting = true })
            }
        }
    }) { padding ->
        if (tracks.isEmpty()) {
            EmptyState(R.drawable.ic_queue_music, title, stringResource(R.string.empty_playlist), Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), state = listState, contentPadding = listPadding()) {
                item(key = "play") { PlayShuffleRow(ids) }
                itemsIndexed(tracks, key = { index, track -> "${track.id}-$index" }) { index, track ->
                    Row(Modifier.reorderItem(reorder, index), verticalAlignment = Alignment.CenterVertically) {
                        TrackRow(
                            track,
                            onClick = { container.queue.play(ids, index) },
                            onMenu = { actions.openSong(track) },
                            modifier = Modifier.weight(1f),
                        )
                        DragHandle(reorder, index, Modifier.padding(end = LocalSpacing.current.screen))
                    }
                }
            }
        }
    }
    if (renaming) {
        NewPlaylistDialog(
            initial = title,
            title = stringResource(R.string.rename),
            onCreate = { scope.launch { container.playlists.rename(playlistId, it) }; renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        PocketDialog(
            title = stringResource(R.string.delete_playlist_title),
            onDismissRequest = { deleting = false },
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                deleting = false
                scope.launch { container.playlists.delete(playlistId) }
                navigator.back()
            },
            dismissLabel = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.delete_playlist_body, title)) }
    }
}

@Composable
fun FavoritesScreen() {
    val container = LocalAppContainer.current
    val tracks by remember { container.library.favorites }.collectAsStateWithLifecycle(null)
    TrackListScreen(stringResource(R.string.favorites), tracks, stringResource(R.string.empty_favorites))
}

@Composable
fun FolderScreen(parentUri: String) {
    val container = LocalAppContainer.current
    val tracks by remember(parentUri) { container.library.folderTracks(parentUri) }.collectAsStateWithLifecycle(null)
    val title = tracks?.firstOrNull()?.folderPath?.substringAfterLast('/').orEmpty()
    TrackListScreen(title, tracks, stringResource(R.string.empty_list))
}

@Composable
fun ShelfScreen(kind: ShelfKind) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val actions = LocalActions.current
    when (kind) {
        ShelfKind.RECENTLY_PLAYED -> {
            val tracks by remember { container.history.recentlyPlayedTracks() }.collectAsStateWithLifecycle(null)
            TrackListScreen(stringResource(R.string.recently_played), tracks, stringResource(R.string.empty_history))
        }
        ShelfKind.MOST_PLAYED -> {
            val tracks by remember { container.history.mostPlayedTracks() }.collectAsStateWithLifecycle(null)
            TrackListScreen(stringResource(R.string.most_played), tracks, stringResource(R.string.empty_history))
        }
        ShelfKind.RECENTLY_ADDED -> {
            val albums by remember { container.library.recentlyAdded(RECENTLY_ADDED_PAGE) }.collectAsStateWithLifecycle(emptyList())
            val spacing = LocalSpacing.current
            DetailScaffold(stringResource(R.string.recently_added)) { padding ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(LocalSizes.current.gridMinCell),
                    contentPadding = PaddingValues(
                        start = spacing.screen - spacing.one,
                        end = spacing.screen - spacing.one,
                        bottom = LocalChromePadding.current.calculateBottomPadding(),
                    ),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                ) {
                    items(albums, key = { it.key }) { album ->
                        AlbumCard(album, { navigator.go(Routes.album(album.key)) }, { actions.openRelease(album) })
                    }
                }
            }
        }
    }
}

@Composable
fun FacetsScreen(type: FacetType) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val facets by remember(type) {
        when (type) {
            FacetType.GENRE -> container.library.genres
            FacetType.YEAR -> container.library.years
            FacetType.COMPOSER -> container.library.composers
        }
    }.collectAsStateWithLifecycle(emptyList())
    DetailScaffold(facetTitle(type)) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = listPadding()) {
            items(facets, key = { it.value }) { facet ->
                ListItem(
                    headlineContent = { Text(facet.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Text(facet.trackCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { navigator.go(Routes.facet(type, facet.value)) },
                )
            }
        }
    }
}

@Composable
fun FacetScreen(type: FacetType, value: String) {
    val container = LocalAppContainer.current
    val tracks by remember(type, value) {
        when (type) {
            FacetType.GENRE -> container.library.genreTracks(value)
            FacetType.YEAR -> value.toIntOrNull()?.let(container.library::yearTracks) ?: flowOf(emptyList())
            FacetType.COMPOSER -> container.library.composerTracks(value)
        }
    }.collectAsStateWithLifecycle(null)
    TrackListScreen(value, tracks, stringResource(R.string.empty_list))
}

@Composable
private fun facetTitle(type: FacetType) = stringResource(
    when (type) {
        FacetType.GENRE -> R.string.genres
        FacetType.YEAR -> R.string.years
        FacetType.COMPOSER -> R.string.composers
    },
)

private const val HEADER_ART_FRACTION = 0.6f
private val HEADER_ART_MAX = 320.dp
private const val RECENTLY_ADDED_PAGE = 200
