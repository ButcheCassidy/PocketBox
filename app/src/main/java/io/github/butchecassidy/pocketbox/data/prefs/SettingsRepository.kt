// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.butchecassidy.pocketbox.domain.model.AlbumSort
import io.github.butchecassidy.pocketbox.domain.model.ArtShape
import io.github.butchecassidy.pocketbox.domain.model.ColorSource
import io.github.butchecassidy.pocketbox.domain.model.DonationState
import io.github.butchecassidy.pocketbox.domain.model.DonationStatus
import io.github.butchecassidy.pocketbox.domain.model.LibraryTab
import io.github.butchecassidy.pocketbox.domain.model.NowPlayingStyle
import io.github.butchecassidy.pocketbox.domain.model.ReplayGainMode
import io.github.butchecassidy.pocketbox.domain.model.ScrollPosition
import io.github.butchecassidy.pocketbox.domain.model.SongSort
import io.github.butchecassidy.pocketbox.domain.model.ThemeMode
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * All user settings and small app state, stored with Jetpack DataStore (off the main thread, atomic).
 * A corrupt file is replaced with defaults instead of crashing the app.
 */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    // A storage read error (not corruption, which the handler above covers) falls back to defaults instead of crashing.
    private val data: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<UserSettings> = data.map(::toSettings).distinctUntilChanged()

    suspend fun current(): UserSettings = settings.first()

    /** Changes settings atomically: `update { it.copy(themeMode = ThemeMode.DARK) }`. */
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.edit { prefs -> write(prefs, transform(toSettings(prefs))) }
    }

    // ---- Last tab and scroll positions ----

    val lastTab: Flow<LibraryTab?> = data.map { enumOrNull<LibraryTab>(it[Keys.lastTab]) }.distinctUntilChanged()

    suspend fun setLastTab(tab: LibraryTab) {
        store.edit { it[Keys.lastTab] = tab.name }
    }

    suspend fun scrollPosition(tab: LibraryTab): ScrollPosition? {
        val raw = data.first()[scrollKey(tab)] ?: return null
        val index = raw.substringBefore(',').toIntOrNull() ?: return null
        val offset = raw.substringAfter(',').toIntOrNull() ?: 0
        return ScrollPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0))
    }

    suspend fun setScrollPosition(tab: LibraryTab, position: ScrollPosition) {
        store.edit { it[scrollKey(tab)] = "${position.index},${position.offset}" }
    }

    // ---- Donation reminder state (local only) ----

    val donationState: Flow<DonationState> = data.map { prefs ->
        DonationState(
            firstLaunchEpochDay = prefs[Keys.firstLaunchDay],
            playDays = prefs[Keys.playDays].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet(),
            status = enumOrNull<DonationStatus>(prefs[Keys.donationStatus]) ?: DonationStatus.NEVER_SHOWN,
            lastShownEpochDay = prefs[Keys.donationShownDay],
            remindersEnabled = prefs[Keys.remindersEnabled] ?: true,
        )
    }.distinctUntilChanged()

    /** Records the first launch date once; later calls do nothing. */
    suspend fun recordFirstLaunch(epochDay: Long) {
        store.edit { if (it[Keys.firstLaunchDay] == null) it[Keys.firstLaunchDay] = epochDay }
    }

    /** Adds a day with at least one counted play, keeping only the last [PLAY_DAY_WINDOW] days. */
    suspend fun recordPlayDay(epochDay: Long) {
        store.edit { prefs ->
            val days = prefs[Keys.playDays].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
            days += epochDay
            prefs[Keys.playDays] = days.filter { it > epochDay - PLAY_DAY_WINDOW }.sorted().joinToString(",")
        }
    }

    suspend fun setDonationStatus(status: DonationStatus, shownEpochDay: Long) {
        store.edit {
            it[Keys.donationStatus] = status.name
            it[Keys.donationShownDay] = shownEpochDay
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[Keys.remindersEnabled] = enabled }
    }

    // ---- Mapping ----

    private fun toSettings(p: Preferences): UserSettings {
        val d = UserSettings()
        return UserSettings(
            themeMode = enumOrNull<ThemeMode>(p[Keys.themeMode]) ?: d.themeMode,
            amoledBlack = p[Keys.amoledBlack] ?: d.amoledBlack,
            colorSource = enumOrNull<ColorSource>(p[Keys.colorSource]) ?: d.colorSource,
            customSeedColor = p[Keys.customSeedColor] ?: d.customSeedColor,
            artShape = enumOrNull<ArtShape>(p[Keys.artShape]) ?: d.artShape,
            nowPlayingStyle = enumOrNull<NowPlayingStyle>(p[Keys.nowPlayingStyle]) ?: d.nowPlayingStyle,
            gridColumns = (p[Keys.gridColumns] ?: d.gridColumns).coerceIn(0, MAX_GRID_COLUMNS),
            albumsAsGrid = p[Keys.albumsAsGrid] ?: d.albumsAsGrid,
            tabOrder = parseTabOrder(p[Keys.tabOrder]),
            hiddenTabs = p[Keys.hiddenTabs].orEmpty().split(',').mapNotNull { enumOrNull<LibraryTab>(it) }.toSet(),
            showRecentlyPlayed = p[Keys.showRecentlyPlayed] ?: d.showRecentlyPlayed,
            showMostPlayed = p[Keys.showMostPlayed] ?: d.showMostPlayed,
            showRecentlyAdded = p[Keys.showRecentlyAdded] ?: d.showRecentlyAdded,
            hapticsEnabled = p[Keys.haptics] ?: d.hapticsEnabled,
            albumSort = enumOrNull<AlbumSort>(p[Keys.albumSort]) ?: d.albumSort,
            songSort = enumOrNull<SongSort>(p[Keys.songSort]) ?: d.songSort,
            minTrackSeconds = (p[Keys.minTrackSeconds] ?: d.minTrackSeconds).coerceIn(0, MAX_MIN_TRACK_SECONDS),
            crossfadeSeconds = (p[Keys.crossfade] ?: d.crossfadeSeconds).coerceIn(0, UserSettings.MAX_CROSSFADE_SECONDS),
            confirmQueueReplace = p[Keys.confirmQueueReplace] ?: d.confirmQueueReplace,
            replayGainMode = enumOrNull<ReplayGainMode>(p[Keys.replayGainMode]) ?: d.replayGainMode,
            replayGainPreampDb = (p[Keys.replayGainPreamp] ?: d.replayGainPreampDb).coerceIn(-15f, 15f),
            pauseOnDisconnect = p[Keys.pauseOnDisconnect] ?: d.pauseOnDisconnect,
            resumeOnBluetooth = p[Keys.resumeOnBluetooth] ?: d.resumeOnBluetooth,
            playbackSpeed = (p[Keys.speed] ?: d.playbackSpeed).coerceIn(0.25f, 3f),
            playbackPitch = (p[Keys.pitch] ?: d.playbackPitch).coerceIn(0.5f, 2f),
            bitPerfect = p[Keys.bitPerfect] ?: d.bitPerfect,
            eqEnabled = p[Keys.eqEnabled] ?: d.eqEnabled,
            eqBandsDb = parseBands(p[Keys.eqBands]) ?: d.eqBandsDb,
            eqPresetName = p[Keys.eqPresetName] ?: d.eqPresetName,
            bassBoost = (p[Keys.bassBoost] ?: d.bassBoost).coerceIn(0f, 1f),
            historyPaused = p[Keys.historyPaused] ?: d.historyPaused,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }

    private fun write(p: MutablePreferences, s: UserSettings) {
        p[Keys.themeMode] = s.themeMode.name
        p[Keys.amoledBlack] = s.amoledBlack
        p[Keys.colorSource] = s.colorSource.name
        p[Keys.customSeedColor] = s.customSeedColor
        p[Keys.artShape] = s.artShape.name
        p[Keys.nowPlayingStyle] = s.nowPlayingStyle.name
        p[Keys.gridColumns] = s.gridColumns
        p[Keys.albumsAsGrid] = s.albumsAsGrid
        p[Keys.tabOrder] = s.tabOrder.joinToString(",") { it.name }
        p[Keys.hiddenTabs] = s.hiddenTabs.joinToString(",") { it.name }
        p[Keys.showRecentlyPlayed] = s.showRecentlyPlayed
        p[Keys.showMostPlayed] = s.showMostPlayed
        p[Keys.showRecentlyAdded] = s.showRecentlyAdded
        p[Keys.haptics] = s.hapticsEnabled
        p[Keys.albumSort] = s.albumSort.name
        p[Keys.songSort] = s.songSort.name
        p[Keys.minTrackSeconds] = s.minTrackSeconds
        p[Keys.crossfade] = s.crossfadeSeconds
        p[Keys.confirmQueueReplace] = s.confirmQueueReplace
        p[Keys.replayGainMode] = s.replayGainMode.name
        p[Keys.replayGainPreamp] = s.replayGainPreampDb
        p[Keys.pauseOnDisconnect] = s.pauseOnDisconnect
        p[Keys.resumeOnBluetooth] = s.resumeOnBluetooth
        p[Keys.speed] = s.playbackSpeed
        p[Keys.pitch] = s.playbackPitch
        p[Keys.bitPerfect] = s.bitPerfect
        p[Keys.eqEnabled] = s.eqEnabled
        p[Keys.eqBands] = s.eqBandsDb.joinToString(",")
        p[Keys.eqPresetName] = s.eqPresetName
        p[Keys.bassBoost] = s.bassBoost
        p[Keys.historyPaused] = s.historyPaused
        p[Keys.onboardingDone] = s.onboardingDone
    }

    /** Keeps the saved order, drops unknown names and appends tabs added in newer versions. */
    private fun parseTabOrder(raw: String?): List<LibraryTab> {
        val saved = raw.orEmpty().split(',').mapNotNull { enumOrNull<LibraryTab>(it) }.distinct()
        return saved + LibraryTab.entries.filterNot { it in saved }
    }

    private fun parseBands(raw: String?): List<Float>? {
        val bands = raw?.split(',')?.mapNotNull { it.toFloatOrNull()?.coerceIn(-15f, 15f) } ?: return null
        return bands.takeIf { it.size == UserSettings.EQ_BANDS }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun scrollKey(tab: LibraryTab) = stringPreferencesKey("scroll_${tab.name}")

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val amoledBlack = booleanPreferencesKey("amoled_black")
        val colorSource = stringPreferencesKey("color_source")
        val customSeedColor = intPreferencesKey("custom_seed_color")
        val artShape = stringPreferencesKey("art_shape")
        val nowPlayingStyle = stringPreferencesKey("now_playing_style")
        val gridColumns = intPreferencesKey("grid_columns")
        val albumsAsGrid = booleanPreferencesKey("albums_as_grid")
        val tabOrder = stringPreferencesKey("tab_order")
        val hiddenTabs = stringPreferencesKey("hidden_tabs")
        val showRecentlyPlayed = booleanPreferencesKey("show_recently_played")
        val showMostPlayed = booleanPreferencesKey("show_most_played")
        val showRecentlyAdded = booleanPreferencesKey("show_recently_added")
        val haptics = booleanPreferencesKey("haptics")
        val albumSort = stringPreferencesKey("album_sort")
        val songSort = stringPreferencesKey("song_sort")
        val minTrackSeconds = intPreferencesKey("min_track_seconds")
        val crossfade = intPreferencesKey("crossfade_seconds")
        val confirmQueueReplace = booleanPreferencesKey("confirm_queue_replace")
        val replayGainMode = stringPreferencesKey("replaygain_mode")
        val replayGainPreamp = floatPreferencesKey("replaygain_preamp")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val resumeOnBluetooth = booleanPreferencesKey("resume_on_bluetooth")
        val speed = floatPreferencesKey("playback_speed")
        val pitch = floatPreferencesKey("playback_pitch")
        val bitPerfect = booleanPreferencesKey("bit_perfect")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqBands = stringPreferencesKey("eq_bands")
        val eqPresetName = stringPreferencesKey("eq_preset_name")
        val bassBoost = floatPreferencesKey("bass_boost")
        val historyPaused = booleanPreferencesKey("history_paused")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val lastTab = stringPreferencesKey("last_tab")
        val firstLaunchDay = longPreferencesKey("first_launch_day")
        val playDays = stringPreferencesKey("play_days")
        val donationStatus = stringPreferencesKey("donation_status")
        val donationShownDay = longPreferencesKey("donation_shown_day")
        val remindersEnabled = booleanPreferencesKey("donation_reminders_enabled")
    }

    private companion object {
        const val FILE_NAME = "settings"
        const val PLAY_DAY_WINDOW = 30
        const val MAX_GRID_COLUMNS = 6
        const val MAX_MIN_TRACK_SECONDS = 600
    }
}
