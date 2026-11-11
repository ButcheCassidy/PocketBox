// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.butchecassidy.pocketbox.PocketBoxApp
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.nowplaying.ArtNeighbours
import io.github.butchecassidy.pocketbox.ui.nowplaying.ArtStage
import io.github.butchecassidy.pocketbox.ui.nowplaying.NowPlayingLayout
import io.github.butchecassidy.pocketbox.ui.nowplaying.NowPlayingTags
import io.github.butchecassidy.pocketbox.ui.nowplaying.PlayerControls
import io.github.butchecassidy.pocketbox.ui.nowplaying.SeekBar
import io.github.butchecassidy.pocketbox.ui.theme.PocketBoxTheme
import io.github.butchecassidy.pocketbox.ui.theme.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The album art, the progress bar and the play button share one vertical centre line (within 1 dp)
 * on every target phone's portrait screen, playing or paused, with art or without.
 */
@RunWith(AndroidJUnit4::class)
class NowPlayingCenteringTest {
    @get:Rule
    val rule = createComposeRule()

    /** Portrait content areas in dp (screen minus status and navigation bars). */
    private val screens = mapOf(
        "Pixel 6 Pro / 7 Pro" to DpSize(412.dp, 797.dp),
        "Pixel 6 / 6a / 7 / 7a / 8 / 8a" to DpSize(412.dp, 820.dp),
        "Pixel 9 / 9 Pro / 10 / 10 Pro" to DpSize(412.dp, 830.dp),
        "Pixel 9 Pro XL / 10 Pro XL" to DpSize(448.dp, 900.dp),
    )

    @Test
    fun artProgressAndPlayShareOneCentreLine() {
        val container = (ApplicationProvider.getApplicationContext<PocketBoxApp>()).container
        var screen by mutableStateOf(screens.values.first())
        var playing by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                PocketBoxTheme(ThemeConfig.from(UserSettings(), artSeed = null)) {
                    Box(Modifier.requiredSize(screen), contentAlignment = Alignment.TopStart) {
                        NowPlayingLayout(
                            art = { size: Dp ->
                                ArtStage(
                                    art = null,
                                    artKey = "test",
                                    size = size,
                                    isPlaying = playing,
                                    neighbours = ArtNeighbours(null, null, null, null),
                                    hasPrevious = true,
                                    hasNext = true,
                                    onPrevious = {},
                                    onNext = {},
                                    lyrics = {},
                                )
                            },
                            details = { width: Dp ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    SeekBar(positionMs = 30_000, durationMs = 180_000, isPlaying = playing, width = width, onSeek = {})
                                    PlayerControls(isPlaying = playing, hasPrevious = true, hasNext = true, onPrevious = {}, onToggle = {}, onNext = {})
                                }
                            },
                        )
                    }
                }
            }
        }
        for ((name, size) in screens) {
            for (isPlaying in listOf(true, false)) {
                screen = size
                playing = isPlaying
                rule.waitForIdle()
                val art = rule.onNodeWithTag(NowPlayingTags.ART).getBoundsInRoot()
                val progress = rule.onNodeWithTag(NowPlayingTags.PROGRESS).getBoundsInRoot()
                val play = rule.onNodeWithTag(NowPlayingTags.PLAY).getBoundsInRoot()
                val screenCentre = size.width / 2
                val label = "$name, ${if (isPlaying) "playing" else "paused"}"
                assertEquals("$label: art", screenCentre.value, ((art.left + art.right) / 2).value, TOLERANCE_DP)
                assertEquals("$label: progress bar", screenCentre.value, ((progress.left + progress.right) / 2).value, TOLERANCE_DP)
                assertEquals("$label: play button", screenCentre.value, ((play.left + play.right) / 2).value, TOLERANCE_DP)
                // The tagged art square keeps its full size; only its picture shrinks when paused.
                assertEquals("$label: progress bar matches the art width", (art.right - art.left).value, (progress.right - progress.left).value, TOLERANCE_DP)
            }
        }
    }

    private companion object {
        const val TOLERANCE_DP = 1f
    }
}
