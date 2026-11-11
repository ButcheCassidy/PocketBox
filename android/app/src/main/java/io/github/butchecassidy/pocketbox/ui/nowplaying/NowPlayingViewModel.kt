// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.domain.model.Lyrics
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.playback.OutputInfo
import io.github.butchecassidy.pocketbox.playback.SleepTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lyrics for the current song: still loading, found, or none. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

/** The full details behind Now Playing: the playing track from the library, lyrics, output, timers. */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(private val container: AppContainer) : ViewModel() {
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS)
    private val trackId = container.player.state.map { it.currentTrackId }.distinctUntilChanged()

    val track: StateFlow<Track?> = trackId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.library.track(id) }
        .stateIn(viewModelScope, started, null)

    val lyrics: StateFlow<LyricsState> = track
        .map { it?.id to it }
        .distinctUntilChanged { a, b -> a.first == b.first }
        .mapLatest { (_, current) ->
            val found = current?.let { container.lyrics.load(it) }
            if (found == null) LyricsState.None else LyricsState.Found(found)
        }
        .stateIn(viewModelScope, started, LyricsState.Loading)

    /** The current song's lyrics timing fix (positive: lyrics earlier). */
    val lyricOffset: StateFlow<Long> = trackId
        .flatMapLatest { id -> if (id == null) flowOf(0L) else container.lyrics.offset(id) }
        .stateIn(viewModelScope, started, 0L)

    val isFavorite: StateFlow<Boolean> = combine(trackId, container.library.favoriteIds) { id, favorites -> id != null && id in favorites }
        .stateIn(viewModelScope, started, false)

    val output: StateFlow<OutputInfo> = container.outputMonitor.info
    val sleepTimer: StateFlow<SleepTimerState> = container.sleepTimerState
    val settings: StateFlow<UserSettings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun toggleFavorite() {
        val id = track.value?.id ?: return
        val favorite = isFavorite.value
        viewModelScope.launch { container.library.setFavorite(id, !favorite) }
    }

    fun nudgeLyrics(deltaMs: Long) {
        val id = track.value?.id ?: return
        container.appScope.launch { container.lyrics.adjustOffset(id, deltaMs) }
    }

    fun resetLyricsTiming() {
        val id = track.value?.id ?: return
        container.appScope.launch { container.lyrics.adjustOffset(id, 0, reset = true) }
    }

    /** Saves the playing chapter's spot as a bookmark; returns false when a song (not a book) plays. */
    fun addBookmark(positionMs: Long): Boolean {
        val current = track.value?.takeIf { it.isAudiobook } ?: return false
        container.appScope.launch { container.books.addBookmark(current.albumKey, current.id, positionMs) }
        return true
    }

    fun saveQueueAsPlaylist(name: String) {
        val ids = container.player.queue.value.entries.map { it.trackId }.filter { it >= 0 }
        viewModelScope.launch { container.playlists.create(name, ids) }
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        viewModelScope.launch { container.settings.update { it.copy(playbackSpeed = speed, playbackPitch = pitch) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
