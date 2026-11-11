// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.butchecassidy.pocketbox.MainActivity
import io.github.butchecassidy.pocketbox.PocketBoxApp
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.domain.model.ArtRef
import io.github.butchecassidy.pocketbox.domain.model.ThemeMode
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.theme.ArtColors
import io.github.butchecassidy.pocketbox.ui.theme.Sizes
import io.github.butchecassidy.pocketbox.ui.theme.Spacing
import io.github.butchecassidy.pocketbox.ui.theme.colorSchemeFor
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget: album art, title, artist and previous / play-pause / next. It follows the
 * look chosen in the app (light/dark/system, AMOLED, Material You, album-art or custom colour).
 * Play/pause is a circle while paused and a rounded square while playing, like the app's hero button.
 */
class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container()
        val firstSettings = container.settings.settings.first()
        val firstSnapshot = container.widgetState.value ?: savedSnapshot(container)
        val firstArt = firstSnapshot?.art?.let { loadArt(container, it) }
        provideContent {
            val settings by container.settings.settings.collectAsState(firstSettings)
            val live by container.widgetState.collectAsState()
            val snapshot = live ?: firstSnapshot
            val art by produceState(firstArt?.takeIf { it.ref == snapshot?.art }, snapshot?.art) {
                value = snapshot?.art?.let { loadArt(container, it) }
            }
            GlanceTheme(colors = colorsFor(context, settings, art?.seed)) {
                WidgetContent(snapshot, art?.bitmap)
            }
        }
    }

    /** After a restart nothing is loaded yet, so show the song that will resume. */
    private suspend fun savedSnapshot(container: AppContainer): WidgetSnapshot? =
        container.queueStore.savedCurrentTrack()?.let { WidgetSnapshot(it.title, it.artist, it.art, isPlaying = false) }

    private suspend fun loadArt(container: AppContainer, ref: ArtRef): WidgetArt? {
        val bitmap = container.artwork.load(ref, ART_SIZE_PX) ?: return null
        return WidgetArt(ref, bitmap, ArtColors.seedFrom(bitmap))
    }

    private fun colorsFor(context: Context, settings: UserSettings, artSeed: Int?): ColorProviders {
        fun scheme(dark: Boolean) =
            colorSchemeFor(context, settings.colorSource, settings.customSeedColor, artSeed, dark, settings.amoledBlack)
        return when (settings.themeMode) {
            ThemeMode.SYSTEM -> ColorProviders(light = scheme(dark = false), dark = scheme(dark = true))
            ThemeMode.LIGHT -> ColorProviders(scheme(dark = false))
            ThemeMode.DARK -> ColorProviders(scheme(dark = true))
        }
    }

    private class WidgetArt(val ref: ArtRef, val bitmap: Bitmap, val seed: Int?)

    private companion object {
        val COMPACT = DpSize(120.dp, 56.dp)
        val WIDE = DpSize(250.dp, 56.dp)
        val TALL = DpSize(250.dp, 120.dp)
        const val ART_SIZE_PX = 192
    }
}

private val spacing = Spacing()
private val sizes = Sizes()
private val titleSize = 14.sp
private val artistSize = 12.sp
private val compactArt = 40.dp
private val tallArt = 72.dp
private val WIDE_MIN_WIDTH = 250.dp
private val TALL_MIN_HEIGHT = 120.dp

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?, art: Bitmap?) {
    val context = LocalContext.current
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true),
    )
    val size = LocalSize.current
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
        .background(GlanceTheme.colors.widgetBackground)
        .padding(spacing.oneAndHalf)
        .clickable(openApp)
    when {
        size.width < WIDE_MIN_WIDTH -> Row(root, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Art(art, compactArt)
            Spacer(GlanceModifier.width(spacing.oneAndHalf))
            PlayPause(snapshot)
        }
        size.height < TALL_MIN_HEIGHT -> Row(root, verticalAlignment = Alignment.CenterVertically) {
            Art(art, compactArt)
            Spacer(GlanceModifier.width(spacing.oneAndHalf))
            Labels(snapshot, GlanceModifier.defaultWeight())
            Controls(snapshot)
        }
        else -> Column(root, verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Art(art, tallArt)
                Spacer(GlanceModifier.width(spacing.two))
                Labels(snapshot, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.size(spacing.oneAndHalf))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Controls(snapshot)
            }
        }
    }
}

@Composable
private fun Art(bitmap: Bitmap?, side: Dp) {
    val modifier = GlanceModifier.size(side).cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    if (bitmap != null) {
        Image(ImageProvider(bitmap), contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(GlanceTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(R.drawable.ic_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                modifier = GlanceModifier.size(sizes.icon),
            )
        }
    }
}

@Composable
private fun Labels(snapshot: WidgetSnapshot?, modifier: GlanceModifier) {
    Column(modifier) {
        Text(
            text = snapshot?.title?.ifBlank { null } ?: stringRes(R.string.widget_nothing_playing),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = titleSize, fontWeight = FontWeight.Medium),
        )
        Text(
            text = snapshot?.artist?.ifBlank { null } ?: stringRes(R.string.widget_tap_to_open),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = artistSize),
        )
    }
}

@Composable
private fun Controls(snapshot: WidgetSnapshot?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_skip_previous_filled),
            contentDescription = stringRes(R.string.previous),
            onClick = actionRunCallback<PreviousAction>(),
            backgroundColor = null,
            contentColor = GlanceTheme.colors.onSurface,
        )
        Spacer(GlanceModifier.width(spacing.half))
        PlayPause(snapshot)
        Spacer(GlanceModifier.width(spacing.half))
        CircleIconButton(
            imageProvider = ImageProvider(R.drawable.ic_skip_next_filled),
            contentDescription = stringRes(R.string.next),
            onClick = actionRunCallback<NextAction>(),
            backgroundColor = null,
            contentColor = GlanceTheme.colors.onSurface,
        )
    }
}

/** Circle while paused, rounded square while playing — the same shape change as the app's play button. */
@Composable
private fun PlayPause(snapshot: WidgetSnapshot?) {
    val playing = snapshot?.isPlaying == true
    val icon = ImageProvider(if (playing) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled)
    val label = stringRes(if (playing) R.string.pause else R.string.play)
    val action = actionRunCallback<TogglePlayAction>()
    if (playing) {
        SquareIconButton(icon, label, action, backgroundColor = GlanceTheme.colors.primary, contentColor = GlanceTheme.colors.onPrimary)
    } else {
        CircleIconButton(icon, label, action, backgroundColor = GlanceTheme.colors.primary, contentColor = GlanceTheme.colors.onPrimary)
    }
}

@Composable
private fun stringRes(@StringRes id: Int): String = LocalContext.current.getString(id)

/** Widget buttons talk to the playback service through the app's own media controller. */
class TogglePlayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container().player.togglePlay().join()
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container().player.next().join()
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container().player.previous().join()
    }
}

private fun Context.container() = (applicationContext as PocketBoxApp).container

/** Registers the widget with Android. Exported: the system's widget host must reach it. */
class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
