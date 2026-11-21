package dev.phonefocus.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeverBlockedPackagesTest {
    @Test
    fun `system UI and the system server are never blocked`() {
        assertTrue(NeverBlockedPackages.SYSTEM_UI in NeverBlockedPackages)
        assertTrue(NeverBlockedPackages.ANDROID in NeverBlockedPackages)
    }

    @Test
    fun `telephony, telecom, in-call screens and emergency apps are never blocked`() {
        listOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.android.emergency",
            "com.samsung.android.emergency",
            "com.google.android.cellbroadcastreceiver",
        ).forEach { assertTrue(it, it in NeverBlockedPackages) }
    }

    @Test
    fun `the share sheet, permission and install dialogs are never blocked`() {
        listOf(
            "com.android.intentresolver",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
        ).forEach { assertTrue(it, it in NeverBlockedPackages) }
    }

    @Test
    fun `keyboards, lock screen overlays and the system launcher drawing recents are never blocked`() {
        listOf(
            "com.samsung.android.honeyboard",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.app.aodservice",
            "com.sec.android.app.launcher",
        ).forEach { assertTrue(it, it in NeverBlockedPackages) }
    }

    @Test
    fun `ordinary apps are not on the list`() {
        listOf(ShortVideoApps.INSTAGRAM, ShortVideoApps.YOUTUBE, "com.whatsapp", "com.android.chrome", "com.android.settings")
            .forEach { assertFalse(it, it in NeverBlockedPackages) }
    }

    @Test
    fun `packages is the union of every group`() {
        val groups = NeverBlockedPackages.CALLS + NeverBlockedPackages.SYSTEM_SURFACES +
            NeverBlockedPackages.SYSTEM_DIALOGS + NeverBlockedPackages.KEYBOARDS + NeverBlockedPackages.LAUNCHERS
        assertTrue(NeverBlockedPackages.packages == groups)
    }
}
