// Copyright (c) 2026 ButcheCassidy - The Nomadic Rebel. Licensed under PolyForm Strict 1.0.0. See LICENSE.
package io.github.butchecassidy.pocketbox

import android.app.Application
import io.github.butchecassidy.pocketbox.di.AppContainer

/** Application entry point; owns the object graph. */
class PocketBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.widgetRefresher.start(container.widgetState, container.settings.settings)
    }
}
