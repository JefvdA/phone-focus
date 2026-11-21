package dev.phonefocus.infrastructure.system

import android.content.Context
import android.view.accessibility.AccessibilityManager
import dev.phonefocus.application.AppBlockerStatusSource
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.application.StartPrerequisites
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Whether this app's accessibility service is enabled, re-checked when the enabled
 * accessibility services change ([AccessibilityManager] listener) and on [refresh].
 */
class AndroidAppBlockerStatus(
    context: Context,
    private val prerequisites: StartPrerequisites,
) : AppBlockerStatusSource {
    private val appContext = context.applicationContext

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val serviceChanges: Flow<Unit> = callbackFlow {
        val manager = appContext.getSystemService(AccessibilityManager::class.java)
        val listener = AccessibilityManager.AccessibilityServicesStateChangeListener { trySend(Unit) }
        manager?.addAccessibilityServicesStateChangeListener(appContext.mainExecutor, listener)
        awaitClose { manager?.removeAccessibilityServicesStateChangeListener(listener) }
    }

    override val status: Flow<PrerequisiteStatus> = merge(serviceChanges, refreshes)
        .onStart { emit(Unit) }
        .conflate()
        .map { prerequisites.status(StartPrerequisite.AccessibilityService) }
        .distinctUntilChanged()

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }
}
