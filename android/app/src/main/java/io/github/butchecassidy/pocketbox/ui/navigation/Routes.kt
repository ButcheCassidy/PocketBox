// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.navigation

import android.net.Uri

/** Every screen's route. Arguments are URI-encoded so any album title or folder path is safe. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val PLAYLIST = "playlist/{id}"
    const val FAVORITES = "favorites"
    const val FOLDER = "folder/{uri}"
    const val SHELF = "shelf/{kind}"
    const val FACETS = "facets/{type}"
    const val FACET = "facet/{type}/{value}"
    const val EQUALIZER = "equalizer"
    const val LICENSES = "licenses"
    const val HELD_FILES = "held"

    val topLevel = setOf(LIBRARY, SEARCH, SETTINGS)

    fun album(key: String) = "album/${Uri.encode(key)}"
    fun artist(key: String) = "artist/${Uri.encode(key)}"
    fun playlist(id: Long) = "playlist/$id"
    fun folder(uri: String) = "folder/${Uri.encode(uri)}"
    fun shelf(kind: ShelfKind) = "shelf/${kind.name}"
    fun facets(type: FacetType) = "facets/${type.name}"
    fun facet(type: FacetType, value: String) = "facet/${type.name}/${Uri.encode(value)}"
    fun settings(section: SettingsSection) = "settings/${section.name}"
}

enum class ShelfKind { RECENTLY_PLAYED, MOST_PLAYED, RECENTLY_ADDED }

enum class FacetType { GENRE, YEAR, COMPOSER }

enum class SettingsSection { LIBRARY, APPEARANCE, PLAYBACK, AUDIO, PRIVACY, ABOUT }
