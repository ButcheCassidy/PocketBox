// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.butchecassidy.pocketbox.R
import io.github.butchecassidy.pocketbox.playback.QueueEvent
import io.github.butchecassidy.pocketbox.ui.LocalAppContainer
import io.github.butchecassidy.pocketbox.ui.LocalChromePadding
import io.github.butchecassidy.pocketbox.ui.components.ActionsHost
import io.github.butchecassidy.pocketbox.ui.components.ActionsState
import io.github.butchecassidy.pocketbox.ui.components.LocalActions
import io.github.butchecassidy.pocketbox.ui.components.LocalShowMessage
import io.github.butchecassidy.pocketbox.ui.components.PocketDialog
import io.github.butchecassidy.pocketbox.ui.nowplaying.MiniPlayer
import io.github.butchecassidy.pocketbox.ui.nowplaying.NowPlayingScreen
import io.github.butchecassidy.pocketbox.ui.support.DonationReminderHost
import kotlinx.coroutines.launch

/** Moves between screens; one instance, available everywhere. */
@Stable
class Navigator(private val nav: NavHostController) {
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
    fun back() = nav.popBackStack()

    /** Switches top-level destination, keeping each one's own back stack and scroll position. */
    fun topLevel(route: String) = nav.navigate(route) {
        popUpTo(Routes.LIBRARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun finishOnboarding() = nav.navigate(Routes.LIBRARY) {
        popUpTo(Routes.ONBOARDING) { inclusive = true }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Whether Now Playing is expanded; any screen can open it. */
@Stable
class NowPlayingState {
    var expanded by mutableStateOf(false)
}

val LocalNowPlaying = staticCompositionLocalOf { NowPlayingState() }

private data class TopDestination(val route: String, val icon: Int, val label: Int)

private val topDestinations = listOf(
    TopDestination(Routes.LIBRARY, R.drawable.ic_library_music, R.string.library),
    TopDestination(Routes.SEARCH, R.drawable.ic_search, R.string.search),
    TopDestination(Routes.SETTINGS, R.drawable.ic_settings, R.string.settings),
)

/**
 * The app's frame: navigation bar (portrait) or rail (wide screens), the mini player above it,
 * snackbars, and the dialogs and sheets that any screen can open. Now Playing expands over it all.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PocketBoxRoot(startDestination: String, openNowPlayingRequest: Boolean, onNowPlayingRequestHandled: () -> Unit) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val actions = remember { ActionsState() }
    val nowPlaying = remember { NowPlayingState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP
    val showChrome = route != null && route != Routes.ONBOARDING
    val showNav = showChrome && route in Routes.topLevel
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    val queueReplaced = stringResource(R.string.queue_replaced)
    val undo = stringResource(R.string.undo)

    LaunchedEffect(openNowPlayingRequest, playerState.hasMedia) {
        if (openNowPlayingRequest && playerState.hasMedia) {
            nowPlaying.expanded = true
            onNowPlayingRequestHandled()
        }
    }
    LaunchedEffect(Unit) {
        container.queue.events.collect { event ->
            if (event == QueueEvent.Replaced) {
                val result = snackbar.showSnackbar(queueReplaced, actionLabel = undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) container.queue.undo()
            }
        }
    }
    LaunchedEffect(Unit) { container.playbackMessages.collect { showMessage(it) } }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalActions provides actions,
        LocalNowPlaying provides nowPlaying,
        LocalShowMessage provides showMessage,
    ) {
        SharedTransitionLayout {
            Row(Modifier.fillMaxSize()) {
                if (wide && showNav) {
                    WideNavigationRail {
                        topDestinations.forEach { destination ->
                            WideNavigationRailItem(
                                selected = route == destination.route,
                                onClick = { navigator.topLevel(destination.route) },
                                icon = { Icon(painterResource(destination.icon), null) },
                                label = { Text(stringResource(destination.label)) },
                                railExpanded = false,
                            )
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        Column {
                            AnimatedVisibility(
                                visible = showChrome && playerState.hasMedia && !nowPlaying.expanded,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                MiniPlayer(
                                    state = playerState,
                                    onExpand = { nowPlaying.expanded = true },
                                    sharedScope = this@SharedTransitionLayout,
                                    visibilityScope = this,
                                    modifier = if (showNav && !wide) Modifier else Modifier.navigationBarsPadding(),
                                )
                            }
                            if (showNav && !wide) {
                                ShortNavigationBar {
                                    topDestinations.forEach { destination ->
                                        ShortNavigationBarItem(
                                            selected = route == destination.route,
                                            onClick = { navigator.topLevel(destination.route) },
                                            icon = { Icon(painterResource(destination.icon), null) },
                                            label = { Text(stringResource(destination.label)) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    CompositionLocalProvider(LocalChromePadding provides padding) {
                        AppNavHost(navController, startDestination)
                    }
                }
            }
            AnimatedVisibility(
                visible = nowPlaying.expanded && playerState.hasMedia,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                BackHandler { nowPlaying.expanded = false }
                NowPlayingScreen(
                    onCollapse = { nowPlaying.expanded = false },
                    sharedScope = this@SharedTransitionLayout,
                    visibilityScope = this,
                )
            }
        }

        ActionsHost(
            state = actions,
            onOpenAlbum = { nowPlaying.expanded = false; navigator.go(Routes.album(it)) },
            onOpenArtist = { nowPlaying.expanded = false; navigator.go(Routes.artist(it)) },
        )
        ClearQueueDialog()
        if (route == Routes.LIBRARY && !nowPlaying.expanded) DonationReminderHost()
    }
}

/** "Clear queue?" — shown when tapping a song would throw away songs the user queued by hand. */
@Composable
private fun ClearQueueDialog() {
    val queue = LocalAppContainer.current.queue
    val pending by queue.pending.collectAsStateWithLifecycle()
    if (pending == null) return
    var dontAsk by rememberSaveable { mutableStateOf(false) }
    PocketDialog(
        title = stringResource(R.string.clear_queue_title),
        onDismissRequest = queue::cancel,
        confirmLabel = stringResource(R.string.clear_and_play),
        onConfirm = { queue.confirm(dontAsk) },
        dismissLabel = stringResource(R.string.cancel),
    ) {
        Text(stringResource(R.string.clear_queue_body), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
            Text(stringResource(R.string.dont_ask_again))
        }
    }
}

private const val WIDE_SCREEN_DP = 600
