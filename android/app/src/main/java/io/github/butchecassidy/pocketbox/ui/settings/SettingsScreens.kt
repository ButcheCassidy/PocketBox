// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.BuildConfig
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.SupportConfig
import io.github.butchecassidy.pocketbox.data.scan.DocumentEntry
import io.github.butchecassidy.pocketbox.data.scan.ScanState
import io.github.butchecassidy.pocketbox.domain.audio.EqPresets
import io.github.butchecassidy.pocketbox.domain.model.LibraryRoot
import io.github.butchecassidy.pocketbox.domain.model.LibraryTab
import io.github.butchecassidy.pocketbox.domain.model.NowPlayingStyle
import io.github.butchecassidy.pocketbox.domain.model.ReplayGainMode
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.Format
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.LocalChromePadding
import io.github.butchecassidy.pocketbox.ui.components.LocalShowMessage
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.components.PocketTopBar
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.navigation.SettingsSection
import io.github.butchecassidy.pocketbox.ui.support.openDonationPage
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.launch
import java.util.Locale

/** Settings state and a single way to change it, shared by every settings screen. */
@Composable
private fun rememberSettings(): Pair<UserSettings, ((UserSettings) -> UserSettings) -> Unit> {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(UserSettings())
    return settings to { transform -> scope.launch { container.settings.update(transform) } }
}

@Composable
internal fun SettingsScaffold(title: String, showBack: Boolean = true, scrollable: Boolean = true, content: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = { PocketTopBar(title, onBack = if (showBack) ({ navigator.back() }) else null) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(bottom = LocalChromePadding.current.calculateBottomPadding()),
        ) { content() }
    }
}

@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    SettingsScaffold(stringResource(R.string.settings), showBack = false) {
        LinkRow(stringResource(R.string.settings_library), stringResource(R.string.settings_library_body), R.drawable.ic_folder) {
            navigator.go(Routes.settings(SettingsSection.LIBRARY))
        }
        LinkRow(stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_body), R.drawable.ic_palette) {
            navigator.go(Routes.settings(SettingsSection.APPEARANCE))
        }
        LinkRow(stringResource(R.string.settings_playback), stringResource(R.string.settings_playback_body), R.drawable.ic_play_arrow_filled) {
            navigator.go(Routes.settings(SettingsSection.PLAYBACK))
        }
        LinkRow(stringResource(R.string.settings_audio), stringResource(R.string.settings_audio_body), R.drawable.ic_tune) {
            navigator.go(Routes.settings(SettingsSection.AUDIO))
        }
        LinkRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_body), R.drawable.ic_lock) {
            navigator.go(Routes.settings(SettingsSection.PRIVACY))
        }
        LinkRow(stringResource(R.string.settings_about), stringResource(R.string.settings_about_body), R.drawable.ic_info) {
            navigator.go(Routes.settings(SettingsSection.ABOUT))
        }
        LinkRow(stringResource(R.string.run_setup_again), icon = R.drawable.ic_refresh) {
            navigator.go(Routes.ONBOARDING)
        }
    }
}

@Composable
fun SettingsSectionScreen(section: SettingsSection) {
    val title = stringResource(
        when (section) {
            SettingsSection.LIBRARY -> R.string.settings_library
            SettingsSection.APPEARANCE -> R.string.settings_appearance
            SettingsSection.PLAYBACK -> R.string.settings_playback
            SettingsSection.AUDIO -> R.string.settings_audio
            SettingsSection.PRIVACY -> R.string.settings_privacy
            SettingsSection.ABOUT -> R.string.settings_about
        },
    )
    SettingsScaffold(title) {
        when (section) {
            SettingsSection.LIBRARY -> LibrarySettings()
            SettingsSection.APPEARANCE -> AppearanceSettings()
            SettingsSection.PLAYBACK -> PlaybackSettings()
            SettingsSection.AUDIO -> AudioSettings()
            SettingsSection.PRIVACY -> PrivacySettings()
            SettingsSection.ABOUT -> AboutSettings()
        }
    }
}

@Composable
private fun LibrarySettings() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val (settings, update) = rememberSettings()
    val roots by container.scanner.roots.collectAsStateWithLifecycle(emptyList())
    val excluded by container.scanner.excludedFolders.collectAsStateWithLifecycle(emptyList())
    val scan by container.scanner.state.collectAsStateWithLifecycle()
    var excluding by remember { mutableStateOf<LibraryRoot?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) container.appScope.launch { if (container.scanner.addFolder(uri)) container.scanner.scan() }
    }
    SectionHeader(stringResource(R.string.music_folders))
    if (roots.isEmpty()) ListItem(headlineContent = { Text(stringResource(R.string.no_folders)) })
    roots.forEach { root ->
        ListItem(
            headlineContent = { Text(root.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = when {
                !root.isAccessible -> ({ Text(stringResource(R.string.access_lost_short), color = MaterialTheme.colorScheme.error) })
                root.isAudiobooks -> ({ Text(stringResource(R.string.audiobook_folder)) })
                else -> null
            },
            leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
            trailingContent = {
                Row {
                    IconToggleButton(
                        checked = root.isAudiobooks,
                        shapes = IconButtonDefaults.toggleableShapes(),
                        onCheckedChange = { checked -> container.appScope.launch { container.scanner.setAudiobookFolder(root.id, checked) } },
                    ) {
                        Icon(painterResource(R.drawable.ic_auto_stories), stringResource(R.string.mark_audiobook_folder))
                    }
                    PocketIconButton(R.drawable.ic_visibility_off, stringResource(R.string.exclude_subfolder), { excluding = root })
                    PocketIconButton(R.drawable.ic_delete, stringResource(R.string.remove), {
                        container.appScope.launch { container.scanner.removeFolder(root.id) }
                    })
                }
            },
        )
    }
    OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.padding(horizontal = LocalSpacing.current.screen)) {
        Text(stringResource(R.string.add_folder))
    }
    if (excluded.isNotEmpty()) {
        SectionHeader(stringResource(R.string.excluded_folders))
        excluded.forEach { folder ->
            ListItem(
                headlineContent = { Text(folder.displayPath) },
                trailingContent = {
                    PocketIconButton(R.drawable.ic_visibility, stringResource(R.string.include_again), {
                        container.appScope.launch { container.scanner.includeSubfolder(folder.id); container.scanner.scan() }
                    })
                },
            )
        }
    }
    LinkRow(stringResource(R.string.held_files), stringResource(R.string.held_files_body), R.drawable.ic_shield) {
        navigator.go(Routes.HELD_FILES)
    }
    val running = scan as? ScanState.Running
    LinkRow(
        stringResource(R.string.rescan),
        if (running != null) pluralStringResource(R.plurals.scanning, running.filesFound, running.filesFound) else stringResource(R.string.rescan_body),
        R.drawable.ic_refresh,
    ) { container.appScope.launch { container.scanner.scan() } }
    SliderRow(
        title = { value ->
            val seconds = value.toInt()
            stringResource(R.string.min_length) + ": " + if (seconds == 0) stringResource(R.string.off) else stringResource(R.string.seconds, seconds)
        },
        value = settings.minTrackSeconds.toFloat(),
        range = 0f..MAX_MIN_LENGTH,
        steps = MIN_LENGTH_STEPS,
        onChange = { value ->
            update { it.copy(minTrackSeconds = value.toInt()) }
            container.appScope.launch { container.scanner.applyMinimumDuration(value.toInt()) }
        },
    )
    excluding?.let { root ->
        SubfolderPicker(root, onPick = { entry, path ->
            excluding = null
            scope.launch {
                container.scanner.excludeSubfolder(root.id, entry.documentId, path)
                container.appScope.launch { container.scanner.scan() }
            }
        }, onDismiss = { excluding = null })
    }
}

/** Browse a music folder's subfolders, one level at a time, and pick one to leave out. */
@Composable
private fun SubfolderPicker(root: LibraryRoot, onPick: (DocumentEntry, String) -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val trail = remember { mutableStateListOf<DocumentEntry>() }
    val children by produceState(emptyList<DocumentEntry>(), trail.size) {
        value = container.scanner.subfolders(root.id, trail.lastOrNull()?.documentId)
    }
    val path = (listOf(root.name) + trail.map { it.name }).joinToString("/")
    PocketDialog(
        title = path,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.exclude_subfolder),
        confirmEnabled = trail.isNotEmpty(),
        onConfirm = { trail.lastOrNull()?.let { onPick(it, path) } },
        dismissLabel = stringResource(R.string.cancel),
        neutralLabel = if (trail.isNotEmpty()) stringResource(R.string.back) else null,
        onNeutral = { trail.removeAt(trail.lastIndex) },
    ) {
        LazyColumn(Modifier.heightIn(max = PICKER_MAX_HEIGHT)) {
            items(children, key = { it.documentId }) { child ->
                ListItem(
                    headlineContent = { Text(child.name) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
                    modifier = Modifier.clickable { trail.add(child) },
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettings() {
    val (settings, update) = rememberSettings()
    AppearanceControls(settings, update)
    ChoiceRow(
        stringResource(R.string.now_playing_style),
        listOf(
            NowPlayingStyle.BLURRED_ART to stringResource(R.string.style_blurred),
            NowPlayingStyle.GRADIENT to stringResource(R.string.style_gradient),
            NowPlayingStyle.PLAIN to stringResource(R.string.style_plain),
        ),
        settings.nowPlayingStyle,
    ) { style -> update { it.copy(nowPlayingStyle = style) } }
    ChoiceRow(
        stringResource(R.string.grid_columns),
        listOf(0 to stringResource(R.string.grid_auto), 2 to "2", 3 to "3", 4 to "4"),
        settings.gridColumns,
    ) { columns -> update { it.copy(gridColumns = columns) } }
    SwitchRow(stringResource(R.string.grid_view), settings.albumsAsGrid, { on -> update { it.copy(albumsAsGrid = on) } })
    SwitchRow(stringResource(R.string.haptics), settings.hapticsEnabled, { on -> update { it.copy(hapticsEnabled = on) } })
    SectionHeader(stringResource(R.string.show_shelves))
    SwitchRow(stringResource(R.string.recently_played), settings.showRecentlyPlayed, { on -> update { it.copy(showRecentlyPlayed = on) } })
    SwitchRow(stringResource(R.string.most_played), settings.showMostPlayed, { on -> update { it.copy(showMostPlayed = on) } })
    SwitchRow(stringResource(R.string.recently_added), settings.showRecentlyAdded, { on -> update { it.copy(showRecentlyAdded = on) } })
    SectionHeader(stringResource(R.string.tabs))
    settings.tabOrder.forEachIndexed { index, tab ->
        TabOrderRow(tab, index, settings, update)
    }
}

/** One library tab: show/hide, and move up or down. */
@Composable
private fun TabOrderRow(tab: LibraryTab, index: Int, settings: UserSettings, update: ((UserSettings) -> UserSettings) -> Unit) {
    val visible = tab !in settings.hiddenTabs
    ListItem(
        headlineContent = { Text(tab.label) },
        leadingContent = {
            Switch(checked = visible, onCheckedChange = { show ->
                update { s -> s.copy(hiddenTabs = if (show) s.hiddenTabs - tab else s.hiddenTabs + tab) }
            })
        },
        trailingContent = {
            Row {
                PocketIconButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.move_up), {
                    if (index > 0) update { s -> s.copy(tabOrder = s.tabOrder.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                })
                PocketIconButton(R.drawable.ic_keyboard_arrow_down, stringResource(R.string.move_down), {
                    if (index < settings.tabOrder.lastIndex) update { s -> s.copy(tabOrder = s.tabOrder.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                })
            }
        },
    )
}

@Composable
private fun PlaybackSettings() {
    val (settings, update) = rememberSettings()
    SliderRow(
        title = { value -> stringResource(R.string.crossfade) + ": " + if (value.toInt() == 0) stringResource(R.string.off) else stringResource(R.string.seconds, value.toInt()) },
        value = settings.crossfadeSeconds.toFloat(),
        range = 0f..UserSettings.MAX_CROSSFADE_SECONDS.toFloat(),
        steps = UserSettings.MAX_CROSSFADE_SECONDS - 1,
        onChange = { value -> update { it.copy(crossfadeSeconds = value.toInt()) } },
        enabled = !settings.bitPerfect,
    )
    SwitchRow(stringResource(R.string.confirm_queue_replace), settings.confirmQueueReplace, { on -> update { it.copy(confirmQueueReplace = on) } })
    SwitchRow(stringResource(R.string.pause_on_disconnect), settings.pauseOnDisconnect, { on -> update { it.copy(pauseOnDisconnect = on) } })
    SwitchRow(
        stringResource(R.string.resume_on_bluetooth),
        settings.resumeOnBluetooth,
        { on -> update { it.copy(resumeOnBluetooth = on) } },
        body = stringResource(R.string.resume_on_bluetooth_body),
    )
    ChoiceRow(
        stringResource(R.string.replaygain),
        listOf(
            ReplayGainMode.OFF to stringResource(R.string.replaygain_off),
            ReplayGainMode.TRACK to stringResource(R.string.replaygain_track),
            ReplayGainMode.ALBUM to stringResource(R.string.replaygain_album),
        ),
        settings.replayGainMode,
    ) { mode -> update { it.copy(replayGainMode = mode) } }
    if (settings.replayGainMode != ReplayGainMode.OFF) {
        SliderRow(
            title = { value -> stringResource(R.string.replaygain_preamp, Format.decibels(value)) },
            value = settings.replayGainPreampDb,
            range = -PREAMP_RANGE..PREAMP_RANGE,
            steps = PREAMP_STEPS,
            onChange = { value -> update { it.copy(replayGainPreampDb = value) } },
        )
    }
}

@Composable
private fun AudioSettings() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val (settings, update) = rememberSettings()
    val output by container.outputMonitor.info.collectAsStateWithLifecycle()
    LinkRow(stringResource(R.string.equalizer), stringResource(R.string.equalizer_body), R.drawable.ic_equalizer) { navigator.go(Routes.EQUALIZER) }
    SwitchRow(stringResource(R.string.bit_perfect), settings.bitPerfect, { on -> update { it.copy(bitPerfect = on) } }, body = stringResource(R.string.bit_perfect_body))
    ListItem(
        overlineContent = { Text(stringResource(R.string.current_output)) },
        headlineContent = { Text(output.deviceName) },
        supportingContent = { Text(output.kind.label) },
    )
}

@Composable
private fun PrivacySettings() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val showMessage = LocalShowMessage.current
    val cleared = stringResource(R.string.cleared)
    val (settings, update) = rememberSettings()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(R.string.privacy_note),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.screen, vertical = LocalSpacing.current.one),
    )
    SwitchRow(
        stringResource(R.string.pause_history),
        settings.historyPaused,
        { on -> update { it.copy(historyPaused = on) } },
        body = stringResource(R.string.pause_history_body),
    )
    LinkRow(stringResource(R.string.clear_history), stringResource(R.string.clear_history_body), R.drawable.ic_history) { confirmClear = true }
    LinkRow(stringResource(R.string.clear_searches), icon = R.drawable.ic_search) {
        scope.launch { container.history.clearRecentSearches(); showMessage(cleared) }
    }
    LinkRow(stringResource(R.string.clear_art_cache), icon = R.drawable.ic_album) {
        scope.launch { container.artwork.clear(); showMessage(cleared) }
    }
    if (confirmClear) {
        PocketDialog(
            title = stringResource(R.string.clear_history),
            onDismissRequest = { confirmClear = false },
            confirmLabel = stringResource(R.string.clear_queue),
            onConfirm = {
                confirmClear = false
                scope.launch { container.history.clearHistory(); showMessage(cleared) }
            },
            dismissLabel = stringResource(R.string.cancel),
        ) { Text(stringResource(R.string.clear_history_confirm)) }
    }
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val donation by container.settings.donationState.collectAsStateWithLifecycle(null)
    ListItem(overlineContent = { Text(stringResource(R.string.version)) }, headlineContent = { Text(BuildConfig.VERSION_NAME) })
    ListItem(overlineContent = { Text(stringResource(R.string.made_by)) }, headlineContent = { Text(stringResource(R.string.app_author)) })
    ListItem(
        overlineContent = { Text(stringResource(R.string.source_code)) },
        headlineContent = { SelectionContainer { Text(stringResource(R.string.source_code_url)) } },
        supportingContent = { Text(stringResource(R.string.source_code_note)) },
    )
    LinkRow(stringResource(R.string.licenses), stringResource(R.string.licenses_body), R.drawable.ic_description) { navigator.go(Routes.LICENSES) }
    if (SupportConfig.isConfigured()) {
        LinkRow(stringResource(R.string.support_pocket_box), stringResource(R.string.support_body), R.drawable.ic_volunteer_activism) {
            openDonationPage(context)
        }
        SwitchRow(
            stringResource(R.string.donation_reminders),
            donation?.remindersEnabled ?: true,
            { on -> scope.launch { container.settings.setRemindersEnabled(on) } },
            body = stringResource(R.string.donation_reminders_body),
        )
    }
}

/** 10-band equalizer, bass boost, built-in and saved presets. */
@Composable
fun EqualizerScreen() {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val (settings, update) = rememberSettings()
    val presets by container.eqPresets.presets.collectAsStateWithLifecycle(EqPresets.builtIn)
    var saving by rememberSaveable { mutableStateOf(false) }
    val spacing = LocalSpacing.current
    SettingsScaffold(stringResource(R.string.equalizer)) {
        if (settings.bitPerfect) {
            Text(stringResource(R.string.eq_off_bit_perfect), Modifier.padding(spacing.screen), color = MaterialTheme.colorScheme.error)
        }
        SwitchRow(stringResource(R.string.eq_enabled), settings.eqEnabled, { on -> update { it.copy(eqEnabled = on) } }, enabled = !settings.bitPerfect)
        SectionHeader(stringResource(R.string.presets))
        FlowRow(Modifier.padding(horizontal = spacing.screen), horizontalArrangement = Arrangement.spacedBy(spacing.one)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = settings.eqPresetName == preset.name,
                    onClick = { update { it.copy(eqEnabled = true, eqBandsDb = preset.bandsDb, eqPresetName = preset.name) } },
                    label = { Text(preset.name) },
                    trailingIcon = if (!preset.isBuiltIn) ({
                        Icon(
                            painterResource(R.drawable.ic_close),
                            stringResource(R.string.delete_preset),
                            Modifier.clickable { scope.launch { container.eqPresets.delete(preset.id) } },
                        )
                    }) else null,
                )
            }
            FilterChip(selected = false, onClick = { saving = true }, label = { Text(stringResource(R.string.save_preset)) })
        }
        EqPresets.bandCentersHz.forEachIndexed { band, hz ->
            SliderRow(
                title = { value ->
                    val label = if (hz >= 1000) stringResource(R.string.band_khz, (hz / 1000).toString()) else stringResource(R.string.band_hz, hz.toString())
                    "$label  " + String.format(Locale.ROOT, "%+.1f dB", value)
                },
                value = settings.eqBandsDb.getOrElse(band) { 0f },
                range = EqPresets.MIN_DB..EqPresets.MAX_DB,
                steps = EQ_STEPS,
                onChange = { value ->
                    update { s -> s.copy(eqEnabled = true, eqPresetName = "", eqBandsDb = List(EqPresets.bandCentersHz.size) { i -> if (i == band) value else s.eqBandsDb.getOrElse(i) { 0f } }) }
                },
                enabled = !settings.bitPerfect,
            )
        }
        SliderRow(
            title = { value -> stringResource(R.string.bass_boost) + ": ${(value * 100).toInt()} %" },
            value = settings.bassBoost,
            range = 0f..1f,
            steps = BASS_STEPS,
            onChange = { value -> update { it.copy(bassBoost = value) } },
            enabled = !settings.bitPerfect,
        )
    }
    if (saving) {
        var name by rememberSaveable { mutableStateOf("") }
        PocketDialog(
            title = stringResource(R.string.save_preset),
            onDismissRequest = { saving = false },
            confirmLabel = stringResource(R.string.save),
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                saving = false
                scope.launch { container.eqPresets.save(name, settings.eqBandsDb) }
                update { it.copy(eqPresetName = name.trim()) }
            },
            dismissLabel = stringResource(R.string.cancel),
        ) {
            OutlinedTextField(name, { name = it.take(MAX_PRESET_NAME) }, label = { Text(stringResource(R.string.preset_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Every bundled library with its licence; the Apache 2.0 text is shown in full. */
@Composable
fun LicensesScreen() {
    val resources = LocalResources.current
    val text = remember(resources) { resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() } }
    val spacing = LocalSpacing.current
    SettingsScaffold(stringResource(R.string.licenses)) {
        libraries.forEach { (name, owner) ->
            ListItem(headlineContent = { Text(name) }, supportingContent = { Text("$owner · ${stringResource(R.string.license_apache)}") })
        }
        SectionHeader(stringResource(R.string.license_apache))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.one),
        )
    }
}

private val libraries = listOf(
    "AndroidX Core, Activity, Lifecycle, Navigation" to "Google / AOSP",
    "Jetpack Compose and Material 3 (Expressive)" to "Google",
    "AndroidX Graphics Shapes" to "Google",
    "AndroidX Room, DataStore, DocumentFile, Palette" to "Google / AOSP",
    "Jetpack Glance" to "Google",
    "AndroidX ProfileInstaller" to "Google",
    "Media3 (ExoPlayer, Session)" to "Google",
    "Guava (used by Media3)" to "Google",
    "Kotlin standard library" to "JetBrains",
    "kotlinx.coroutines, kotlinx.collections.immutable" to "JetBrains",
    "Material Symbols (icons)" to "Google",
)

private const val MAX_MIN_LENGTH = 120f
private const val MIN_LENGTH_STEPS = 23
private const val PREAMP_RANGE = 12f
private const val PREAMP_STEPS = 47
private const val EQ_STEPS = 47
private const val BASS_STEPS = 9
private const val MAX_PRESET_NAME = 40
private val PICKER_MAX_HEIGHT = 320.dp
