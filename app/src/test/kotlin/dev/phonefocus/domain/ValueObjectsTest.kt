package dev.phonefocus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ValueObjectsTest {
    @Test
    fun `AppRule strictness order is Allowed then ShortVideoBlocked then Blocked`() {
        assertTrue(AppRule.Allowed.allowsChangeTo(AppRule.ShortVideoBlocked))
        assertTrue(AppRule.ShortVideoBlocked.allowsChangeTo(AppRule.Blocked))
        assertTrue(AppRule.Allowed.allowsChangeTo(AppRule.Blocked))
        assertFalse(AppRule.Blocked.allowsChangeTo(AppRule.ShortVideoBlocked))
        assertFalse(AppRule.Blocked.allowsChangeTo(AppRule.Allowed))
        assertFalse(AppRule.ShortVideoBlocked.allowsChangeTo(AppRule.Allowed))
        AppRule.entries.forEach { assertTrue(it.allowsChangeTo(it)) }
    }

    @Test
    fun `AppPolicy defaults unlisted packages to Blocked`() {
        val policy = AppPolicy(mapOf("a" to AppRule.Allowed))
        assertEquals(AppRule.Allowed, policy.ruleFor("a"))
        assertEquals(AppRule.Blocked, policy.ruleFor("b"))
        assertEquals(AppRule.Blocked, AppPolicy.EMPTY.ruleFor("a"))
    }

    @Test
    fun `AppPolicy is not affected by later changes to the source map`() {
        val source = mutableMapOf("a" to AppRule.Allowed)
        val policy = AppPolicy(source)
        source["a"] = AppRule.Blocked
        assertEquals(AppRule.Allowed, policy.ruleFor("a"))
    }

    @Test
    fun `AppPolicy launchablePackages excludes Blocked`() {
        val policy = AppPolicy(mapOf("a" to AppRule.Allowed, "s" to AppRule.ShortVideoBlocked, "b" to AppRule.Blocked))
        assertEquals(setOf("a", "s"), policy.launchablePackages())
    }

    @Test
    fun `ExitDelay compares by duration`() {
        assertTrue(ExitDelay.TWELVE_HOURS < ExitDelay.ONE_DAY)
        assertTrue(ExitDelay.SEVEN_DAYS > ExitDelay.TWO_DAYS)
        assertEquals(ExitDelay.ONE_DAY, ExitDelay(Duration.ofHours(24)))
        assertEquals(ExitDelay.PRESETS.sorted(), ExitDelay.PRESETS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ExitDelay rejects zero`() {
        ExitDelay(Duration.ZERO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ExitDelay rejects negative durations`() {
        ExitDelay(Duration.ofHours(-1))
    }
}
