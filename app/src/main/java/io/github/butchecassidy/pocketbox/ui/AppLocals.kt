// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.ui.theme.LocalHapticsEnabled

/** The app's object graph, available to every screen. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Space taken at the bottom by the mini player and navigation bar, so lists can scroll clear of it. */
val LocalChromePadding = staticCompositionLocalOf { PaddingValues() }

/** Creates a ViewModel with the app container, scoped to the current screen. */
@Composable
inline fun <reified VM : ViewModel> pocketViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** A haptic tick that respects the user's "Haptics" setting. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return { type -> if (enabled) haptics.performHapticFeedback(type) }
}
