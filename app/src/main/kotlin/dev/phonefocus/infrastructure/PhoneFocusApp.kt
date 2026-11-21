package dev.phonefocus.infrastructure

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import dev.phonefocus.infrastructure.system.FocusEventReceiver
import kotlinx.coroutines.launch

/** Composition root: builds the [AppContainer], ticks on app start, listens for screen-on. */
class PhoneFocusApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Screen-on, user-present and shutdown are only delivered to runtime receivers. They
        // work while the process is alive; boot and the unlock alarm cover the other cases.
        val filter = IntentFilter().apply { FocusEventReceiver.RUNTIME_ACTIONS.forEach(::addAction) }
        registerReceiver(FocusEventReceiver(), filter, Context.RECEIVER_NOT_EXPORTED)

        container.scope.launch {
            runCatching { container.commands.tick() }
                .onFailure { Log.e("PhoneFocusApp", "tick on app start failed", it) }
        }
    }
}
