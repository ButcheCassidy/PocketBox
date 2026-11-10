// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.butchecassidy.pocketbox.ui.detail.AlbumScreen
import io.github.butchecassidy.pocketbox.ui.detail.ArtistScreen
import io.github.butchecassidy.pocketbox.ui.detail.FacetScreen
import io.github.butchecassidy.pocketbox.ui.detail.FacetsScreen
import io.github.butchecassidy.pocketbox.ui.detail.FavoritesScreen
import io.github.butchecassidy.pocketbox.ui.detail.FolderScreen
import io.github.butchecassidy.pocketbox.ui.detail.PlaylistScreen
import io.github.butchecassidy.pocketbox.ui.detail.ShelfScreen
import io.github.butchecassidy.pocketbox.ui.library.LibraryScreen
import io.github.butchecassidy.pocketbox.ui.onboarding.OnboardingScreen
import io.github.butchecassidy.pocketbox.ui.search.SearchScreen
import io.github.butchecassidy.pocketbox.ui.settings.EqualizerScreen
import io.github.butchecassidy.pocketbox.ui.settings.LicensesScreen
import io.github.butchecassidy.pocketbox.ui.settings.SettingsScreen
import io.github.butchecassidy.pocketbox.ui.settings.SettingsSectionScreen

private fun NavBackStackEntry.arg(name: String): String = Uri.decode(arguments?.getString(name).orEmpty())

/** All screens. Transitions follow the theme's motion; predictive back is handled by Navigation. */
@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(Routes.ONBOARDING) { OnboardingScreen() }
        composable(Routes.LIBRARY) { LibraryScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.SETTINGS_SECTION) { entry ->
            SettingsSection.entries.firstOrNull { it.name == entry.arg("section") }?.let { SettingsSectionScreen(it) }
        }
        composable(Routes.ALBUM) { entry -> AlbumScreen(entry.arg("key")) }
        composable(Routes.ARTIST) { entry -> ArtistScreen(entry.arg("key")) }
        composable(Routes.PLAYLIST) { entry -> entry.arg("id").toLongOrNull()?.let { PlaylistScreen(it) } }
        composable(Routes.FAVORITES) { FavoritesScreen() }
        composable(Routes.FOLDER) { entry -> FolderScreen(entry.arg("uri")) }
        composable(Routes.SHELF) { entry ->
            ShelfKind.entries.firstOrNull { it.name == entry.arg("kind") }?.let { ShelfScreen(it) }
        }
        composable(Routes.FACETS) { entry ->
            FacetType.entries.firstOrNull { it.name == entry.arg("type") }?.let { FacetsScreen(it) }
        }
        composable(Routes.FACET) { entry ->
            FacetType.entries.firstOrNull { it.name == entry.arg("type") }?.let { FacetScreen(it, entry.arg("value")) }
        }
        composable(Routes.EQUALIZER) { EqualizerScreen() }
        composable(Routes.LICENSES) { LicensesScreen() }
    }
}
