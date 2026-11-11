// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.data.scan.ScanState
import io.github.butchecassidy.pocketbox.domain.model.UserSettings
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.ArtImage
import io.github.butchecassidy.pocketbox.ui.components.PocketIconButton
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.settings.AppearanceControls
import io.github.butchecassidy.pocketbox.ui.theme.LocalSizes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/** First run: welcome, pick folders, pick a look, then the first scan. Reopenable from Settings. */
@Composable
fun OnboardingScreen() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    AnimatedContent(step, Modifier.fillMaxSize().safeDrawingPadding(), label = "onboarding") { current ->
        when (current) {
            0 -> WelcomeStep { step = 1 }
            1 -> FoldersStep { step = 2 }
            2 -> LookStep { step = 3 }
            else -> DoneStep()
        }
    }
}

@Composable
private fun StepFrame(title: String?, body: String?, primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.fillMaxSize().padding(horizontal = spacing.screen)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = spacing.four).semantics { heading() },
                )
            }
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = spacing.two),
                )
            }
            Spacer(Modifier.size(spacing.three))
            content()
        }
        Row(Modifier.fillMaxWidth().padding(vertical = spacing.two), horizontalArrangement = Arrangement.End) {
            if (secondary != null && onSecondary != null) TextButton(onClick = onSecondary) { Text(secondary) }
            Button(onClick = onPrimary, modifier = Modifier.padding(start = spacing.one)) { Text(primary) }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val spacing = LocalSpacing.current
    StepFrame(title = null, body = null, primary = stringResource(R.string.get_started), onPrimary = onNext) {
        Spacer(Modifier.size(spacing.six))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(LocalSizes.current.gridMinCell))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.one),
        )
        Text(
            stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.two),
        )
        Text(
            stringResource(R.string.onboarding_credit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.four),
        )
    }
}

@Composable
private fun FoldersStep(onNext: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val roots by container.scanner.roots.collectAsStateWithLifecycle(emptyList())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { container.scanner.addFolder(uri) }
    }
    StepFrame(
        title = stringResource(R.string.onboarding_folders_title),
        body = stringResource(R.string.onboarding_folders_body),
        primary = stringResource(R.string.continue_label),
        onPrimary = onNext,
        secondary = if (roots.isEmpty()) stringResource(R.string.skip_for_now) else null,
        onSecondary = onNext,
    ) {
        roots.forEach { root ->
            ListItem(
                headlineContent = { Text(root.name) },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
                trailingContent = {
                    PocketIconButton(R.drawable.ic_close, stringResource(R.string.remove), { scope.launch { container.scanner.removeFolder(root.id) } })
                },
            )
        }
        OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.padding(top = LocalSpacing.current.two)) {
            Icon(painterResource(R.drawable.ic_add), null)
            Text(stringResource(R.string.add_folder), Modifier.padding(start = LocalSpacing.current.one))
        }
    }
}

@Composable
private fun LookStep(onNext: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(UserSettings())
    StepFrame(
        title = stringResource(R.string.onboarding_look_title),
        body = null,
        primary = stringResource(R.string.continue_label),
        onPrimary = onNext,
    ) {
        NowPlayingPreview()
        // Changes apply to the whole app at once, so the preview above is always live.
        AppearanceControls(settings) { transform -> scope.launch { container.settings.update(transform) } }
    }
}

/** A small, live mock of Now Playing in the chosen theme. */
@Composable
private fun NowPlayingPreview() {
    val spacing = LocalSpacing.current
    val sizes = LocalSizes.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.three), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ArtImage(null, sizes.gridMinCell, "preview", Modifier.padding(top = spacing.two).size(sizes.gridMinCell))
            Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = spacing.two))
            Text(stringResource(R.string.preview_artist), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearWavyProgressIndicator(progress = { PREVIEW_PROGRESS }, modifier = Modifier.padding(vertical = spacing.two).size(width = sizes.gridMinCell, height = spacing.two))
            FilledIconButton(onClick = {}, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())) {
                Icon(painterResource(R.drawable.ic_play_arrow_filled), stringResource(R.string.play))
            }
        }
    }
}

@Composable
private fun DoneStep() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scan by container.scanner.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.appScope.launch { container.scanner.scan() } }
    val finished = scan as? ScanState.Finished
    StepFrame(
        title = stringResource(R.string.onboarding_done_title),
        body = null,
        primary = stringResource(R.string.start_listening),
        onPrimary = {
            scope.launch {
                container.settings.update { it.copy(onboardingDone = true) }
                navigator.finishOnboarding()
            }
        },
    ) {
        if (finished != null) {
            Text(pluralStringResource(R.plurals.onboarding_done_ready, finished.trackCount, finished.trackCount), style = MaterialTheme.typography.titleMedium)
        } else {
            LoadingIndicator(Modifier.size(LocalSizes.current.gridMinCell / 2))
            val running = scan as? ScanState.Running
            if (running != null) {
                Text(pluralStringResource(R.plurals.scanning, running.filesFound, running.filesFound), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private const val PREVIEW_PROGRESS = 0.4f
