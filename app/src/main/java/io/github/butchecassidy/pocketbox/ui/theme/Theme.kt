// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.theme

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import io.github.butchecassidy.pocketbox.domain.model.ArtShape
import io.github.butchecassidy.pocketbox.domain.model.ColorSource
import io.github.butchecassidy.pocketbox.domain.model.ThemeMode
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.theme.color.SchemeFactory

/** The album-art outline the user picked, as a Compose shape. */
val LocalArtShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(ART_FALLBACK_CORNER_PERCENT) }

private const val ART_FALLBACK_CORNER_PERCENT = 12

/** Inputs to the theme. [artSeed] is the current album's colour, when the colour source is album art. */
data class ThemeConfig(
    val mode: ThemeMode,
    val amoled: Boolean,
    val source: ColorSource,
    val customSeed: Int,
    val artSeed: Int?,
    val artShape: ArtShape,
    val haptics: Boolean,
) {
    companion object {
        fun from(settings: UserSettings, artSeed: Int?) = ThemeConfig(
            mode = settings.themeMode,
            amoled = settings.amoledBlack,
            source = settings.colorSource,
            customSeed = settings.customSeedColor,
            artSeed = artSeed,
            artShape = settings.artShape,
            haptics = settings.hapticsEnabled,
        )
    }
}

/**
 * Pocket Box's single theme: Material 3 Expressive with spring motion, colours from Material You,
 * album art or a custom colour, light/dark/AMOLED. Every screen, dialog and sheet sits inside it.
 */
@Composable
fun PocketBoxTheme(config: ThemeConfig, content: @Composable () -> Unit) {
    val dark = when (config.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = rememberColorScheme(config, dark)
    val reduceMotion = rememberReduceMotion()
    val artShape = artShapeFor(config.artShape)
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
        LocalReduceMotion provides reduceMotion,
        LocalArtShape provides artShape,
        LocalHapticsEnabled provides config.haptics,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
            content = content,
        )
    }
}

@Composable
fun rememberColorScheme(config: ThemeConfig, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return remember(config.source, config.customSeed, config.artSeed, dark, config.amoled) {
        val seed = when (config.source) {
            ColorSource.DYNAMIC -> null
            ColorSource.ALBUM_ART -> config.artSeed ?: config.customSeed
            ColorSource.CUSTOM -> config.customSeed
        }
        when {
            seed == null && dark -> dynamicDarkColorScheme(context).let { if (config.amoled) SchemeFactory.amoled(it) else it }
            seed == null -> dynamicLightColorScheme(context)
            dark -> SchemeFactory.dark(seed, config.amoled)
            else -> SchemeFactory.light(seed)
        }
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Maps the stored choice to an official MaterialShapes outline (remembered by toShape()). */
@Composable
fun artShapeFor(shape: ArtShape): Shape = when (shape) {
    ArtShape.ROUNDED_SQUARE -> MaterialShapes.Square
    ArtShape.CIRCLE -> MaterialShapes.Circle
    ArtShape.COOKIE -> MaterialShapes.Cookie9Sided
    ArtShape.CLOVER -> MaterialShapes.Clover4Leaf
    ArtShape.SUNNY -> MaterialShapes.Sunny
    ArtShape.ARCH -> MaterialShapes.Arch
    ArtShape.SLANTED -> MaterialShapes.Slanted
    ArtShape.PILL -> MaterialShapes.Pill
}.toShape()

/** Picks a seed colour from album art, on the device, with AndroidX Palette. */
object ArtColors {
    fun seedFrom(bitmap: Bitmap): Int? {
        val palette = Palette.from(bitmap).maximumColorCount(MAX_COLORS).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        return swatch?.rgb
    }

    private const val MAX_COLORS = 16
}
