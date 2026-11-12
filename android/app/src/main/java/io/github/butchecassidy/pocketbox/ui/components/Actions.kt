// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.AudioQuality
import io.github.butchecassidy.pocketbox.domain.model.BitrateMode
import io.github.butchecassidy.pocketbox.domain.model.ReleaseType
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.domain.release.ReleaseClassifier
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which menu, sheet or dialog is open. One instance lives at the app root. */
@Stable
class ActionsState {
    var song by mutableStateOf<Track?>(null)
    var release by mutableStateOf<Album?>(null)
    var info by mutableStateOf<Track?>(null)
    var addToPlaylist by mutableStateOf<List<Long>?>(null)

    fun openSong(track: Track) {
        song = track
    }

    fun openRelease(album: Album) {
        release = album
    }
}

val LocalActions = staticCompositionLocalOf { ActionsState() }

/** Shows a short message at the bottom of the screen. */
val LocalShowMessage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Hosts the song menu, release menu, track info and add-to-playlist, for every screen. */
@Composable
fun ActionsHost(state: ActionsState, onOpenAlbum: (String) -> Unit, onOpenArtist: (String) -> Unit, onOpenBook: (String) -> Unit) {
    state.song?.let { track -> SongActionsSheet(track, state, if (track.isAudiobook) onOpenBook else onOpenAlbum, onOpenArtist) }
    state.release?.let { album -> ReleaseActionsSheet(album, state, onOpenArtist) }
    state.info?.let { track -> TrackInfoSheet(track) { state.info = null } }
    state.addToPlaylist?.let { ids -> AddToPlaylistDialog(ids) { state.addToPlaylist = null } }
}

@Composable
private fun ActionItem(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(painterResource(icon), null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.screen, vertical = spacing.one)) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    HorizontalDivider()
}

@Composable
private fun SongActionsSheet(track: Track, state: ActionsState, onOpenAlbum: (String) -> Unit, onOpenArtist: (String) -> Unit) {
    val container = LocalAppContainer.current
    val showMessage = LocalShowMessage.current
    val favorites by container.library.favoriteIds.collectAsState(initial = emptySet())
    val isFavorite = track.id in favorites
    val playingNext = stringResource(R.string.playing_next)
    val addedToQueue = stringResource(R.string.added_to_queue)
    val close = { state.song = null }
    PocketSheet(onDismiss = close) {
        SheetHeader(track.title, stringResource(R.string.separator_dot, track.artist, track.album))
        // Audiobook chapters live in their book: never in music queues, playlists, favourites, albums or artists.
        if (track.isAudiobook) {
            ActionItem(R.drawable.ic_auto_stories, stringResource(R.string.action_go_to_book)) { close(); onOpenAlbum(track.albumKey) }
        } else {
            ActionItem(R.drawable.ic_queue_play_next, stringResource(R.string.action_play_next)) {
                container.queue.playNext(listOf(track.id)); showMessage(playingNext); close()
            }
            ActionItem(R.drawable.ic_playlist_add, stringResource(R.string.action_add_to_queue)) {
                container.queue.addToQueue(listOf(track.id)); showMessage(addedToQueue); close()
            }
            ActionItem(R.drawable.ic_playlist_play, stringResource(R.string.action_add_to_playlist)) {
                state.addToPlaylist = listOf(track.id); close()
            }
            ActionItem(
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
                stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
            ) {
                container.appScope.launch { container.library.setFavorite(track.id, !isFavorite) }; close()
            }
            ActionItem(R.drawable.ic_album, stringResource(R.string.action_go_to_album)) { close(); onOpenAlbum(track.albumKey) }
            ActionItem(R.drawable.ic_person, stringResource(R.string.action_go_to_artist)) { close(); onOpenArtist(track.artistKey) }
        }
        ActionItem(R.drawable.ic_info, stringResource(R.string.action_track_info)) { state.info = track; close() }
    }
}

@Composable
private fun ReleaseActionsSheet(album: Album, state: ActionsState, onOpenArtist: (String) -> Unit) {
    val container = LocalAppContainer.current
    val showMessage = LocalShowMessage.current
    val playingNext = stringResource(R.string.playing_next)
    val addedToQueue = stringResource(R.string.added_to_queue)
    val close = { state.release = null }
    fun withTracks(block: (List<Long>) -> Unit) = container.appScope.launch {
        block(container.library.albumTracks(album.key).first().map { it.id })
    }
    PocketSheet(onDismiss = close) {
        SheetHeader(album.title, album.albumArtist)
        ActionItem(R.drawable.ic_play_arrow_filled, stringResource(R.string.play)) {
            withTracks { container.queue.play(it, 0) }; close()
        }
        ActionItem(R.drawable.ic_shuffle, stringResource(R.string.shuffle)) {
            withTracks { container.queue.shufflePlay(it) }; close()
        }
        ActionItem(R.drawable.ic_queue_play_next, stringResource(R.string.action_play_next)) {
            withTracks { container.queue.playNext(it); showMessage(playingNext) }; close()
        }
        ActionItem(R.drawable.ic_playlist_add, stringResource(R.string.action_add_to_queue)) {
            withTracks { container.queue.addToQueue(it); showMessage(addedToQueue) }; close()
        }
        ActionItem(R.drawable.ic_playlist_play, stringResource(R.string.action_add_to_playlist)) {
            withTracks { state.addToPlaylist = it }; close()
        }
        ActionItem(R.drawable.ic_person, stringResource(R.string.action_go_to_artist)) { close(); onOpenArtist(album.artistKey) }
        HorizontalDivider()
        Text(
            stringResource(R.string.action_file_as),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = LocalSpacing.current.screen, vertical = LocalSpacing.current.one),
        )
        val automatic = ReleaseClassifier.classify(null, album.trackCount, album.durationMs)
        val options = listOf<Pair<ReleaseType?, String>>(
            null to stringResource(R.string.action_file_as_auto, automatic.label),
            ReleaseType.ALBUM to ReleaseType.ALBUM.label,
            ReleaseType.SINGLE to ReleaseType.SINGLE.label,
            ReleaseType.EP to ReleaseType.EP.label,
        )
        options.forEach { (type, label) ->
            val selected = if (type == null) !album.isTypeOverridden else album.isTypeOverridden && album.releaseType == type
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = selected, onClick = null) },
                modifier = Modifier.clickable {
                    container.appScope.launch { container.library.setReleaseType(album.key, type) }
                    close()
                },
            )
        }
    }
}

/** Full source-quality details for one file. Nothing is guessed: unknowns say so. */
@Composable
fun TrackInfoSheet(track: Track, onDismiss: () -> Unit) {
    PocketSheet(onDismiss = onDismiss) {
        SheetHeader(track.title, stringResource(R.string.separator_dot, track.artist, track.album))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            QualityDetails(track.quality)
            val rg = track.replayGain
            val gain = listOfNotNull(
                rg.trackGainDb?.let { stringResource(R.string.info_gain_track, Format.decibels(it)) },
                rg.albumGainDb?.let { stringResource(R.string.info_gain_album, Format.decibels(it)) },
            ).joinToString(", ")
            InfoRow(stringResource(R.string.info_replaygain), gain.ifEmpty { stringResource(R.string.info_not_stated) })
            InfoRow(stringResource(R.string.info_file), track.fileName)
            InfoRow(stringResource(R.string.info_folder), track.folderPath)
        }
    }
}

/** The source-quality rows, shared by Track info and the Now Playing quality sheet. */
@Composable
fun QualityDetails(quality: AudioQuality) {
    val notStated = stringResource(R.string.info_not_stated)
    val spacing = LocalSpacing.current
    Column {
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = spacing.screen, vertical = spacing.one)) {
            QualityBadge(quality.qualityClass, long = true)
        }
        InfoRow(stringResource(R.string.info_codec), quality.codec.label)
        InfoRow(stringResource(R.string.info_sample_rate), quality.sampleRateHz?.let(Format::sampleRate) ?: notStated)
        if (quality.codec.isLossless == true) {
            InfoRow(stringResource(R.string.info_bit_depth), quality.bitDepth?.let { "$it-bit" } ?: notStated)
        }
        val bitrate = quality.bitrateBps?.let(Format::bitrate)
        InfoRow(
            stringResource(R.string.info_bitrate),
            when {
                bitrate == null -> notStated
                quality.bitrateMode == BitrateMode.CONSTANT -> stringResource(R.string.bitrate_constant, bitrate)
                quality.bitrateMode == BitrateMode.VARIABLE -> stringResource(R.string.bitrate_variable, bitrate)
                quality.bitrateMode == BitrateMode.AVERAGE -> stringResource(R.string.bitrate_average, bitrate)
                else -> bitrate
            },
        )
        InfoRow(
            stringResource(R.string.info_channels),
            when (quality.channels) {
                null -> notStated
                1 -> stringResource(R.string.channels_mono)
                2 -> stringResource(R.string.channels_stereo)
                else -> stringResource(R.string.channels_count, quality.channels)
            },
        )
        InfoRow(stringResource(R.string.info_file_size), Format.fileSize(quality.fileSizeBytes))
        InfoRow(stringResource(R.string.info_duration), Format.duration(quality.durationMs))
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
    )
}

@Composable
private fun AddToPlaylistDialog(trackIds: List<Long>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val showMessage = LocalShowMessage.current
    val playlists by container.playlists.playlists.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    val addedTemplate = stringResource(R.string.added_to_playlist, "%s")
    PocketDialog(
        title = stringResource(R.string.action_add_to_playlist),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.create),
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            container.appScope.launch { container.playlists.create(name, trackIds); showMessage(addedTemplate.format(name.trim())) }
            onDismiss()
        },
        dismissLabel = stringResource(R.string.cancel),
    ) {
        LazyColumn(Modifier.heightIn(max = PLAYLIST_LIST_MAX_HEIGHT)) {
            items(playlists, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    leadingContent = {
                        ArtImage(playlist.art, LocalSizes.current.rowArt, playlist.name, Modifier.size(LocalSizes.current.rowArt))
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        container.appScope.launch { container.playlists.add(playlist.id, trackIds); showMessage(addedTemplate.format(playlist.name)) }
                        onDismiss()
                    },
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME) },
            label = { Text(stringResource(R.string.new_playlist)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = LocalSpacing.current.one),
        )
    }
}

private val PLAYLIST_LIST_MAX_HEIGHT = 280.dp
private const val MAX_NAME = 100
