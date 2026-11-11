// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.media3.common.Player
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.playback.MediaItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the home-screen widget shows. */
data class WidgetSnapshot(
    val title: String,
    val artist: String,
    val art: ArtRef?,
    val isPlaying: Boolean,
)

/** Copies the playing song and play/pause state from the service's player into [target]. */
class WidgetStatePublisher(private val target: MutableStateFlow<WidgetSnapshot?>) : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
        if (!events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) return
        val metadata = player.currentMediaItem?.mediaMetadata
        target.value = metadata?.let {
            WidgetSnapshot(
                title = it.title?.toString().orEmpty(),
                artist = it.artist?.toString().orEmpty(),
                art = MediaItems.artRef(it),
                isPlaying = player.playWhenReady,
            )
        }
    }

    /** The service is going away: keep showing the song, but as paused. */
    fun onServiceStopped() {
        target.value = target.value?.copy(isPlaying = false)
    }
}

/**
 * Redraws every placed widget when the song, play state or look changes. Updates are debounced so
 * a burst of events (skip, then play) costs one redraw. Does nothing while no widget is placed.
 */
class WidgetRefresher(private val context: Context, private val scope: CoroutineScope) {
    @OptIn(FlowPreview::class)
    fun start(state: Flow<WidgetSnapshot?>, settings: Flow<UserSettings>) {
        val look = settings.map { listOf(it.themeMode, it.amoledBlack, it.colorSource, it.customSeedColor) }.distinctUntilChanged()
        scope.launch {
            combine(state, look) { snapshot, appearance -> snapshot to appearance }
                .distinctUntilChanged()
                .drop(1) // the first value is what the widget already shows
                .debounce(REFRESH_DEBOUNCE_MS)
                .collect { NowPlayingWidget().updateAll(context) }
        }
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 250L
    }
}
