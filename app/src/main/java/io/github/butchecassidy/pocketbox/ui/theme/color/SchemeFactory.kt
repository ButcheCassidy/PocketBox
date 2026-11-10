// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.theme.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Every tone of one hue and chroma, from black (0) to white (100). */
class TonalPalette(private val hue: Double, private val chroma: Double) {
    private val cache = HashMap<Int, Color>()

    fun tone(tone: Int): Color = cache.getOrPut(tone) { Color(Hct.toArgb(hue, chroma, tone.toDouble())) }
}

/**
 * Builds full, matching light and dark Material 3 colour schemes from one seed colour, using HCT
 * tonal palettes (the same tone mapping Material uses). Light and dark use the same palettes at
 * mirrored tones, so colours properly switch instead of being inverted. Every text/background pair
 * is at least 50 tones apart, which guarantees WCAG AA contrast (tested in SchemeContrastTest).
 */
object SchemeFactory {
    private const val ERROR_HUE = 25.0
    private const val ERROR_CHROMA = 84.0

    /** Keeps the seed's own character (its chroma), like Material's content-based schemes. */
    private class Palettes(seed: Int) {
        private val hct = Hct.fromArgb(seed)
        private val hue = hct.first
        private val chroma = hct.second
        val primary = TonalPalette(hue, maxOf(chroma, MIN_PRIMARY_CHROMA))
        val secondary = TonalPalette(hue, maxOf(chroma - 32.0, chroma * 0.5).coerceAtLeast(MIN_ACCENT_CHROMA))
        val tertiary = TonalPalette(Hct.sanitizeDegrees(hue + 60.0), maxOf(chroma - 32.0, chroma * 0.5).coerceAtLeast(MIN_ACCENT_CHROMA))
        val neutral = TonalPalette(hue, (chroma / 8.0).coerceAtMost(MAX_NEUTRAL_CHROMA))
        val neutralVariant = TonalPalette(hue, (chroma / 8.0 + 4.0).coerceAtMost(MAX_NEUTRAL_CHROMA + 4.0))
        val error = TonalPalette(ERROR_HUE, ERROR_CHROMA)
    }

    fun light(seed: Int): ColorScheme {
        val p = Palettes(seed)
        return lightColorScheme(
            primary = p.primary.tone(40), onPrimary = p.primary.tone(100),
            primaryContainer = p.primary.tone(90), onPrimaryContainer = p.primary.tone(10),
            inversePrimary = p.primary.tone(80),
            secondary = p.secondary.tone(40), onSecondary = p.secondary.tone(100),
            secondaryContainer = p.secondary.tone(90), onSecondaryContainer = p.secondary.tone(10),
            tertiary = p.tertiary.tone(40), onTertiary = p.tertiary.tone(100),
            tertiaryContainer = p.tertiary.tone(90), onTertiaryContainer = p.tertiary.tone(10),
            background = p.neutral.tone(98), onBackground = p.neutral.tone(10),
            surface = p.neutral.tone(98), onSurface = p.neutral.tone(10),
            surfaceVariant = p.neutralVariant.tone(90), onSurfaceVariant = p.neutralVariant.tone(30),
            surfaceTint = p.primary.tone(40),
            inverseSurface = p.neutral.tone(20), inverseOnSurface = p.neutral.tone(95),
            error = p.error.tone(40), onError = p.error.tone(100),
            errorContainer = p.error.tone(90), onErrorContainer = p.error.tone(10),
            outline = p.neutralVariant.tone(50), outlineVariant = p.neutralVariant.tone(80),
            scrim = p.neutral.tone(0),
            surfaceBright = p.neutral.tone(98), surfaceDim = p.neutral.tone(87),
            surfaceContainerLowest = p.neutral.tone(100), surfaceContainerLow = p.neutral.tone(96),
            surfaceContainer = p.neutral.tone(94), surfaceContainerHigh = p.neutral.tone(92),
            surfaceContainerHighest = p.neutral.tone(90),
            primaryFixed = p.primary.tone(90), primaryFixedDim = p.primary.tone(80),
            onPrimaryFixed = p.primary.tone(10), onPrimaryFixedVariant = p.primary.tone(30),
            secondaryFixed = p.secondary.tone(90), secondaryFixedDim = p.secondary.tone(80),
            onSecondaryFixed = p.secondary.tone(10), onSecondaryFixedVariant = p.secondary.tone(30),
            tertiaryFixed = p.tertiary.tone(90), tertiaryFixedDim = p.tertiary.tone(80),
            onTertiaryFixed = p.tertiary.tone(10), onTertiaryFixedVariant = p.tertiary.tone(30),
        )
    }

    fun dark(seed: Int, amoled: Boolean): ColorScheme {
        val p = Palettes(seed)
        val scheme = darkColorScheme(
            primary = p.primary.tone(80), onPrimary = p.primary.tone(20),
            primaryContainer = p.primary.tone(30), onPrimaryContainer = p.primary.tone(90),
            inversePrimary = p.primary.tone(40),
            secondary = p.secondary.tone(80), onSecondary = p.secondary.tone(20),
            secondaryContainer = p.secondary.tone(30), onSecondaryContainer = p.secondary.tone(90),
            tertiary = p.tertiary.tone(80), onTertiary = p.tertiary.tone(20),
            tertiaryContainer = p.tertiary.tone(30), onTertiaryContainer = p.tertiary.tone(90),
            background = p.neutral.tone(6), onBackground = p.neutral.tone(90),
            surface = p.neutral.tone(6), onSurface = p.neutral.tone(90),
            surfaceVariant = p.neutralVariant.tone(30), onSurfaceVariant = p.neutralVariant.tone(80),
            surfaceTint = p.primary.tone(80),
            inverseSurface = p.neutral.tone(90), inverseOnSurface = p.neutral.tone(20),
            error = p.error.tone(80), onError = p.error.tone(20),
            errorContainer = p.error.tone(30), onErrorContainer = p.error.tone(90),
            outline = p.neutralVariant.tone(60), outlineVariant = p.neutralVariant.tone(30),
            scrim = p.neutral.tone(0),
            surfaceBright = p.neutral.tone(24), surfaceDim = p.neutral.tone(6),
            surfaceContainerLowest = p.neutral.tone(4), surfaceContainerLow = p.neutral.tone(10),
            surfaceContainer = p.neutral.tone(12), surfaceContainerHigh = p.neutral.tone(17),
            surfaceContainerHighest = p.neutral.tone(22),
            primaryFixed = p.primary.tone(90), primaryFixedDim = p.primary.tone(80),
            onPrimaryFixed = p.primary.tone(10), onPrimaryFixedVariant = p.primary.tone(30),
            secondaryFixed = p.secondary.tone(90), secondaryFixedDim = p.secondary.tone(80),
            onSecondaryFixed = p.secondary.tone(10), onSecondaryFixedVariant = p.secondary.tone(30),
            tertiaryFixed = p.tertiary.tone(90), tertiaryFixedDim = p.tertiary.tone(80),
            onTertiaryFixed = p.tertiary.tone(10), onTertiaryFixedVariant = p.tertiary.tone(30),
        )
        return if (amoled) amoled(scheme) else scheme
    }

    /**
     * Pure-black version of a dark scheme (also used for Material You dark). Surfaces go to black;
     * containers keep a faint step up so cards and sheets stay distinguishable.
     */
    fun amoled(dark: ColorScheme): ColorScheme = dark.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = lerp(dark.surfaceContainerLow, Color.Black, AMOLED_DARKEN),
        surfaceContainer = lerp(dark.surfaceContainer, Color.Black, AMOLED_DARKEN),
        surfaceContainerHigh = lerp(dark.surfaceContainerHigh, Color.Black, AMOLED_DARKEN),
        surfaceContainerHighest = lerp(dark.surfaceContainerHighest, Color.Black, AMOLED_DARKEN),
    )

    private const val MIN_PRIMARY_CHROMA = 24.0
    private const val MIN_ACCENT_CHROMA = 12.0
    private const val MAX_NEUTRAL_CHROMA = 8.0
    private const val AMOLED_DARKEN = 0.5f
}
