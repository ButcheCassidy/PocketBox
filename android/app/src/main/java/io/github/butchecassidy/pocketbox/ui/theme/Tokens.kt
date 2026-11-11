// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing on an 8 dp grid with a 4 dp half-step. Screens use only these, never raw numbers. */
@Immutable
data class Spacing(
    val half: Dp = 4.dp,
    val one: Dp = 8.dp,
    val oneAndHalf: Dp = 12.dp,
    val two: Dp = 16.dp,
    val three: Dp = 24.dp,
    val four: Dp = 32.dp,
    val six: Dp = 48.dp,
    /** The side margin every screen, header, list and sheet lines up on. */
    val screen: Dp = 16.dp,
)

/** Fixed sizes shared across screens, so the same thing is the same size everywhere. */
@Immutable
data class Sizes(
    val touchTarget: Dp = 48.dp,
    val rowArt: Dp = 48.dp,
    val rowHeight: Dp = 72.dp,
    val miniPlayerHeight: Dp = 72.dp,
    val gridMinCell: Dp = 156.dp,
    val shelfItem: Dp = 140.dp,
    val artistImage: Dp = 56.dp,
    val icon: Dp = 24.dp,
    val smallIcon: Dp = 18.dp,
    val letterRailWidth: Dp = 24.dp,
    val dragHandle: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalSizes = staticCompositionLocalOf { Sizes() }

/** True when the system "Remove animations" setting is on: morphs and waves become static or fades. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Whether the user turned haptics on in Settings. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }
