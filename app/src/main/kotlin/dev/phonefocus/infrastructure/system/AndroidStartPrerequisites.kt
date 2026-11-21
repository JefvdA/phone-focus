package dev.phonefocus.infrastructure.system

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.application.StartPrerequisites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks the [StartPrerequisite]s on the device.
 *
 * A prerequisite whose feature this build does not declare yet (no `HOME` activity before the
 * focus launcher #8, no accessibility service before #11) is [PrerequisiteStatus.NotAvailable]
 * and does not block starting focus. Once the manifest declares it, the check switches to the
 * real status with no change here.
 */
class AndroidStartPrerequisites(context: Context) : StartPrerequisites {
    private val appContext = context.applicationContext
    private val packageName = appContext.packageName

    override suspend fun status(prerequisite: StartPrerequisite): PrerequisiteStatus = withContext(Dispatchers.IO) {
        when (prerequisite) {
            StartPrerequisite.DefaultHome -> defaultHomeStatus()
            StartPrerequisite.AccessibilityService -> accessibilityStatus()
        }
    }

    private fun defaultHomeStatus(): PrerequisiteStatus {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
        val declared = appContext.packageManager
            .queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
            .isNotEmpty()
        if (!declared) return PrerequisiteStatus.NotAvailable
        val roles = appContext.getSystemService(RoleManager::class.java)
        return if (roles?.isRoleHeld(RoleManager.ROLE_HOME) == true) PrerequisiteStatus.Met else PrerequisiteStatus.Missing
    }

    private fun accessibilityStatus(): PrerequisiteStatus {
        val manager = appContext.getSystemService(AccessibilityManager::class.java) ?: return PrerequisiteStatus.NotAvailable
        val ours = manager.installedAccessibilityServiceList
            .map { it.resolveInfo.serviceInfo }
            .filter { it.packageName == packageName }
            .map { ComponentName(it.packageName, it.name) }
            .toSet()
        if (ours.isEmpty()) return PrerequisiteStatus.NotAvailable
        val enabled = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .toSet()
        return if (ours.any { it in enabled }) PrerequisiteStatus.Met else PrerequisiteStatus.Missing
    }

    companion object {
        /** The system settings screen where the user can set up [prerequisite]. */
        fun settingsIntent(prerequisite: StartPrerequisite): Intent = when (prerequisite) {
            StartPrerequisite.DefaultHome -> Intent(Settings.ACTION_HOME_SETTINGS)
            StartPrerequisite.AccessibilityService -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
