// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the theme's seed colour comes from. */
enum class ColorSource { DYNAMIC, ALBUM_ART, CUSTOM }

/** Album-art outline, chosen from the official Material shapes in Settings → Appearance. */
enum class ArtShape { ROUNDED_SQUARE, CIRCLE, COOKIE, CLOVER, SUNNY, ARCH, SLANTED, PILL }

/** The background behind Now Playing. */
enum class NowPlayingStyle { BLURRED_ART, GRADIENT, PLAIN }

enum class LibraryTab(val label: String) {
    ALBUMS("Albums"),
    SINGLES("Singles & EPs"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
}

enum class AlbumSort(val label: String) {
    ARTIST_YEAR("Artist, then year"),
    TITLE("Title A–Z"),
    YEAR("Year"),
    RECENTLY_ADDED("Recently added"),
}

enum class SongSort(val label: String) {
    TITLE("Title A–Z"),
    ARTIST("Artist"),
    RECENTLY_ADDED("Recently added"),
}

enum class ReplayGainMode { OFF, TRACK, ALBUM }

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the user can change, with safe defaults for a first launch. */
data class UserSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val colorSource: ColorSource = ColorSource.DYNAMIC,
    val customSeedColor: Int = DEFAULT_SEED_COLOR,
    val artShape: ArtShape = ArtShape.ROUNDED_SQUARE,
    val nowPlayingStyle: NowPlayingStyle = NowPlayingStyle.BLURRED_ART,
    val gridColumns: Int = 0,
    val albumsAsGrid: Boolean = true,
    val tabOrder: List<LibraryTab> = LibraryTab.entries,
    val hiddenTabs: Set<LibraryTab> = emptySet(),
    val showRecentlyPlayed: Boolean = true,
    val showMostPlayed: Boolean = true,
    val showRecentlyAdded: Boolean = true,
    val hapticsEnabled: Boolean = true,
    // Library
    val albumSort: AlbumSort = AlbumSort.ARTIST_YEAR,
    val songSort: SongSort = SongSort.TITLE,
    val minTrackSeconds: Int = 0,
    // Playback
    val crossfadeSeconds: Int = 0,
    val confirmQueueReplace: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val replayGainPreampDb: Float = 0f,
    val pauseOnDisconnect: Boolean = true,
    val resumeOnBluetooth: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val bitPerfect: Boolean = false,
    // Audio effects
    val eqEnabled: Boolean = false,
    val eqBandsDb: List<Float> = List(EQ_BANDS) { 0f },
    val eqPresetName: String = "Flat",
    val bassBoost: Float = 0f,
    // Privacy
    val historyPaused: Boolean = false,
    // First run
    val onboardingDone: Boolean = false,
) {
    /** Visible tabs in the user's order; never empty, so the library always has something to show. */
    val visibleTabs: List<LibraryTab> get() = tabOrder.filterNot { it in hiddenTabs }.ifEmpty { listOf(LibraryTab.SONGS) }

    companion object {
        const val EQ_BANDS = 10
        const val DEFAULT_SEED_COLOR = 0xFFE07A3F.toInt()
        const val MAX_CROSSFADE_SECONDS = 12
    }
}

/** A list's saved scroll position, so the app reopens exactly where the user left it. */
data class ScrollPosition(val index: Int, val offset: Int)

/** Local-only state for the once-in-a-while donation reminder (see DonationReminderPolicy). */
data class DonationState(
    val firstLaunchEpochDay: Long?,
    val playDays: Set<Long>,
    val status: DonationStatus,
    val lastShownEpochDay: Long?,
    val remindersEnabled: Boolean,
)

enum class DonationStatus { NEVER_SHOWN, MAYBE_LATER, FINISHED }
