package dev.phonefocus.infrastructure.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dev.phonefocus.application.SystemPackagesSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * The device-specific packages the app blocker must never block: the current and enabled
 * keyboards, every home app (the system launcher also draws recents) and the default and
 * system dialers. Re-resolved when the keyboard settings, the default dialer or packages change.
 */
class AndroidSystemPackages(context: Context, scope: CoroutineScope) : SystemPackagesSource {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    private val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = appContext.contentResolver
        listOf(Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.ENABLED_INPUT_METHODS).forEach {
            resolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        appContext.registerReceiver(receiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        appContext.registerReceiver(
            receiver,
            IntentFilter(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        trySend(Unit)
        awaitClose {
            resolver.unregisterContentObserver(observer)
            appContext.unregisterReceiver(receiver)
        }
    }.conflate()

    override val systemPackages: Flow<Set<String>> = changes
        .map { resolve() }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private fun resolve(): Set<String> = buildSet {
        // Keyboards: blocking the one in use would leave no way to type.
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.let { add(it.packageName) }
        appContext.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.forEach { add(it.packageName) }

        // Home apps: One UI Home keeps drawing recents while this app is the default home.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
            .forEach { add(it.activityInfo.packageName) }

        // Dialers: calls must always work.
        appContext.getSystemService(TelecomManager::class.java)?.let { telecom ->
            telecom.defaultDialerPackage?.let(::add)
            telecom.systemDialerPackage?.let(::add)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
