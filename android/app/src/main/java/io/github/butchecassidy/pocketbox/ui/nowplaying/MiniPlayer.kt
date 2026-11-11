// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.nowplaying

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.playback.PlayerConnection
import io.github.butchecassidy.pocketbox.playback.PlayerState
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing

/** Shared-element key for the album art that grows from the mini player into Now Playing. */
const val NOW_PLAYING_ART_KEY = "now-playing-art"

/**
 * The playback position, read once per display frame and only while it's on screen and playing.
 * When paused, it's read once. Nothing polls in the background.
 */
@Composable
fun rememberPositionMs(player: PlayerConnection, state: PlayerState): State<Long> =
    produceState(player.positionMs(), state.isPlaying, state.currentIndex, state.jumps) {
        value = player.positionMs()
        if (state.isPlaying) {
            while (true) withFrameMillis { value = player.positionMs() }
        }
    }

/** The mini player above the navigation bar. Tapping it expands into Now Playing. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    onExpand: () -> Unit,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val spacing = LocalSpacing.current
    val sizes = LocalSizes.current
    val position by rememberPositionMs(container.player, state)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onExpand),
    ) {
        Column {
            Row(
                Modifier.height(sizes.miniPlayerHeight).padding(horizontal = spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                with(sharedScope) {
                    ArtImage(
                        art = state.art,
                        sizeHint = sizes.rowArt,
                        placeholderKey = state.album,
                        modifier = Modifier
                            .sharedElement(rememberSharedContentState(NOW_PLAYING_ART_KEY), visibilityScope)
                            .size(sizes.rowArt),
                        contentDescription = stringResource(R.string.album_art),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = spacing.oneAndHalf)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = container.player::togglePlay,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(if (state.playWhenReady) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled),
                        stringResource(if (state.playWhenReady) R.string.pause else R.string.play),
                    )
                }
                IconButton(onClick = container.player::next, enabled = state.hasNext, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_skip_next_filled), stringResource(R.string.next))
                }
            }
            Box(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
