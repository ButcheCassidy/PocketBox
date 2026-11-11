// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code runs at startup and while scrolling, so Android compiles it ahead of time on
 * install. The startup part also becomes the startup profile, which orders the app's code for a
 * faster cold start.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        startApp()
        scrollLibrary()
    }
}
