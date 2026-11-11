// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.playback.BitPerfectStatus
import io.github.butchecassidy.pocketbox.playback.OutputInfo
import io.github.butchecassidy.pocketbox.playback.OutputKind
import io.github.butchecassidy.pocketbox.playback.SleepTimerState
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.DragHandle
import io.github.butchecassidy.pocketbox.ui.components.InfoRow
import io.github.butchecassidy.pocketbox.ui.components.PocketSheet
import io.github.butchecassidy.pocketbox.ui.components.QualityDetails
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.components.rememberReorderState
import io.github.butchecassidy.pocketbox.ui.components.reorderItem
import io.github.butchecassidy.pocketbox.ui.library.NewPlaylistDialog
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The queue in play order: tap to jump, drag to reorder, swipe to remove; clear, save, undo. */
@Composable
fun QueueSheet(onDismiss: () -> Unit, onSaveAsPlaylist: (String) -> Unit) {
    val container = LocalAppContainer.current
    val queueController = container.queue
    val queue by container.player.queue.collectAsStateWithLifecycle()
    val state by container.player.state.collectAsStateWithLifecycle()
    val canUndo by queueController.canUndo.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val sizes = LocalSizes.current
    var saving by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val reorder = rememberReorderState(list) { from, to -> queueController.move(from, to) }
    PocketSheet(onDismiss = onDismiss, fullHeight = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.up_next), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (canUndo) TextButton(onClick = queueController::undo) { Text(stringResource(R.string.undo)) }
            TextButton(onClick = { saving = true }, enabled = queue.entries.isNotEmpty()) { Text(stringResource(R.string.save_as_playlist)) }
            TextButton(onClick = queueController::clear, enabled = queue.entries.isNotEmpty()) { Text(stringResource(R.string.clear_queue)) }
        }
        if (queue.entries.isEmpty()) {
            Text(stringResource(R.string.queue_empty), Modifier.padding(spacing.screen))
        }
        LazyColumn(Modifier.fillMaxHeight(), state = list) {
            itemsIndexed(queue.playOrder, key = { _, index -> queue.entries.getOrNull(index)?.let { "${it.trackId}-$index" } ?: index }) { position, index ->
                val entry = queue.entries.getOrNull(index) ?: return@itemsIndexed
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = dismiss,
                    backgroundContent = {},
                    onDismiss = { queueController.remove(index) },
                    modifier = Modifier.reorderItem(reorder, position),
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                entry.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (index == state.currentIndex) FontWeight.Bold else null,
                                color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { ArtImage(entry.art, sizes.rowArt, entry.title, Modifier.size(sizes.rowArt)) },
                        trailingContent = { DragHandle(reorder, position) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable { container.player.skipTo(index) },
                    )
                }
            }
        }
    }
    if (saving) {
        NewPlaylistDialog(
            title = stringResource(R.string.save_as_playlist),
            onCreate = { onSaveAsPlaylist(it); saving = false },
            onDismiss = { saving = false },
        )
    }
}

/** Full-height lyrics with larger text; tap a line to jump there. */
@Composable
fun LyricsSheet(lyrics: LyricsState, positionMs: Long, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    PocketSheet(onDismiss = onDismiss, fullHeight = true) {
        LyricsView(lyrics, positionMs, onSeek, large = true, modifier = Modifier.fillMaxHeight())
    }
}

/** Source quality (from the file) and output quality (what reaches the speaker/DAC), side by side. */
@Composable
fun QualitySheet(track: Track?, output: OutputInfo, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    PocketSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.info_source_quality))
            track?.let { QualityDetails(it.quality) }
            SectionHeader(stringResource(R.string.output_title))
            InfoRow(stringResource(R.string.output_device), output.deviceName)
            if (output.kind == OutputKind.BLUETOOTH) InfoRow(stringResource(R.string.bt_codec), stringResource(R.string.bt_codec_not_exposed))
            InfoRow(
                stringResource(R.string.output_app_format),
                listOfNotNull(output.appSampleRateHz?.let(Format::sampleRate), output.appEncoding).joinToString(" · ")
                    .ifEmpty { stringResource(R.string.info_not_stated) },
            )
            InfoRow(
                stringResource(R.string.output_mixer),
                output.mixerSampleRateHz?.let(Format::sampleRate) ?: stringResource(R.string.output_mixer_unknown),
            )
            InfoRow(
                stringResource(R.string.output_bit_perfect),
                when (val status = output.bitPerfect) {
                    BitPerfectStatus.Off -> stringResource(R.string.bit_perfect_off)
                    BitPerfectStatus.NoUsbDevice -> stringResource(R.string.bit_perfect_no_usb)
                    is BitPerfectStatus.Unsupported -> status.reason
                    is BitPerfectStatus.Active -> stringResource(R.string.bit_perfect_active, Format.sampleRate(status.sampleRateHz), status.encoding)
                },
            )
            SectionHeader(stringResource(R.string.resampled_title))
            Text(
                stringResource(R.string.resampled_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.one),
            )
        }
    }
}

/** Minutes or end of song, with an optional fade-out. */
@Composable
fun SleepTimerSheet(vm: NowPlayingViewModel, onDismiss: () -> Unit) {
    val player = LocalAppContainer.current.player
    val timer by vm.sleepTimer.collectAsStateWithLifecycle()
    var fade by rememberSaveable { mutableStateOf(true) }
    val spacing = LocalSpacing.current
    PocketSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = spacing.screen))
        val status = when (val t = timer) {
            SleepTimerState.Off -> null
            is SleepTimerState.Timed -> stringResource(R.string.timer_until, DateFormat.getTimeInstance(DateFormat.SHORT, LocalConfiguration.current.locales[0]).format(Date(t.endsAtMs)))
            is SleepTimerState.EndOfTrack -> stringResource(R.string.timer_end_of_track_on)
        }
        if (status != null) Text(status, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.one))
        FlowRow(
            Modifier.padding(horizontal = spacing.screen, vertical = spacing.one),
            horizontalArrangement = Arrangement.spacedBy(spacing.one),
        ) {
            TIMER_MINUTES.forEach { minutes ->
                FilterChip(selected = false, onClick = { player.setSleepTimer(minutes, endOfTrack = false, fadeOut = fade); onDismiss() }, label = { Text(stringResource(R.string.minutes, minutes)) })
            }
            FilterChip(
                selected = timer is SleepTimerState.EndOfTrack,
                onClick = { player.setSleepTimer(0, endOfTrack = true, fadeOut = fade); onDismiss() },
                label = { Text(stringResource(R.string.end_of_track)) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.fade_out)) },
            trailingContent = { Switch(checked = fade, onCheckedChange = { fade = it }) },
        )
        if (timer != SleepTimerState.Off) {
            OutlinedButton(onClick = { player.cancelSleepTimer(); onDismiss() }, modifier = Modifier.padding(spacing.screen)) {
                Text(stringResource(R.string.timer_off))
            }
        }
    }
}

/** Playback speed and pitch, saved in settings so they survive restarts. */
@Composable
fun SpeedSheet(speed: Float, pitch: Float, bitPerfect: Boolean, onChange: (Float, Float) -> Unit, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    var newSpeed by rememberSaveable { mutableFloatStateOf(speed) }
    var newPitch by rememberSaveable { mutableFloatStateOf(pitch) }
    PocketSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = spacing.screen)) {
            Text(stringResource(R.string.speed_and_pitch), style = MaterialTheme.typography.titleLarge)
            if (bitPerfect) {
                Text(stringResource(R.string.speed_off_bit_perfect), modifier = Modifier.padding(vertical = spacing.one))
            } else {
                Text(stringResource(R.string.speed, String.format(Locale.ROOT, "%.2f", newSpeed)), modifier = Modifier.padding(top = spacing.two))
                Slider(value = newSpeed, onValueChange = { newSpeed = it }, valueRange = 0.5f..2f, steps = SPEED_STEPS, onValueChangeFinished = { onChange(newSpeed, newPitch) })
                Text(stringResource(R.string.pitch, String.format(Locale.ROOT, "%.2f", newPitch)))
                Slider(value = newPitch, onValueChange = { newPitch = it }, valueRange = 0.5f..2f, steps = SPEED_STEPS, onValueChangeFinished = { onChange(newSpeed, newPitch) })
                Button(
                    onClick = { newSpeed = 1f; newPitch = 1f; onChange(1f, 1f) },
                    modifier = Modifier.padding(vertical = spacing.two),
                ) { Text(stringResource(R.string.reset)) }
            }
        }
    }
}

private val TIMER_MINUTES = listOf(15, 30, 45, 60, 90)
private const val SPEED_STEPS = 29
