// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.Lyrics
import io.github.butchecassidy.pocketbox.domain.model.NowPlayingStyle
import io.github.butchecassidy.pocketbox.domain.model.RepeatMode
import io.github.butchecassidy.pocketbox.domain.model.Track
import io.github.butchecassidy.pocketbox.playback.OutputInfo
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.QualityBadge
import io.github.butchecassidy.pocketbox.ui.rememberHaptic
import io.github.butchecassidy.pocketbox.ui.theme.LocalReduceMotion
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.util.Locale

/** Test tags for the centring test: art, progress bar and play button share one centre line. */
object NowPlayingTags {
    const val ART = "np_art"
    const val PROGRESS = "np_progress"
    const val PLAY = "np_play"
}

/** Neighbouring covers for swipe-to-skip; null when there's nothing that way (repeat off, queue end). */
data class ArtNeighbours(val previous: ArtRef?, val previousKey: String?, val next: ArtRef?, val nextKey: String?)

/**
 * Places the art and everything under it on one vertical centre line. Portrait: art at 84 % of the
 * width (capped so the controls fit), details below at the same width. Landscape: two columns, each
 * centred in itself. [details] gets the width the progress bar must match.
 */
@Composable
fun NowPlayingLayout(art: @Composable (Dp) -> Unit, details: @Composable (Dp) -> Unit) {
    val spacing = LocalSpacing.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = spacing.screen)) {
        val width = maxWidth
        if (width > maxHeight) {
            val artSize = min(maxHeight - spacing.three, width / 2 - spacing.three)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { art(artSize) }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { details(width / 2 - spacing.four) }
            }
        } else {
            val artSize = min(maxWidth * ART_WIDTH_FRACTION, maxHeight * ART_HEIGHT_FRACTION)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                art(artSize)
                Spacer(Modifier.height(spacing.three))
                details(artSize)
            }
        }
    }
}

/**
 * The album art square. Shrinks to 88 % when paused; swipe left/right to skip (the neighbour slides
 * in, a 30 % drag or a fling commits, otherwise it springs back; at the ends it resists like a
 * rubber band); tap to flip to lyrics in the same square. It never leaves its centre line. [modifier]
 * is applied to the cover itself, so the shared-element transition follows it.
 */
@Composable
fun ArtStage(
    art: ArtRef?,
    artKey: String,
    size: Dp,
    isPlaying: Boolean,
    neighbours: ArtNeighbours,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    lyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var flipped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if ((isPlaying && !dragging && offset.value == 0f) || reduceMotion) 1f else PAUSED_SCALE,
        animationSpec = motion.defaultSpatialSpec(),
        label = "art-scale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (reduceMotion) motion.fastEffectsSpec() else motion.slowSpatialSpec(),
        label = "art-flip",
    )
    LaunchedEffect(artKey) { offset.snapTo(0f) }

    Box(
        Modifier
            .size(size)
            .testTag(NowPlayingTags.ART)
            .pointerInput(flipped, hasPrevious, hasNext) {
                if (flipped) return@pointerInput
                val widthPx = this.size.width.toFloat()
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; tracker.resetTracking() },
                    onHorizontalDrag = { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val blocked = (amount < 0 && !hasNext) || (amount > 0 && !hasPrevious)
                        scope.launch { offset.snapTo(offset.value + if (blocked) amount * RUBBER_BAND else amount) }
                    },
                    onDragEnd = {
                        dragging = false
                        val velocity = tracker.calculateVelocity().x
                        val goNext = hasNext && (offset.value < -widthPx * COMMIT_FRACTION || velocity < -FLING_VELOCITY)
                        val goPrevious = hasPrevious && (offset.value > widthPx * COMMIT_FRACTION || velocity > FLING_VELOCITY)
                        scope.launch {
                            when {
                                goNext -> {
                                    haptic(HapticFeedbackType.Confirm)
                                    offset.animateTo(-widthPx, motion.fastSpatialSpec())
                                    onNext()
                                }
                                goPrevious -> {
                                    haptic(HapticFeedbackType.Confirm)
                                    offset.animateTo(widthPx, motion.fastSpatialSpec())
                                    onPrevious()
                                }
                                else -> offset.animateTo(0f, motion.defaultSpatialSpec())
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { offset.animateTo(0f, motion.defaultSpatialSpec()) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val sizePx = with(LocalDensity.current) { size.toPx() }
        // The neighbour cover slides in from the side the finger is moving away from.
        if (offset.value < 0f && neighbours.nextKey != null) {
            ArtImage(neighbours.next, size, neighbours.nextKey.orEmpty(), Modifier.size(size).offsetX(offset.value + sizePx).graphicsLayer { scaleX = PAUSED_SCALE; scaleY = PAUSED_SCALE })
        }
        if (offset.value > 0f && neighbours.previousKey != null) {
            ArtImage(neighbours.previous, size, neighbours.previousKey.orEmpty(), Modifier.size(size).offsetX(offset.value - sizePx).graphicsLayer { scaleX = PAUSED_SCALE; scaleY = PAUSED_SCALE })
        }
        Box(
            Modifier
                .size(size)
                .offsetX(offset.value)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationY = rotation
                    cameraDistance = CAMERA_DISTANCE * density
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { flipped = !flipped },
        ) {
            if (rotation <= 90f) {
                ArtImage(art, size, artKey, modifier.size(size), contentDescription = stringResource(R.string.album_art))
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { lyrics() }
            }
        }
    }
}

private fun Modifier.offsetX(x: Float) = offset { IntOffset(x.roundToInt(), 0) }

/** Wavy progress that waves while playing and flattens when paused; drag the handle to seek. */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, isPlaying: Boolean, width: Dp, onSeek: (Long) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val state = remember { SliderState(value = 0f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    if (!dragging) state.value = fraction
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying && !reduceMotion && !dragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "wave-amplitude",
    )
    Column(Modifier.width(width).testTag(NowPlayingTags.PROGRESS)) {
        Slider(
            state = state,
            onValueChange = { dragging = true; state.value = it },
            onValueChangeFinished = {
                dragging = false
                onSeek((state.value * durationMs).toLong())
            },
            enabled = durationMs > 0,
            track = { slider ->
                LinearWavyProgressIndicator(
                    progress = { slider.value },
                    amplitude = { amplitude },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            thumb = { SliderDefaults.Thumb(interactionSource = remember { MutableInteractionSource() }) },
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = if (dragging) (state.value * durationMs).toLong() else positionMs
            Text(Format.duration(shown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("-" + Format.duration((durationMs - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Previous · play/pause · next in an expressive button group. The play button is the large hero: a
 * circle while paused that morphs into a rounded square while playing, squishing on press.
 */
@Composable
fun PlayerControls(isPlaying: Boolean, hasPrevious: Boolean, hasNext: Boolean, onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val previousSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val side = IconButtonDefaults.mediumContainerSize()
    val hero = IconButtonDefaults.largeContainerSize()
    ButtonGroup(
        overflowIndicator = {},
        horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.one, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onPrevious,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = previousSource,
                    modifier = Modifier.size(side).animateWidth(previousSource),
                ) { Icon(painterResource(R.drawable.ic_skip_previous_filled), stringResource(R.string.previous)) }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledIconToggleButton(
                    checked = isPlaying,
                    onCheckedChange = { onToggle() },
                    shapes = IconToggleButtonShapes(
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(PRESSED_CORNER_PERCENT),
                        checkedShape = RoundedCornerShape(PLAYING_CORNER_PERCENT),
                    ),
                    interactionSource = playSource,
                    modifier = Modifier.size(hero).animateWidth(playSource).testTag(NowPlayingTags.PLAY),
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                        label = "play-pause",
                    ) { playing ->
                        Icon(
                            painterResource(if (playing) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled),
                            stringResource(if (playing) R.string.pause else R.string.play),
                            Modifier.size(IconButtonDefaults.largeIconSize),
                        )
                    }
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onNext,
                    enabled = hasNext || !hasPrevious,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = nextSource,
                    modifier = Modifier.size(side).animateWidth(nextSource),
                ) { Icon(painterResource(R.drawable.ic_skip_next_filled), stringResource(R.string.next)) }
            },
            menuContent = {},
        )
    }
}

/** Shuffle · favourite · lyrics · queue · repeat, evenly spaced on the centre line. */
@Composable
fun SecondaryControls(
    shuffle: Boolean,
    repeat: RepeatMode,
    favorite: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    width: Dp,
) {
    val toggleShapes = IconToggleButtonShapes(CircleShape, RoundedCornerShape(PRESSED_CORNER_PERCENT), RoundedCornerShape(TOGGLE_ON_CORNER_PERCENT))
    Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        FilledIconToggleButton(checked = shuffle, onCheckedChange = { onShuffle() }, shapes = toggleShapes) {
            Icon(painterResource(R.drawable.ic_shuffle), stringResource(R.string.shuffle))
        }
        IconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }, shapes = toggleShapes) {
            Icon(
                painterResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
            )
        }
        FilledTonalIconButton(onClick = onLyrics, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_lyrics), stringResource(R.string.lyrics))
        }
        FilledTonalIconButton(onClick = onQueue, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_queue_music), stringResource(R.string.queue))
        }
        FilledIconToggleButton(checked = repeat != RepeatMode.OFF, onCheckedChange = { onRepeat() }, shapes = toggleShapes) {
            Icon(
                painterResource(if (repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                stringResource(
                    when (repeat) {
                        RepeatMode.OFF -> R.string.repeat_off
                        RepeatMode.ALL -> R.string.repeat_all
                        RepeatMode.ONE -> R.string.repeat_one
                    },
                ),
            )
        }
    }
}

/** For audiobooks: back 30 s · bookmark · lyrics · queue · forward 30 s, on the same centre line. */
@Composable
fun BookControls(onBack: () -> Unit, onBookmark: () -> Unit, onLyrics: () -> Unit, onQueue: () -> Unit, onForward: () -> Unit, width: Dp) {
    Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_replay_30), stringResource(R.string.back_30))
        }
        IconButton(onClick = onBookmark, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_bookmark_add), stringResource(R.string.add_bookmark))
        }
        FilledTonalIconButton(onClick = onLyrics, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_lyrics), stringResource(R.string.lyrics))
        }
        FilledTonalIconButton(onClick = onQueue, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_queue_music), stringResource(R.string.queue))
        }
        IconButton(onClick = onForward, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.ic_forward_30), stringResource(R.string.forward_30))
        }
    }
}

/** Compact source + output chips; tap for the full quality sheet. */
@Composable
fun QualityChips(track: Track?, output: OutputInfo, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val quality = track?.quality ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.one, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        val source = listOfNotNull(
            quality.codec.label,
            quality.sampleRateHz?.let(Format::sampleRate),
            quality.bitDepth?.takeIf { quality.codec.isLossless == true }?.let { "$it-bit" },
        ).joinToString(" · ")
        AssistChip(onClick = onClick, label = { Text(source) }, colors = AssistChipDefaults.assistChipColors())
        if (output.isResampled(quality.sampleRateHz)) {
            AssistChip(onClick = onClick, label = { Text(stringResource(R.string.resampled)) })
        }
        QualityBadge(quality.qualityClass, Modifier.clickable(onClick = onClick))
    }
}

/**
 * Lyrics, synced or plain. Synced lines scroll smoothly with the current one highlighted, word by word
 * when the file times each word; tapping a line seeks there. [offsetMs] is the user's timing fix
 * (positive shows lyrics earlier); with [onNudge] the timing controls show below synced lyrics.
 * With no lyrics, a friendly note explains where they come from.
 */
@Composable
fun LyricsView(
    state: LyricsState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    large: Boolean,
    modifier: Modifier = Modifier,
    offsetMs: Long = 0,
    onNudge: ((Long) -> Unit)? = null,
    onResetTiming: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    val style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
    when (state) {
        LyricsState.Loading -> Box(modifier.fillMaxSize())
        LyricsState.None -> Column(
            modifier.fillMaxSize().padding(spacing.three),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.no_lyrics), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.no_lyrics_tip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.one),
            )
        }
        is LyricsState.Found -> when (val lyrics = state.lyrics) {
            is Lyrics.Unsynced -> Text(
                lyrics.text,
                style = style,
                textAlign = TextAlign.Center,
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.two),
            )
            is Lyrics.Synced -> Column(modifier.fillMaxSize()) {
                // Where the lyrics are "at": the song's position moved by the user's timing fix.
                val lyricsMs = positionMs + offsetMs
                val current = lyrics.indexAt(lyricsMs)
                val list = remember(lyrics) { LazyListState() }
                val reduceMotion = LocalReduceMotion.current
                LaunchedEffect(lyrics, current) {
                    // Before the first line (song start, or a seek back) the list returns to the top.
                    val target = (current - LYRIC_CONTEXT_LINES).coerceAtLeast(0)
                    if (reduceMotion) list.scrollToItem(target) else list.animateScrollToItem(target)
                }
                val colors = MaterialTheme.colorScheme
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, horizontalAlignment = Alignment.CenterHorizontally) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val active = index == current
                        val text = if (active && line.words.isNotEmpty()) {
                            val sung = line.wordsSungAt(lyricsMs)
                            buildAnnotatedString {
                                line.words.forEachIndexed { i, word ->
                                    withStyle(SpanStyle(color = if (i < sung) colors.primary else colors.onSurfaceVariant)) { append(word.text) }
                                }
                            }
                        } else {
                            AnnotatedString(line.text.ifEmpty { "♪" })
                        }
                        Text(
                            text,
                            style = style,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs - offsetMs) }
                                .padding(vertical = spacing.one, horizontal = spacing.two),
                        )
                    }
                }
                if (onNudge != null) LyricsTiming(offsetMs, onNudge, onResetTiming)
            }
        }
    }
}

/** Show lyrics earlier / later in small steps, with the current fix in between (tap it to reset). */
@Composable
private fun LyricsTiming(offsetMs: Long, onNudge: (Long) -> Unit, onReset: () -> Unit) {
    val resetLabel = stringResource(R.string.lyrics_timing_reset)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PocketIconButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.lyrics_earlier), { onNudge(LYRIC_NUDGE_MS) })
        TextButton(
            onClick = onReset,
            enabled = offsetMs != 0L,
            modifier = Modifier.semantics { onClick(label = resetLabel) { onReset(); true } },
        ) {
            Text(stringResource(R.string.lyrics_timing, String.format(Locale.ROOT, "%+.2f", offsetMs / 1000.0)))
        }
        PocketIconButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.lyrics_later), { onNudge(-LYRIC_NUDGE_MS) })
    }
}

/**
 * Behind everything: a soft, heavily blurred copy of the art (decoded tiny, so it's cheap), with a
 * scrim so text always stays readable; crossfades between songs. Without art, a theme gradient.
 */
@Composable
fun NowPlayingBackground(art: ArtRef?, artKey: String, style: NowPlayingStyle) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        when {
            style == NowPlayingStyle.PLAIN -> Unit
            style == NowPlayingStyle.BLURRED_ART && art != null -> Crossfade(art to artKey, label = "background") { (current, key) ->
                ArtImage(current, BLUR_SOURCE_SIZE, key, Modifier.fillMaxSize().blur(BLUR_RADIUS), shape = RoundedCornerShape(0))
            }
            else -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)),
                ),
            )
        }
        // Scrim: darker in dark mode, lighter in light mode, keeps WCAG AA contrast over any art.
        Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = SCRIM_ALPHA)))
    }
}

private const val PAUSED_SCALE = 0.88f
private const val COMMIT_FRACTION = 0.3f
private const val RUBBER_BAND = 0.25f
private const val FLING_VELOCITY = 1_500f
private const val CAMERA_DISTANCE = 12f
private const val PRESSED_CORNER_PERCENT = 20
private const val PLAYING_CORNER_PERCENT = 30
private const val TOGGLE_ON_CORNER_PERCENT = 30
private const val LYRIC_CONTEXT_LINES = 2
private const val LYRIC_NUDGE_MS = 250L
private const val SCRIM_ALPHA = 0.72f
private val BLUR_SOURCE_SIZE = 96.dp
private val BLUR_RADIUS = 48.dp

private const val ART_WIDTH_FRACTION = 0.84f
private const val ART_HEIGHT_FRACTION = 0.45f
