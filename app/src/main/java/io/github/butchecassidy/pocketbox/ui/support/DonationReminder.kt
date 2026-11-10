// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.SupportConfig
import io.github.butchecassidy.pocketbox.domain.model.DonationStatus
import io.github.butchecassidy.pocketbox.domain.support.DonationReminderPolicy
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Set once a reminder was considered in this launch, so it never appears twice per launch. */
private object ReminderSession {
    var checked = false
}

/**
 * The friendly donation reminder, using the shared dialog and the user's own theme. Nothing about it
 * is tracked or sent anywhere; its state lives only in DataStore on this phone.
 */
@Composable
fun DonationReminderHost() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(DonationStatus.NEVER_SHOWN) }
    val today = LocalDate.now().toEpochDay()

    LaunchedEffect(Unit) {
        if (ReminderSession.checked) return@LaunchedEffect
        ReminderSession.checked = true
        val state = container.settings.donationState.first()
        status = state.status
        visible = DonationReminderPolicy.shouldShow(state, today, SupportConfig.isConfigured())
    }
    if (!visible) return

    fun finish(next: DonationStatus) {
        visible = false
        scope.launch { container.settings.setDonationStatus(next, today) }
    }
    PocketDialog(
        title = stringResource(R.string.donate_title),
        onDismissRequest = { finish(DonationReminderPolicy.afterMaybeLater(status)) },
        confirmLabel = stringResource(R.string.donate),
        onConfirm = {
            openDonationPage(context)
            finish(DonationStatus.FINISHED)
        },
        neutralLabel = stringResource(R.string.maybe_later),
        onNeutral = { finish(DonationReminderPolicy.afterMaybeLater(status)) },
        dismissLabel = stringResource(R.string.no_thanks),
        onDismiss = { finish(DonationStatus.FINISHED) },
    ) {
        Text(stringResource(R.string.donate_body))
    }
}

/**
 * Opens the donation page in the user's browser. Pocket Box itself makes no connection (it has no
 * INTERNET permission); only a filled-in https://github.com/ link is ever opened.
 */
fun openDonationPage(context: Context) {
    if (!SupportConfig.isConfigured()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SupportConfig.DONATION_URL))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser installed: nothing to open, and nothing else to do.
    }
}
