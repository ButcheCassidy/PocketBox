// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.butchecassidy.pocketbox.di.AppContainer
import io.github.butchecassidy.pocketbox.domain.model.ColorSource
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.theme.ArtColors
import io.github.butchecassidy.pocketbox.ui.theme.ThemeConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App-wide state for the single activity: the settings that shape the theme and where to start. */
class MainViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The album-art seed colour, only computed when the user chose album-art colours. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val artSeed = combine(
        container.settings.settings.map { it.colorSource == ColorSource.ALBUM_ART }.distinctUntilChanged(),
        container.player.state.map { it.art }.distinctUntilChanged(),
    ) { wanted, art -> if (wanted) art else null }
        .distinctUntilChanged()
        .mapLatest { art -> art?.let { container.artwork.load(it, SEED_SIZE_PX) }?.let(ArtColors::seedFrom) }
        .flowOn(container.cpu)

    val theme: StateFlow<ThemeConfig?> = combine(container.settings.settings, artSeed) { s, seed -> ThemeConfig.from(s, seed) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            container.settings.recordFirstLaunch(LocalDate.now().toEpochDay())
            container.scanner.refreshAccess()
        }
        // Pick up songs added or changed since last time; unchanged files aren't re-read.
        container.appScope.launch {
            if (container.settings.current().onboardingDone) container.scanner.scan()
        }
    }

    private companion object {
        const val SEED_SIZE_PX = 96
    }
}
