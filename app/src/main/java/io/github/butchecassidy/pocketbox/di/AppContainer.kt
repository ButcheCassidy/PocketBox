// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.di

import android.content.Context
import io.github.butchecassidy.pocketbox.data.art.ArtworkRepository
import io.github.butchecassidy.pocketbox.data.db.PocketBoxDatabase
import io.github.butchecassidy.pocketbox.data.lyrics.LyricsRepository
import io.github.butchecassidy.pocketbox.data.prefs.SettingsRepository
import io.github.butchecassidy.pocketbox.data.repo.EqPresetRepository
import io.github.butchecassidy.pocketbox.data.repo.HistoryRepository
import io.github.butchecassidy.pocketbox.data.repo.LibraryRepository
import io.github.butchecassidy.pocketbox.data.repo.PlaylistRepository
import io.github.butchecassidy.pocketbox.data.scan.AudioFiles
import io.github.butchecassidy.pocketbox.data.scan.FolderAccess
import io.github.butchecassidy.pocketbox.data.scan.LibraryScanner
import io.github.butchecassidy.pocketbox.playback.OutputMonitor
import io.github.butchecassidy.pocketbox.playback.PlayerConnection
import io.github.butchecassidy.pocketbox.playback.QueueController
import io.github.butchecassidy.pocketbox.playback.QueueStore
import io.github.butchecassidy.pocketbox.playback.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The app's object graph, built by hand once in [io.github.butchecassidy.pocketbox.PocketBoxApp]
 * (see docs/ARCHITECTURE.md for why there's no DI framework). Objects are created lazily, so
 * startup only pays for what the first screen needs.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val io = Dispatchers.IO
    val cpu = Dispatchers.Default

    /** Lives as long as the process; used for work that must outlive a screen (saving, scanning). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database by lazy { PocketBoxDatabase.create(appContext) }
    val settings by lazy { SettingsRepository(appContext, CoroutineScope(SupervisorJob() + io)) }
    val folderAccess by lazy { FolderAccess(appContext) }
    val audioFiles by lazy { AudioFiles(appContext) }
    val scanner by lazy { LibraryScanner(database, folderAccess, audioFiles, settings, io) }
    val artwork by lazy { ArtworkRepository(appContext, audioFiles, io, appScope) }
    val lyrics by lazy { LyricsRepository(audioFiles, io) }
    val library by lazy { LibraryRepository(database, cpu) }
    val history by lazy { HistoryRepository(database, settings, io) }
    val playlists by lazy { PlaylistRepository(appContext, database, audioFiles, io) }
    val eqPresets by lazy { EqPresetRepository(database, io) }
    val queueStore by lazy { QueueStore(database.playbackDao(), library, io) }
    val outputMonitor by lazy { OutputMonitor(appContext) }

    /** Pocket Box's own UI connection to the playback service. */
    val player by lazy { PlayerConnection(appContext, appScope) }
    val queue by lazy { QueueController(player, settings, appScope) }

    // State shared between the playback service and the UI (both run in this one process).
    val sleepTimerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val playbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
