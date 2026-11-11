// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.butchecassidy.pocketbox.data.repo.ImportResult
import io.github.butchecassidy.pocketbox.data.scan.ScanState
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.AlbumSort
import io.github.butchecassidy.pocketbox.domain.model.Artist
import io.github.butchecassidy.pocketbox.domain.model.LibraryRoot
import io.github.butchecassidy.pocketbox.domain.model.LibraryTab
import io.github.butchecassidy.pocketbox.domain.model.MusicFolder
import io.github.butchecassidy.pocketbox.domain.model.Playlist
import io.github.butchecassidy.pocketbox.domain.model.ScrollPosition
import io.github.butchecassidy.pocketbox.domain.model.SongSort
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Songs grouped under their letter header, with each header's position for the letter rail. */
data class SongSections(
    val tracks: ImmutableList<Track> = persistentListOf(),
    val headers: ImmutableList<LetterHeader> = persistentListOf(),
)

/** A letter and the index of its first song within [SongSections.tracks]. */
data class LetterHeader(val letter: String, val firstTrackIndex: Int)

/** Everything the library tabs show. Each list is computed off the main thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val library = container.library
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)

    val settings: StateFlow<UserSettings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    private val settingsFlow = container.settings.settings

    val albums: StateFlow<ImmutableList<Album>> = settingsFlow.map { it.albumSort }.distinctUntilChanged()
        .flatMapLatest { library.albums(it) }.immutable()

    val singles: StateFlow<ImmutableList<Album>> = settingsFlow.map { it.albumSort }.distinctUntilChanged()
        .flatMapLatest { library.singlesAndEps(it) }.immutable()

    val songs: StateFlow<SongSections> = settingsFlow.map { it.songSort }.distinctUntilChanged()
        .flatMapLatest { sort -> library.songs(sort).map { tracks -> sections(tracks, sort) } }
        .flowOn(container.cpu)
        .stateIn(viewModelScope, started, SongSections())

    val artists: StateFlow<ImmutableList<Artist>> = library.artists.immutable()
    val playlists: StateFlow<ImmutableList<Playlist>> = container.playlists.playlists.immutable()
    val folders: StateFlow<ImmutableList<MusicFolder>> = library.folders.immutable()

    val recentlyPlayed = settingsFlow.map { it.showRecentlyPlayed }.distinctUntilChanged()
        .flatMapLatest { if (it) library.recentlyPlayed() else flowOf(emptyList()) }.immutable()
    val mostPlayed = settingsFlow.map { it.showMostPlayed }.distinctUntilChanged()
        .flatMapLatest { if (it) library.mostPlayed() else flowOf(emptyList()) }.immutable()
    val recentlyAdded = settingsFlow.map { it.showRecentlyAdded }.distinctUntilChanged()
        .flatMapLatest { if (it) library.recentlyAdded() else flowOf(emptyList()) }.immutable()

    val songCount: StateFlow<Int?> = library.songCount.stateIn(viewModelScope, started, null)
    val scanState: StateFlow<ScanState> = container.scanner.state
    val lostRoots: StateFlow<ImmutableList<LibraryRoot>> =
        container.scanner.roots.map { roots -> roots.filterNot { it.isAccessible } }.immutable()

    /** The tab to reopen on: the last one used, or Albums on the very first launch. */
    val initialTab: StateFlow<LibraryTab?> = combine(container.settings.lastTab, settingsFlow) { last, s ->
        last?.takeIf { it in s.visibleTabs } ?: s.visibleTabs.firstOrNull { it == LibraryTab.ALBUMS } ?: s.visibleTabs.first()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onTabSelected(tab: LibraryTab) {
        viewModelScope.launch { container.settings.setLastTab(tab) }
    }

    suspend fun scrollPosition(tab: LibraryTab): ScrollPosition? = container.settings.scrollPosition(tab)

    fun saveScroll(tab: LibraryTab, position: ScrollPosition) {
        container.appScope.launch { container.settings.setScrollPosition(tab, position) }
    }

    fun setAlbumsAsGrid(grid: Boolean) = viewModelScope.launch { container.settings.update { it.copy(albumsAsGrid = grid) } }

    fun setAlbumSort(sort: AlbumSort) =
        viewModelScope.launch { container.settings.update { it.copy(albumSort = sort) } }

    fun setSongSort(sort: SongSort) = viewModelScope.launch { container.settings.update { it.copy(songSort = sort) } }

    /** Re-grants a folder whose access was lost, then rescans. */
    fun regrant(uri: Uri) = container.appScope.launch {
        if (container.scanner.addFolder(uri)) container.scanner.scan()
    }

    fun removeRoot(id: Long) = container.appScope.launch { container.scanner.removeFolder(id) }

    fun createPlaylist(name: String) = viewModelScope.launch { container.playlists.create(name) }

    suspend fun importPlaylist(uri: Uri): ImportResult? = container.playlists.import(uri)

    private fun <T> Flow<List<T>>.immutable(): StateFlow<ImmutableList<T>> =
        map { it.toImmutableList() }.flowOn(container.cpu).stateIn(viewModelScope, started, persistentListOf())

    private fun sections(tracks: List<Track>, sort: SongSort): SongSections {
        if (sort != SongSort.TITLE) return SongSections(tracks.toImmutableList(), persistentListOf())
        val headers = ArrayList<LetterHeader>()
        var last: String? = null
        tracks.forEachIndexed { index, track ->
            val letter = track.sortLetter
            if (letter != last) {
                headers += LetterHeader(letter, index)
                last = letter
            }
        }
        return SongSections(tracks.toImmutableList(), headers.toImmutableList())
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
