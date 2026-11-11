// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.nowplaying

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.RepeatMode
import io.github.butchecassidy.pocketbox.playback.PlayerState
import io.github.butchecassidy.pocketbox.playback.QueueState
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.pocketViewModel
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing

/** Which Now Playing sheet is open. */
private enum class NowPlayingSheet { QUEUE, LYRICS, QUALITY, SLEEP, SPEED }

/**
 * Now Playing. In portrait everything shares one vertical centre line: the art (84 % of the width,
 * capped to fit the height), title, quality chips, a progress bar exactly as wide as the art, the
 * control group with play/pause dead centre, and the secondary row. In landscape: art on the left,
 * everything else centred on the right. Nothing moves sideways when songs or titles change.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(onCollapse: () -> Unit, sharedScope: SharedTransitionScope, visibilityScope: AnimatedVisibilityScope) {
    val container = LocalAppContainer.current
    val vm = pocketViewModel { NowPlayingViewModel(it) }
    val state by container.player.state.collectAsStateWithLifecycle()
    val queue by container.player.queue.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val position by rememberPositionMs(container.player, state)
    var sheet by rememberSaveable { mutableStateOf<NowPlayingSheet?>(null) }
    val spacing = LocalSpacing.current
    val player = container.player
    val neighbours = remember(queue, state.currentIndex, state.repeat) { neighbours(queue, state) }
    val canGoBack = neighbours.previousKey != null
    val canGoForward = neighbours.nextKey != null

    val art = @Composable { size: Dp ->
        with(sharedScope) {
            ArtStage(
                art = state.art,
                artKey = state.album,
                size = size,
                isPlaying = state.isPlaying,
                neighbours = neighbours,
                hasPrevious = canGoBack,
                hasNext = canGoForward,
                onPrevious = player::previous,
                onNext = player::next,
                lyrics = { LyricsView(lyrics, position, player::seekTo, large = false) },
                artModifier = Modifier.sharedElement(rememberSharedContentState(NOW_PLAYING_ART_KEY), visibilityScope),
            )
        }
    }
    val details = @Composable { width: Dp ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.two)) {
            Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
            }
            QualityChips(track, output) { sheet = NowPlayingSheet.QUALITY }
            SeekBar(position, state.durationMs, state.isPlaying, width, player::seekTo)
            PlayerControls(
                isPlaying = state.playWhenReady,
                hasPrevious = true,
                hasNext = canGoForward,
                onPrevious = player::previous,
                onToggle = player::togglePlay,
                onNext = player::next,
            )
            SecondaryControls(
                shuffle = state.shuffle,
                repeat = state.repeat,
                favorite = favorite,
                onShuffle = { player.setShuffle(!state.shuffle) },
                onRepeat = player::cycleRepeat,
                onFavorite = vm::toggleFavorite,
                onLyrics = { sheet = NowPlayingSheet.LYRICS },
                onQueue = { sheet = NowPlayingSheet.QUEUE },
                width = width,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        NowPlayingBackground(state.art, state.album, settings.nowPlayingStyle)
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopRow(onCollapse, onSheet = { sheet = it }, trackAlbumKey = track?.albumKey, trackArtistKey = track?.artistKey)
            NowPlayingLayout(art, details)
        }
    }

    when (sheet) {
        NowPlayingSheet.QUEUE -> QueueSheet(onDismiss = { sheet = null }, onSaveAsPlaylist = vm::saveQueueAsPlaylist)
        NowPlayingSheet.LYRICS -> LyricsSheet(lyrics, position, player::seekTo) { sheet = null }
        NowPlayingSheet.QUALITY -> QualitySheet(track, output) { sheet = null }
        NowPlayingSheet.SLEEP -> SleepTimerSheet(vm) { sheet = null }
        NowPlayingSheet.SPEED -> SpeedSheet(settings.playbackSpeed, settings.playbackPitch, settings.bitPerfect, vm::setSpeedAndPitch) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun TopRow(
    onCollapse: () -> Unit,
    onSheet: (NowPlayingSheet) -> Unit,
    trackAlbumKey: String?,
    trackArtistKey: String?,
) {
    val navigator = LocalNavigator.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.one), verticalAlignment = Alignment.CenterVertically) {
        PocketIconButton(R.drawable.ic_expand_more, stringResource(R.string.collapse), onCollapse)
        Spacer(Modifier.weight(1f))
        Box {
            PocketIconButton(R.drawable.ic_more_vert, stringResource(R.string.more_options), { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sleep_timer)) }, onClick = { menuOpen = false; onSheet(NowPlayingSheet.SLEEP) })
                DropdownMenuItem(text = { Text(stringResource(R.string.speed_and_pitch)) }, onClick = { menuOpen = false; onSheet(NowPlayingSheet.SPEED) })
                DropdownMenuItem(text = { Text(stringResource(R.string.equalizer)) }, onClick = { menuOpen = false; onCollapse(); navigator.go(Routes.EQUALIZER) })
                DropdownMenuItem(text = { Text(stringResource(R.string.audio_quality)) }, onClick = { menuOpen = false; onSheet(NowPlayingSheet.QUALITY) })
                if (trackAlbumKey != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_go_to_album)) }, onClick = { menuOpen = false; onCollapse(); navigator.go(Routes.album(trackAlbumKey)) })
                }
                if (trackArtistKey != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_go_to_artist)) }, onClick = { menuOpen = false; onCollapse(); navigator.go(Routes.artist(trackArtistKey)) })
                }
            }
        }
    }
}

/** The covers before and after the current one in play order, respecting shuffle and repeat. */
private fun neighbours(queue: QueueState, state: PlayerState): ArtNeighbours {
    val order = queue.playOrder
    val position = order.indexOf(state.currentIndex)
    if (position < 0) return ArtNeighbours(null, null, null, null)
    val wrap = state.repeat == RepeatMode.ALL
    val previousIndex = order.getOrNull(position - 1) ?: if (wrap) order.lastOrNull() else null
    val nextIndex = order.getOrNull(position + 1) ?: if (wrap) order.firstOrNull() else null
    val previous = previousIndex?.let { queue.entries.getOrNull(it) }
    val next = nextIndex?.let { queue.entries.getOrNull(it) }
    return ArtNeighbours(previous?.art, previous?.let { "q-${it.index}" }, next?.art, next?.let { "q-${it.index}" })
}

