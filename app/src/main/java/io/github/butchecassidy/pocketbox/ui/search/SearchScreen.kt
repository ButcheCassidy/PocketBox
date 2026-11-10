// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.data.repo.RankedTrack
import io.github.butchecassidy.pocketbox.data.repo.SearchResults
import io.github.butchecassidy.pocketbox.data.repo.TopResult
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.LibraryTab
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.LocalChromePadding
import io.github.butchecassidy.pocketbox.ui.components.AlbumRow
import io.github.butchecassidy.pocketbox.ui.components.ArtistRow
import io.github.butchecassidy.pocketbox.ui.components.EmptyState
import io.github.butchecassidy.pocketbox.ui.components.LocalActions
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.components.TrackRow
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.pocketViewModel
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/** Search as you type: an expressive search bar that expands into full-screen search. */
@Composable
fun SearchScreen() {
    val vm = pocketViewModel { SearchViewModel(it) }
    val text = rememberTextFieldState()
    val barState = rememberSearchBarState(initialValue = SearchBarValue.Expanded)
    val scope = rememberCoroutineScope()

    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(vm::onQueryChanged) }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = barState,
            onSearch = vm::remember,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (text.text.isNotEmpty()) {
                    PocketIconButton(R.drawable.ic_close, stringResource(R.string.clear_search), { text.clearText() })
                }
            },
        )
    }
    Scaffold(
        topBar = { TopSearchBar(state = barState, inputField = inputField) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        SearchBody(vm, Modifier.padding(padding), onPickRecent = { recent ->
            text.edit { replace(0, length, recent) }
            scope.launch { barState.animateToExpanded() }
        })
    }
    ExpandedFullScreenSearchBar(state = barState, inputField = inputField) {
        SearchBody(vm, Modifier, onPickRecent = { recent -> text.edit { replace(0, length, recent) } })
    }
}

@Composable
private fun SearchBody(vm: SearchViewModel, modifier: Modifier, onPickRecent: (String) -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    if (results.query.isBlank()) {
        EmptySearch(vm, modifier, onPickRecent)
    } else if (results.isEmpty) {
        EmptyState(
            R.drawable.ic_search,
            stringResource(R.string.no_results, results.query),
            stringResource(R.string.no_results_body),
            modifier,
        )
    } else {
        Results(vm, results, modifier)
    }
}

/** With an empty box: recent searches (clearable) and quick ways into the library. */
@Composable
private fun EmptySearch(vm: SearchViewModel, modifier: Modifier, onPickRecent: (String) -> Unit) {
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current
    fun openTab(tab: LibraryTab) = scope.launch {
        container.settings.setLastTab(tab)
        navigator.topLevel(Routes.LIBRARY)
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding())) {
        item(key = "quick") {
            FlowRow(
                Modifier.padding(horizontal = spacing.screen, vertical = spacing.one),
                horizontalArrangement = Arrangement.spacedBy(spacing.one),
            ) {
                AssistChip(onClick = { openTab(LibraryTab.SONGS) }, label = { Text(stringResource(R.string.quick_all_songs)) })
                AssistChip(onClick = { openTab(LibraryTab.ALBUMS) }, label = { Text(stringResource(R.string.quick_all_albums)) })
                AssistChip(onClick = { openTab(LibraryTab.ARTISTS) }, label = { Text(stringResource(R.string.quick_all_artists)) })
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent-header") {
                Row {
                    SectionHeader(stringResource(R.string.recent_searches), Modifier.weight(1f))
                    TextButton(onClick = vm::clearRecent, Modifier.padding(end = spacing.one)) {
                        Text(stringResource(R.string.clear_recent))
                    }
                }
            }
            items(recent, key = { "recent-$it" }) { query ->
                ListItem(
                    headlineContent = { Text(query) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_history), null) },
                    modifier = Modifier.clickable { onPickRecent(query) },
                )
            }
        }
    }
}

@Composable
private fun Results(vm: SearchViewModel, results: SearchResults, modifier: Modifier) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalChromePadding.current.calculateBottomPadding())) {
        item(key = "filters") {
            Row(Modifier.padding(horizontal = spacing.screen, vertical = spacing.half), horizontalArrangement = Arrangement.spacedBy(spacing.one)) {
                SearchFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { vm.filter.value = option },
                        label = { Text(filterLabel(option)) },
                    )
                }
            }
        }
        val all = filter == SearchFilter.ALL
        if (all) results.top?.let { top -> topResult(top, results, vm) }
        if (all || filter == SearchFilter.SONGS) songs(results, vm, limit = if (all) PREVIEW else Int.MAX_VALUE)
        if (all || filter == SearchFilter.ALBUMS) {
            releases(R.string.albums_section, results.albums, "albums", if (all) PREVIEW else Int.MAX_VALUE, vm, results.query)
            releases(R.string.singles_section, results.singles, "singles", if (all) PREVIEW else Int.MAX_VALUE, vm, results.query)
        }
        if (all || filter == SearchFilter.ARTISTS) artists(results, vm, if (all) PREVIEW else Int.MAX_VALUE)
        if (all && results.closeMatches.isNotEmpty()) {
            item(key = "close-header") { SectionHeader(stringResource(R.string.close_matches)) }
            items(results.closeMatches, key = { "close-${it.id}" }) { track ->
                val container = LocalAppContainer.current
                val actions = LocalActions.current
                TrackRow(track, onClick = {
                    vm.remember(results.query)
                    container.queue.play(results.closeMatches.map { it.id }, results.closeMatches.indexOf(track))
                }, onMenu = { actions.openSong(track) })
            }
        }
    }
}

@Composable
private fun filterLabel(filter: SearchFilter) = stringResource(
    when (filter) {
        SearchFilter.ALL -> R.string.filter_all
        SearchFilter.SONGS -> R.string.songs
        SearchFilter.ALBUMS -> R.string.albums_section
        SearchFilter.ARTISTS -> R.string.artists
    },
)

/** The title with the matched letters in bold, in the theme's primary colour. */
@Composable
private fun highlighted(result: RankedTrack): AnnotatedString {
    val style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return buildAnnotatedString {
        append(result.track.title)
        result.highlights.forEach { range -> addStyle(style, range.first, range.last + 1) }
    }
}

private fun LazyListScope.topResult(top: TopResult, results: SearchResults, vm: SearchViewModel) {
    item(key = "top") {
        val container = LocalAppContainer.current
        val navigator = LocalNavigator.current
        val actions = LocalActions.current
        SectionHeader(stringResource(R.string.top_result))
        when (top) {
            is TopResult.Song -> TrackRow(
                top.result.track,
                title = highlighted(top.result),
                onClick = {
                    vm.remember(results.query)
                    container.queue.play(results.songs.map { it.track.id }, results.songs.indexOf(top.result))
                },
                onMenu = { actions.openSong(top.result.track) },
            )
            is TopResult.Release -> AlbumRow(
                top.album,
                onClick = { vm.remember(results.query); navigator.go(Routes.album(top.album.key)) },
                onLongClick = { actions.openRelease(top.album) },
            )
            is TopResult.Person -> ArtistRow(
                top.artist,
                subtitle = stringResource(R.string.result_artist),
                onClick = { vm.remember(results.query); navigator.go(Routes.artist(top.artist.key)) },
            )
        }
    }
}

private fun LazyListScope.songs(results: SearchResults, vm: SearchViewModel, limit: Int) {
    if (results.songs.isEmpty()) return
    item(key = "songs-header") {
        SectionHeader(stringResource(R.string.songs), onSeeAll = if (results.songs.size > limit) ({ vm.filter.value = SearchFilter.SONGS }) else null)
    }
    itemsIndexed(results.songs.take(limit), key = { _, r -> "song-${r.track.id}" }) { index, result ->
        val container = LocalAppContainer.current
        val actions = LocalActions.current
        TrackRow(
            result.track,
            title = highlighted(result),
            onClick = {
                vm.remember(results.query)
                // The queue becomes the search results, in their current order.
                container.queue.play(results.songs.map { it.track.id }, index)
            },
            onMenu = { actions.openSong(result.track) },
        )
    }
}

private fun LazyListScope.releases(title: Int, albums: List<Album>, key: String, limit: Int, vm: SearchViewModel, query: String) {
    if (albums.isEmpty()) return
    item(key = "$key-header") {
        SectionHeader(stringResource(title), onSeeAll = if (albums.size > limit) ({ vm.filter.value = SearchFilter.ALBUMS }) else null)
    }
    items(albums.take(limit), key = { "$key-${it.key}" }) { album ->
        val navigator = LocalNavigator.current
        val actions = LocalActions.current
        AlbumRow(album, onClick = { vm.remember(query); navigator.go(Routes.album(album.key)) }, onLongClick = { actions.openRelease(album) })
    }
}

private fun LazyListScope.artists(results: SearchResults, vm: SearchViewModel, limit: Int) {
    if (results.artists.isEmpty()) return
    item(key = "artists-header") {
        SectionHeader(stringResource(R.string.artists), onSeeAll = if (results.artists.size > limit) ({ vm.filter.value = SearchFilter.ARTISTS }) else null)
    }
    items(results.artists.take(limit), key = { "artist-${it.key}" }) { artist ->
        val navigator = LocalNavigator.current
        ArtistRow(
            artist,
            subtitle = stringResource(R.string.albums_and_songs, artist.albumCount, artist.trackCount),
            onClick = { vm.remember(results.query); navigator.go(Routes.artist(artist.key)) },
        )
    }
}

private const val PREVIEW = 4
