package dev.phonefocus.domain

import dev.phonefocus.domain.AppRule.Allowed
import dev.phonefocus.domain.AppRule.Blocked
import dev.phonefocus.domain.AppRule.ShortVideoBlocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Short-video registry, rule options and essential apps (#5). */
class AppCatalogueRulesTest {
    private val self = "dev.phonefocus"
    private val settings = "com.android.settings"
    private val dialer = "com.samsung.android.dialer"
    private val essential = EssentialApps(setOf(self, settings, dialer))

    private fun FocusResult.ok(): FocusSession = (this as? FocusResult.Success)?.session
        ?: throw AssertionError("expected success, got $this")

    private fun FocusResult.err(): FocusError = (this as? FocusResult.Failure)?.error
        ?: throw AssertionError("expected failure, got $this")

    private fun focused(policy: AppPolicy = AppPolicy.EMPTY) =
        FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY).ok()

    // --- short-video registry --------------------------------------------------------------

    @Test
    fun `registry contains Instagram, YouTube, Facebook and Snapchat`() {
        assertEquals(
            setOf("com.instagram.android", "com.google.android.youtube", "com.facebook.katana", "com.snapchat.android"),
            ShortVideoApps.PACKAGES,
        )
        ShortVideoApps.PACKAGES.forEach { assertTrue(ShortVideoApps.supports(it)) }
        assertFalse(ShortVideoApps.supports("com.zhiliaoapp.musically"))
        assertFalse(ShortVideoApps.supports("com.instagram"))
    }

    // --- rule options ----------------------------------------------------------------------

    @Test
    fun `Instagram and YouTube get three options, other apps two`() {
        val three = listOf(Allowed, ShortVideoBlocked, Blocked)
        ShortVideoApps.PACKAGES.forEach { assertEquals(three, AppRuleOptions.forPackage(it, essential)) }
        assertEquals(listOf(Allowed, Blocked), AppRuleOptions.forPackage("com.whatsapp", essential))
    }

    @Test
    fun `essential apps can only be Allowed`() {
        listOf(self, settings, dialer).forEach {
            assertEquals(listOf(Allowed), AppRuleOptions.forPackage(it, essential))
        }
    }

    @Test
    fun `the dialer is always offered as Allowed, even when not essential`() {
        assertTrue(Allowed in AppRuleOptions.forPackage(dialer, EssentialApps.NONE))
    }

    @Test
    fun `an essential app in the short-video registry still only gets Allowed`() {
        val weird = EssentialApps(setOf(ShortVideoApps.YOUTUBE))
        assertEquals(listOf(Allowed), AppRuleOptions.forPackage(ShortVideoApps.YOUTUBE, weird))
    }

    // --- ShortVideoBlocked only for supported apps -----------------------------------------

    @Test
    fun `unsupportedRulesIn lists ShortVideoBlocked for apps outside the registry`() {
        val policy = AppPolicy(
            mapOf(
                "com.whatsapp" to ShortVideoBlocked,
                ShortVideoApps.INSTAGRAM to ShortVideoBlocked,
                "com.game" to Blocked,
                "com.app" to Allowed,
            ),
        )
        assertEquals(listOf(UnsupportedRule("com.whatsapp", ShortVideoBlocked)), policy.unsupportedRulesIn())
    }

    @Test
    fun `startFocus rejects ShortVideoBlocked for an unsupported app`() {
        val policy = AppPolicy(mapOf("com.b" to ShortVideoBlocked, "com.a" to ShortVideoBlocked))
        assertEquals(
            FocusError.UnsupportedRules(
                listOf(UnsupportedRule("com.a", ShortVideoBlocked), UnsupportedRule("com.b", ShortVideoBlocked)),
            ),
            FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY).err(),
        )
    }

    @Test
    fun `startFocus accepts ShortVideoBlocked for every registered app`() {
        val policy = AppPolicy(ShortVideoApps.PACKAGES.associateWith { ShortVideoBlocked })
        assertEquals(policy, focused(policy).policy)
    }

    @Test
    fun `tighten rejects Allowed to ShortVideoBlocked for an unsupported app but accepts Blocked`() {
        val session = focused(AppPolicy(mapOf("com.whatsapp" to Allowed)))
        assertEquals(
            FocusError.UnsupportedRules(listOf(UnsupportedRule("com.whatsapp", ShortVideoBlocked))),
            session.tighten(Tightening(rules = mapOf("com.whatsapp" to ShortVideoBlocked))).err(),
        )
        assertEquals(
            Blocked,
            session.tighten(Tightening(rules = mapOf("com.whatsapp" to Blocked))).ok().policy.ruleFor("com.whatsapp"),
        )
    }

    @Test
    fun `tighten still reports loosenings first`() {
        val session = focused(AppPolicy(mapOf("com.whatsapp" to Blocked)))
        assertTrue(
            session.tighten(Tightening(rules = mapOf("com.whatsapp" to ShortVideoBlocked))).err()
                is FocusError.NotStricter,
        )
    }

    @Test
    fun `a rejected tighten changes nothing`() {
        val session = focused(AppPolicy(mapOf("com.whatsapp" to Allowed, ShortVideoApps.YOUTUBE to Allowed)))
        val result = session.tighten(
            Tightening(rules = mapOf("com.whatsapp" to ShortVideoBlocked, ShortVideoApps.YOUTUBE to Blocked)),
        )
        assertTrue(result is FocusResult.Failure)
        assertEquals(Allowed, session.policy.ruleFor(ShortVideoApps.YOUTUBE))
    }

    // --- essential apps are never blocked --------------------------------------------------

    @Test
    fun `essential apps can launch while focused even when the policy blocks or omits them`() {
        val access = AppAccess(focused(AppPolicy(mapOf(settings to Blocked))), essential)
        assertTrue(access.canLaunch(self))
        assertTrue(access.canLaunch(settings))
        assertTrue(access.canLaunch(dialer))
        assertFalse(access.blocksShortVideos(settings))
    }

    @Test
    fun `essential apps can launch during ExitPending too`() {
        val pending = focused().requestExit(java.time.Instant.EPOCH).ok()
        assertTrue(AppAccess(pending, essential).canLaunch(dialer))
    }

    @Test
    fun `non-essential apps keep the policy rule and unlisted or new apps stay Blocked`() {
        val policy = AppPolicy(mapOf("com.allowed" to Allowed, ShortVideoApps.YOUTUBE to ShortVideoBlocked))
        val access = AppAccess(focused(policy), essential)
        assertTrue(access.canLaunch("com.allowed"))
        assertFalse(access.blocksShortVideos("com.allowed"))
        assertTrue(access.canLaunch(ShortVideoApps.YOUTUBE))
        assertTrue(access.blocksShortVideos(ShortVideoApps.YOUTUBE))
        assertFalse(access.canLaunch("com.newly.installed"))
        assertTrue(access.blocksShortVideos("com.newly.installed"))
    }

    @Test
    fun `allowedApps adds essential apps while focused and is empty while Unlocked`() {
        val policy = AppPolicy(mapOf("com.allowed" to Allowed, "com.game" to Blocked))
        assertEquals(setOf("com.allowed", self, settings, dialer), AppAccess(focused(policy), essential).allowedApps())
        assertEquals(emptySet<String>(), AppAccess(FocusSession.initial(policy), essential).allowedApps())
    }

    @Test
    fun `everything can launch while Unlocked`() {
        val access = AppAccess(FocusSession.initial(), essential)
        assertTrue(access.canLaunch("com.game"))
        assertFalse(access.blocksShortVideos(ShortVideoApps.INSTAGRAM))
    }

    @Test
    fun `essential apps do not change the session or loosen its policy`() {
        val session = focused(AppPolicy(mapOf(settings to Blocked)))
        AppAccess(session, essential).canLaunch(settings)
        assertEquals(Blocked, session.policy.ruleFor(settings))
        assertFalse(session.isLaunchAllowed(settings))
    }
}
