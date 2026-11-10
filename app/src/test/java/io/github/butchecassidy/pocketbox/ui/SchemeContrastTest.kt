// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.butchecassidy.pocketbox.ui.theme.color.ColorUtils
import io.github.butchecassidy.pocketbox.ui.theme.color.Hct
import io.github.butchecassidy.pocketbox.ui.theme.color.SchemeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemeContrastTest {
    private val seeds = listOf(
        0xFFE07A3F, 0xFF6750A4, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
        0xFF00FFFF, 0xFF808080, 0xFF000000, 0xFFFFFFFF, 0xFF12343B, 0xFFF4E3C1,
    ).map { it.toInt() }

    @Test
    fun `hct round trip stays within one step per channel`() {
        for (seed in seeds + listOf(0xFF336699.toInt(), 0xFFAA2244.toInt())) {
            val (h, c, t) = Hct.fromArgb(seed)
            val back = Hct.toArgb(h, c, t)
            for (shift in listOf(16, 8, 0)) {
                val diff = kotlin.math.abs(((seed shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("seed ${Integer.toHexString(seed)} → ${Integer.toHexString(back)}", diff <= 2)
            }
        }
    }

    @Test
    fun `palette tones land on the requested lightness`() {
        val (h, c, _) = Hct.fromArgb(0xFFE07A3F.toInt())
        for (tone in listOf(10, 30, 40, 50, 80, 90, 95)) {
            assertEquals(tone.toDouble(), ColorUtils.lstarFromArgb(Hct.toArgb(h, c, tone.toDouble())), 0.6)
        }
    }

    private fun ColorScheme.textPairs(): List<Pair<String, Pair<Color, Color>>> = listOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onSurface/surface" to (onSurface to surface),
        "onSurfaceVariant/surface" to (onSurfaceVariant to surface),
        "onSurface/surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
        "onSurfaceVariant/surfaceContainerHigh" to (onSurfaceVariant to surfaceContainerHigh),
        "primary/surface" to (primary to surface),
        "onError/error" to (onError to error),
        "inverseOnSurface/inverseSurface" to (inverseOnSurface to inverseSurface),
    )

    @Test
    fun `every text pair passes WCAG AA in light, dark and AMOLED`() {
        for (seed in seeds) {
            val schemes = mapOf(
                "light" to SchemeFactory.light(seed),
                "dark" to SchemeFactory.dark(seed, amoled = false),
                "amoled" to SchemeFactory.dark(seed, amoled = true),
            )
            for ((mode, scheme) in schemes) {
                for ((name, pair) in scheme.textPairs()) {
                    val ratio = ColorUtils.contrast(pair.first.toArgb(), pair.second.toArgb())
                    assertTrue("$mode ${Integer.toHexString(seed)} $name = $ratio", ratio >= 4.5)
                }
            }
        }
    }
}
