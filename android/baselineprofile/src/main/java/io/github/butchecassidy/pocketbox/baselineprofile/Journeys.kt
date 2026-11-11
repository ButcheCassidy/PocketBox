// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The release build's package (profiles are recorded on a release-like build, without the debug suffix). */
internal const val TARGET_PACKAGE = "io.github.butchecassidy.pocketbox"

private const val WAIT_MS = 5_000L

/** Cold start: open the app from the launcher and wait until its first screen is drawn. */
internal fun MacrobenchmarkScope.startApp() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), WAIT_MS)
}

/**
 * Scrolls the library the way people do: a few flings down and back up. On a phone where setup hasn't
 * been done yet there is no list, and the journey simply records the first screen.
 */
internal fun MacrobenchmarkScope.scrollLibrary() {
    val list = device.findObject(By.scrollable(true)) ?: return
    // Keep flings away from the system gesture areas at the screen edges.
    list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    repeat(FLINGS) { list.fling(Direction.DOWN) }
    repeat(FLINGS) { list.fling(Direction.UP) }
    device.waitForIdle()
}

private const val GESTURE_MARGIN_DIVISOR = 5
private const val FLINGS = 3
