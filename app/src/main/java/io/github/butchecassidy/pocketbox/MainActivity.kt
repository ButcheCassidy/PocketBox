// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** The single activity hosting all Compose screens. */
class MainActivity : ComponentActivity() {
    companion object {
        /** Set by the playback notification: open straight onto Now Playing. */
        const val EXTRA_OPEN_NOW_PLAYING = "io.github.butchecassidy.pocketbox.OPEN_NOW_PLAYING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text(getString(R.string.app_name)) }
    }
}
