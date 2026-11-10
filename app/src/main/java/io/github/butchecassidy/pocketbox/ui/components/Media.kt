// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.Album
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.Artist
import io.github.butchecassidy.pocketbox.domain.model.QualityClass
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.theme.LocalArtShape
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlin.math.absoluteValue

/**
 * Album art at a fixed size, decoded at that size from the shared cache. When there is no art, a
 * generated placeholder takes exactly the same size, shape and position, so nothing ever jumps.
 */
@Composable
fun ArtImage(
    art: ArtRef?,
    sizeHint: Dp,
    placeholderKey: String,
    modifier: Modifier = Modifier,
    shape: Shape = LocalArtShape.current,
    contentDescription: String? = null,
) {
    val repository = LocalAppContainer.current.artwork
    val px = with(LocalDensity.current) { sizeHint.roundToPx() }
    val bitmap by produceState(art?.let { repository.cached(it, px) }, art, px) {
        if (art == null) value = null else if (value == null) value = repository.load(art, px)
    }
    val colors = MaterialTheme.colorScheme
    val (container, onContainer) = when (placeholderKey.hashCode().absoluteValue % 3) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Box(modifier.clip(shape).background(container), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            val imageBitmap = remember(image) { image.asImageBitmap() }
            Image(imageBitmap, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = contentDescription,
                tint = onContainer,
                modifier = Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION),
            )
        }
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.4f

/** The Lossy / Lossless / Hi-Res badge. */
@Composable
fun QualityBadge(quality: QualityClass?, modifier: Modifier = Modifier, long: Boolean = false) {
    if (quality == null) return
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (quality) {
        QualityClass.HI_RES_LOSSLESS -> colors.tertiaryContainer to colors.onTertiaryContainer
        QualityClass.LOSSLESS -> colors.secondaryContainer to colors.onSecondaryContainer
        QualityClass.LOSSY -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    val label = when (quality) {
        QualityClass.HI_RES_LOSSLESS -> stringResource(if (long) R.string.badge_hires_lossless else R.string.badge_hires)
        QualityClass.LOSSLESS -> stringResource(R.string.badge_lossless)
        QualityClass.LOSSY -> stringResource(R.string.badge_lossy)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = LocalSpacing.current.one, vertical = LocalSpacing.current.half / 2),
        )
    }
}

/**
 * The one song row used everywhere: art (or a track number), title, "artist · album", quality badge
 * and a ⋮ menu. Long-press opens the same menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    title: AnnotatedString = AnnotatedString(track.title),
    subtitle: String = stringResource(R.string.separator_dot, track.artist, track.album),
    trackNumber: Int? = null,
    isCurrent: Boolean = false,
) {
    val sizes = LocalSizes.current
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                QualityBadge(track.quality.qualityClass, Modifier.padding(start = LocalSpacing.current.one))
            }
        },
        leadingContent = {
            if (trackNumber != null) {
                Box(Modifier.size(sizes.rowArt), contentAlignment = Alignment.Center) {
                    Text(trackNumber.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ArtImage(track.art, sizes.rowArt, track.albumKey, Modifier.size(sizes.rowArt))
            }
        },
        trailingContent = {
            IconButton(onClick = onMenu) {
                Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onMenu, role = Role.Button),
    )
}

/** The one album tile used in every grid and shelf. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, showYear: Boolean = true) {
    val spacing = LocalSpacing.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .padding(spacing.one),
    ) {
        ArtImage(album.art, LocalSizes.current.gridMinCell, album.key, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = spacing.one),
        )
        val year = album.year
        Text(
            if (showYear && year != null) stringResource(R.string.separator_dot, album.albumArtist, year.toString()) else album.albumArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one artist row: round picture from their first album, name, counts. */
@Composable
fun ArtistRow(artist: Artist, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val size = LocalSizes.current.artistImage
    ListItem(
        headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = { ArtImage(artist.art, size, artist.key, Modifier.size(size), shape = CircleShape) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        modifier = modifier.combinedClickable(onClick = onClick, role = Role.Button),
    )
}

/** The one album row, for list view and artist pages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val size = LocalSizes.current.rowArt
    val year = album.year
    ListItem(
        headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (year != null) stringResource(R.string.separator_dot, album.albumArtist, year.toString()) else album.albumArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                QualityBadge(album.qualityClass, Modifier.padding(start = LocalSpacing.current.one))
            }
        },
        leadingContent = { ArtImage(album.art, size, album.key, Modifier.size(size)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button),
    )
}
