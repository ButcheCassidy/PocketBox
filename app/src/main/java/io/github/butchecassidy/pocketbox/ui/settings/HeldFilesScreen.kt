// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.data.scan.HeldFile
import io.github.butchecassidy.pocketbox.data.scan.ScanState
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.EmptyState
import io.github.butchecassidy.pocketbox.ui.components.InfoRow
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import io.github.butchecassidy.pocketbox.ui.components.SectionHeader
import io.github.butchecassidy.pocketbox.ui.navigation.LocalNavigator
import io.github.butchecassidy.pocketbox.ui.navigation.Routes
import io.github.butchecassidy.pocketbox.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/**
 * Songs the integrity check held back, each with what was found inside it. A song joins the library
 * only when the user taps "Add anyway" and confirms. Pocket Box never opens what it found.
 */
@Composable
fun HeldFilesScreen() {
    val container = LocalAppContainer.current
    val held by container.scanner.heldFiles.collectAsStateWithLifecycle(emptyList())
    var confirming by remember { mutableStateOf<HeldFile?>(null) }
    SettingsScaffold(stringResource(R.string.held_files)) {
        if (held.isEmpty()) {
            EmptyState(R.drawable.ic_shield, stringResource(R.string.held_files_empty), stringResource(R.string.held_files_empty_body))
            return@SettingsScaffold
        }
        Text(
            stringResource(R.string.held_files_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = LocalSpacing.current.screen, vertical = LocalSpacing.current.one),
        )
        held.forEach { file ->
            SectionHeader(file.fileName)
            ListItem(
                headlineContent = { Text(file.folderPath) },
                leadingContent = { Icon(painterResource(R.drawable.ic_folder), null) },
            )
            file.findings.forEach { InfoRow(it.issue.message, it.where) }
            OutlinedButton(onClick = { confirming = file }, modifier = Modifier.padding(horizontal = LocalSpacing.current.screen)) {
                Text(stringResource(R.string.add_anyway))
            }
        }
    }
    confirming?.let { file ->
        PocketDialog(
            title = stringResource(R.string.add_anyway_title),
            onDismissRequest = { confirming = null },
            confirmLabel = stringResource(R.string.add_anyway),
            onConfirm = {
                confirming = null
                container.appScope.launch { container.scanner.addHeldFileAnyway(file.documentUri) }
            },
            dismissLabel = stringResource(R.string.cancel),
        ) {
            Text(stringResource(R.string.add_anyway_body, file.fileName), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** After a scan that held songs back, says so once and offers to review them. */
@Composable
fun HeldFilesNotice() {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val scan by container.scanner.state.collectAsStateWithLifecycle()
    val count = (scan as? ScanState.Finished)?.heldBack ?: 0
    if (count == 0) return
    PocketDialog(
        title = pluralStringResource(R.plurals.held_notice_title, count, count),
        onDismissRequest = container.scanner::acknowledgeHeldBack,
        confirmLabel = stringResource(R.string.review),
        onConfirm = {
            container.scanner.acknowledgeHeldBack()
            navigator.go(Routes.HELD_FILES)
        },
        dismissLabel = stringResource(R.string.later),
    ) {
        Text(stringResource(R.string.held_notice_body))
    }
}
