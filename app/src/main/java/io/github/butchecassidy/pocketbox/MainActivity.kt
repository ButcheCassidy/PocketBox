// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.navigation.PocketBoxRoot
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.theme.PocketBoxTheme

/** The single activity hosting all Compose screens, edge to edge. */
class MainActivity : ComponentActivity() {
    companion object {
        /** Set by the playback notification: open straight onto Now Playing. */
        const val EXTRA_OPEN_NOW_PLAYING = "io.github.butchecassidy.pocketbox.OPEN_NOW_PLAYING"
    }

    private val container get() = (application as PocketBoxApp).container
    private val viewModel: MainViewModel by viewModels { viewModelFactory { initializer { MainViewModel(container) } } }
    private var openNowPlaying by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the splash until settings are read, so the first frame already has the right theme and screen.
        splash.setKeepOnScreenCondition { viewModel.theme.value == null || viewModel.settings.value == null }
        openNowPlaying = intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true

        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val config = theme ?: return@setContent
            val current = settings ?: return@setContent
            val start = if (current.onboardingDone) Routes.LIBRARY else Routes.ONBOARDING
            CompositionLocalProvider(LocalAppContainer provides container) {
                PocketBoxTheme(config) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PocketBoxRoot(
                            startDestination = start,
                            openNowPlayingRequest = openNowPlaying,
                            onNowPlayingRequestHandled = { openNowPlaying = false },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.player.acquire()
    }

    override fun onStop() {
        container.player.release()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) openNowPlaying = true
    }
}
