// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.data.scan.ScanState
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.AlbumSort
import io.github.butchecassidy.pocketbox.domain.model.LibraryTab
import io.github.butchecassidy.pocketbox.domain.model.ScrollPosition
import io.github.butchecassidy.pocketbox.domain.model.SongSort
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.LocalChromePadding
import io.github.butchecassidy.pocketbox.ui.components.AlbumCard
import io.github.butchecassidy.pocketbox.ui.components.AlbumRow
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.ArtistRow
import io.github.butchecassidy.pocketbox.ui.components.EmptyState
import io.github.butchecassidy.pocketbox.ui.components.LocalActions
import io.github.butchecassidy.pocketbox.ui.components.LocalShowMessage
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.PocketTopBar
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.components.TrackRow
import io.github.butchecassidy.pocketbox.ui.navigation.FacetType
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.navigation.SettingsSection
import io.github.butchecassidy.pocketbox.ui.navigation.ShelfKind
import io.github.butchecassidy.pocketbox.ui.pocketViewModel
import io.github.butchecassidy.pocketbox.ui.rememberHaptic
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** The library: reorderable, hideable tabs that reopen on the last one used, at the same scroll position. */
@Composable
fun LibraryScreen() {
    val vm = pocketViewModel { LibraryViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val initialTab by vm.initialTab.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val tabs = settings.visibleTabs
    val start = initialTab ?: return
    val pager = rememberPagerState(initialPage = tabs.indexOf(start).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    // Follow a tab chosen elsewhere (for example "All songs" in search).
    LaunchedEffect(start, tabs) {
        val index = tabs.indexOf(start)
        if (index >= 0 && index != pager.currentPage) pager.scrollToPage(index)
    }
    LaunchedEffect(pager, tabs) {
        snapshotFlow { pager.settledPage }.collect { page -> tabs.getOrNull(page)?.let(vm::onTabSelected) }
    }

    Scaffold(
        topBar = {
            PocketTopBar(title = stringResource(R.string.app_name)) {
                PocketIconButton(R.drawable.ic_search, stringResource(R.string.search), { navigator.topLevel(Routes.SEARCH) })
                Box {
                    PocketIconButton(R.drawable.ic_more_vert, stringResource(R.string.more_options), { menuOpen = true })
                    LibraryMenu(menuOpen, onDismiss = { menuOpen = false }, current = tabs.getOrNull(pager.currentPage), vm = vm)
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LibraryBanners(vm)
            PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = LocalSpacing.current.screen) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(tab.label) },
                    )
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { tabs[it] }) { page ->
                when (tabs[page]) {
                    LibraryTab.ALBUMS -> ReleasesTab(LibraryTab.ALBUMS, vm, withShelves = true)
                    LibraryTab.SINGLES -> ReleasesTab(LibraryTab.SINGLES, vm, withShelves = false)
                    LibraryTab.SONGS -> SongsTab(vm)
                    LibraryTab.ARTISTS -> ArtistsTab(vm)
                    LibraryTab.PLAYLISTS -> PlaylistsTab(vm)
                    LibraryTab.FOLDERS -> FoldersTab(vm)
                }
            }
        }
    }
}

@Composable
private fun LibraryMenu(open: Boolean, onDismiss: () -> Unit, current: LibraryTab?, vm: LibraryViewModel) {
    val navigator = LocalNavigator.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (current == LibraryTab.ALBUMS || current == LibraryTab.SINGLES) {
            AlbumSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    trailingIcon = { if (settings.albumSort == sort) Icon(painterResource(R.drawable.ic_check), null) },
                    onClick = { vm.setAlbumSort(sort); onDismiss() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (settings.albumsAsGrid) R.string.list_view else R.string.grid_view)) },
                leadingIcon = { Icon(painterResource(if (settings.albumsAsGrid) R.drawable.ic_view_list else R.drawable.ic_grid_view), null) },
                onClick = { vm.setAlbumsAsGrid(!settings.albumsAsGrid); onDismiss() },
            )
        }
        if (current == LibraryTab.SONGS) {
            SongSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    trailingIcon = { if (settings.songSort == sort) Icon(painterResource(R.drawable.ic_check), null) },
                    onClick = { vm.setSongSort(sort); onDismiss() },
                )
            }
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.favorites)) }, onClick = { onDismiss(); navigator.go(Routes.FAVORITES) })
        DropdownMenuItem(text = { Text(stringResource(R.string.genres)) }, onClick = { onDismiss(); navigator.go(Routes.facets(FacetType.GENRE)) })
        DropdownMenuItem(text = { Text(stringResource(R.string.years)) }, onClick = { onDismiss(); navigator.go(Routes.facets(FacetType.YEAR)) })
        DropdownMenuItem(text = { Text(stringResource(R.string.composers)) }, onClick = { onDismiss(); navigator.go(Routes.facets(FacetType.COMPOSER)) })
    }
}

/** Scan progress and "access lost" notices, above the tabs. */
@Composable
private fun LibraryBanners(vm: LibraryViewModel) {
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val lost by vm.lostRoots.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::regrant) }
    (scan as? ScanState.Running)?.let { running ->
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.half), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.width(LocalSizes.current.touchTarget))
            Text(
                if (running.filesToRead > 0) stringResource(R.string.scanning_reading, running.filesRead, running.filesToRead)
                else stringResource(R.string.scanning, running.filesFound),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = spacing.one),
            )
        }
    }
    lost.forEach { root ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.half),
        ) {
            Column(Modifier.padding(spacing.two)) {
                Text(stringResource(R.string.access_lost, root.name), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.removeRoot(root.id) }) { Text(stringResource(R.string.remove)) }
                    Button(onClick = { picker.launch(null) }) { Text(stringResource(R.string.pick_again)) }
                }
            }
        }
    }
}

/** Restores a list's saved scroll position, and saves it again whenever scrolling stops. */
@Composable
private fun rememberRestoredListState(tab: LibraryTab, vm: LibraryViewModel): LazyListState? {
    var state by remember(tab) { mutableStateOf<LazyListState?>(null) }
    LaunchedEffect(tab) {
        val saved = vm.scrollPosition(tab)
        state = LazyListState(saved?.index ?: 0, saved?.offset ?: 0)
    }
    state?.let { list ->
        LaunchedEffect(list) {
            snapshotFlow { list.isScrollInProgress }.drop(1).filter { !it }.collect {
                vm.saveScroll(tab, ScrollPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset))
            }
        }
    }
    return state
}

@Composable
private fun rememberRestoredGridState(tab: LibraryTab, vm: LibraryViewModel): LazyGridState? {
    var state by remember(tab) { mutableStateOf<LazyGridState?>(null) }
    LaunchedEffect(tab) {
        val saved = vm.scrollPosition(tab)
        state = LazyGridState(saved?.index ?: 0, saved?.offset ?: 0)
    }
    state?.let { grid ->
        LaunchedEffect(grid) {
            snapshotFlow { grid.isScrollInProgress }.drop(1).filter { !it }.collect {
                vm.saveScroll(tab, ScrollPosition(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset))
            }
        }
    }
    return state
}

/** Albums (with history shelves on top) or Singles & EPs, as a grid or a list. */
@Composable
private fun ReleasesTab(tab: LibraryTab, vm: LibraryViewModel, withShelves: Boolean) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val releases by (if (tab == LibraryTab.ALBUMS) vm.albums else vm.singles).collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()
    val recentlyAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val songCount by vm.songCount.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val actions = LocalActions.current
    val spacing = LocalSpacing.current
    val state = rememberRestoredGridState(tab, vm) ?: return
    if (songCount == 0) return EmptyLibrary()
    val columns = when {
        !settings.albumsAsGrid -> GridCells.Fixed(1)
        settings.gridColumns > 0 -> GridCells.Fixed(settings.gridColumns)
        else -> GridCells.Adaptive(LocalSizes.current.gridMinCell)
    }
    LazyVerticalGrid(
        columns = columns,
        state = state,
        contentPadding = PaddingValues(
            start = if (settings.albumsAsGrid) spacing.screen - spacing.one else 0.dp,
            end = if (settings.albumsAsGrid) spacing.screen - spacing.one else 0.dp,
            bottom = LocalChromePadding.current.calculateBottomPadding() + spacing.two,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (withShelves) {
            shelf(R.string.recently_played, recentlyPlayed, ShelfKind.RECENTLY_PLAYED)
            shelf(R.string.most_played, mostPlayed, ShelfKind.MOST_PLAYED)
            shelf(R.string.recently_added, recentlyAdded, ShelfKind.RECENTLY_ADDED)
            if (recentlyPlayed.isNotEmpty() || mostPlayed.isNotEmpty() || recentlyAdded.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-all", contentType = "header") {
                    SectionHeader(stringResource(R.string.albums_section))
                }
            }
        }
        items(releases, key = { it.key }, contentType = { "release" }) { album ->
            if (settings.albumsAsGrid) {
                AlbumCard(album, onClick = { navigator.go(Routes.album(album.key)) }, onLongClick = { actions.openRelease(album) })
            } else {
                AlbumRow(album, onClick = { navigator.go(Routes.album(album.key)) }, onLongClick = { actions.openRelease(album) })
            }
        }
    }
}

/** A horizontal row of albums with a heading and "See all", spanning the whole grid. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.shelf(title: Int, albums: List<Album>, kind: ShelfKind) {
    if (albums.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }, key = "shelf-${kind.name}", contentType = "shelf") {
        val navigator = LocalNavigator.current
        val actions = LocalActions.current
        val spacing = LocalSpacing.current
        Column {
            SectionHeader(stringResource(title), onSeeAll = { navigator.go(Routes.shelf(kind)) })
            LazyRow(contentPadding = PaddingValues(horizontal = spacing.screen - spacing.one)) {
                items(albums, key = { it.key }) { album ->
                    AlbumCard(
                        album,
                        onClick = { navigator.go(Routes.album(album.key)) },
                        onLongClick = { actions.openRelease(album) },
                        modifier = Modifier.width(LocalSizes.current.shelfItem + spacing.two),
                        showYear = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    val navigator = LocalNavigator.current
    EmptyState(
        icon = R.drawable.ic_library_music,
        title = stringResource(R.string.empty_library_title),
        body = stringResource(R.string.empty_library_body),
        actionLabel = stringResource(R.string.add_folder),
        onAction = { navigator.go(Routes.settings(SettingsSection.LIBRARY)) },
    )
}

/** Every song, strictly A–Z, with sticky letter headers and a fast-scroll letter rail. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsTab(vm: LibraryViewModel) {
    val sections by vm.songs.collectAsStateWithLifecycle()
    val songCount by vm.songCount.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val actions = LocalActions.current
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val state = rememberRestoredListState(LibraryTab.SONGS, vm) ?: return
    if (songCount == 0) return EmptyLibrary()
    val tracks = sections.tracks
    val headers = sections.headers
    // Lazy-list index of each header: one leading "Shuffle all" row, then header + songs per letter.
    val headerListIndex = remember(headers) { headers.mapIndexed { i, h -> h.letter to 1 + i + h.firstTrackIndex }.toMap() }
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding()),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "shuffle", contentType = "shuffle") {
                ShuffleAllRow { container.queue.shufflePlay(tracks.map { it.id }) }
            }
            if (headers.isEmpty()) {
                itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { index, track ->
                    TrackRow(
                        track,
                        onClick = { container.queue.play(tracks.map { it.id }, index) },
                        onMenu = { actions.openSong(track) },
                        isCurrent = track.id == playerState.currentTrackId,
                    )
                }
            } else {
                headers.forEachIndexed { sectionIndex, header ->
                    val end = headers.getOrNull(sectionIndex + 1)?.firstTrackIndex ?: tracks.size
                    stickyHeader(key = "letter-${header.letter}", contentType = "letter") { LetterHeaderRow(header.letter) }
                    for (index in header.firstTrackIndex until end) {
                        val track = tracks[index]
                        item(key = track.id, contentType = "track") {
                            TrackRow(
                                track,
                                onClick = { container.queue.play(tracks.map { it.id }, index) },
                                onMenu = { actions.openSong(track) },
                                isCurrent = track.id == playerState.currentTrackId,
                            )
                        }
                    }
                }
            }
        }
        if (headers.size > 1) {
            LetterRail(headers.map { it.letter }) { letter ->
                headerListIndex[letter]?.let { scope.launch { state.scrollToItem(it) } }
            }
        }
    }
}

@Composable
private fun ShuffleAllRow(onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.one)) {
        Icon(painterResource(R.drawable.ic_shuffle), null)
        Text(stringResource(R.string.shuffle_all), Modifier.padding(start = spacing.one))
    }
}

@Composable
private fun LetterHeaderRow(letter: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            letter,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = LocalSpacing.current.screen, vertical = LocalSpacing.current.half),
        )
    }
}

/** The A–Z fast-scroll rail: tap or drag along it to jump; a haptic tick marks each letter. */
@Composable
private fun LetterRail(letters: List<String>, onLetter: (String) -> Unit) {
    val haptic = rememberHaptic()
    var last by remember { mutableStateOf<String?>(null) }
    val label = stringResource(R.string.letter_rail)
    fun pick(y: Float, height: Int) {
        if (height <= 0) return
        val index = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != last) {
            last = letter
            haptic(HapticFeedbackType.SegmentTick)
            onLetter(letter)
        }
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(LocalSizes.current.letterRailWidth)
            .semantics { contentDescription = label }
            .pointerInput(letters) { detectTapGestures { pick(it.y, size.height) } }
            .pointerInput(letters) { detectVerticalDragGestures { change, _ -> pick(change.position.y, size.height) } },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(letter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ArtistsTab(vm: LibraryViewModel) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val songCount by vm.songCount.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val state = rememberRestoredListState(LibraryTab.ARTISTS, vm) ?: return
    if (songCount == 0) return EmptyLibrary()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding())) {
        items(artists, key = { it.key }) { artist ->
            ArtistRow(
                artist,
                subtitle = stringResource(R.string.albums_and_songs, artist.albumCount, artist.trackCount),
                onClick = { navigator.go(Routes.artist(artist.key)) },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(vm: LibraryViewModel) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val showMessage = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current
    val sizes = LocalSizes.current
    var creating by rememberSaveable { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = vm.importPlaylist(uri)
            showMessage(
                if (result == null) resources.getString(R.string.import_failed)
                else resources.getString(R.string.imported_playlist, result.name, result.matched, result.skipped),
            )
        }
    }
    val state = rememberRestoredListState(LibraryTab.PLAYLISTS, vm) ?: return
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding())) {
        item(key = "actions") {
            Row(Modifier.padding(horizontal = spacing.screen, vertical = spacing.one), horizontalArrangement = Arrangement.spacedBy(spacing.one)) {
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Text(stringResource(R.string.new_playlist), Modifier.padding(start = spacing.one))
                }
                FilledTonalButton(onClick = { importer.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "application/octet-stream")) }) {
                    Icon(painterResource(R.drawable.ic_file_download), null)
                    Text(stringResource(R.string.import_playlist), Modifier.padding(start = spacing.one))
                }
            }
        }
        item(key = "favorites") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.favorites)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_favorite_filled), null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { navigator.go(Routes.FAVORITES) },
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(stringResource(R.string.song_count, playlist.trackCount)) },
                leadingContent = {
                    ArtImage(playlist.art, sizes.rowArt, playlist.name, Modifier.size(sizes.rowArt))
                },
                modifier = Modifier.clickable { navigator.go(Routes.playlist(playlist.id)) },
            )
        }
    }
    if (creating) NewPlaylistDialog(onCreate = { vm.createPlaylist(it); creating = false }, onDismiss = { creating = false })
}

@Composable
fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit, initial: String = "", title: String = stringResource(R.string.new_playlist)) {
    var name by rememberSaveable { mutableStateOf(initial) }
    PocketDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onCreate(name.trim()) },
        dismissLabel = stringResource(R.string.cancel),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_PLAYLIST_NAME) },
            label = { Text(stringResource(R.string.playlist_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_PLAYLIST_NAME = 100

@Composable
private fun FoldersTab(vm: LibraryViewModel) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val songCount by vm.songCount.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val state = rememberRestoredListState(LibraryTab.FOLDERS, vm) ?: return
    if (songCount == 0) return EmptyLibrary()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding())) {
        items(folders, key = { it.uri }) { folder ->
            ListItem(
                headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(folder.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
                trailingContent = { Text(folder.trackCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { navigator.go(Routes.folder(folder.uri)) },
            )
        }
    }
}
