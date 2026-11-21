package dev.phonefocus.infrastructure.persistence

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.phonefocus.application.FocusRecord
import dev.phonefocus.application.TimeCheckpoint
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FocusRecordCodecTest {
    private val unlockAt = Instant.parse("2026-01-02T08:00:00.123456789Z")
    private val policy = AppPolicy(
        mapOf(
            "org.phone" to AppRule.Allowed,
            "com.instagram" to AppRule.ShortVideoBlocked,
            "com.game" to AppRule.Blocked,
        ),
    )

    private fun roundTrip(record: FocusRecord): FocusRecord {
        val prefs = mutablePreferencesOf()
        FocusRecordCodec.encode(record, prefs)
        return FocusRecordCodec.decode(prefs)
    }

    @Test
    fun `nothing stored decodes to the initial record`() {
        assertEquals(FocusRecord.INITIAL, FocusRecordCodec.decode(emptyPreferences()))
    }

    @Test
    fun `every state round-trips exactly`() {
        val checkpoint = TimeCheckpoint(Instant.parse("2026-01-01T08:00:00.5Z"), Duration.ofMillis(123_456), 7)
        val records = listOf(
            FocusRecord(FocusSession.restore(FocusState.Unlocked, AppPolicy.EMPTY, ExitDelay.TWELVE_HOURS)),
            FocusRecord(FocusSession.restore(FocusState.Focused, policy, ExitDelay.SEVEN_DAYS)),
            FocusRecord(FocusSession.restore(FocusState.ExitPending(unlockAt), policy, ExitDelay(Duration.ofMinutes(1))), checkpoint),
            FocusRecord(
                FocusSession.restore(FocusState.ExitPending(unlockAt), policy, ExitDelay.ONE_DAY),
                checkpoint,
                countdownStartedAt = unlockAt.minus(Duration.ofDays(1)),
            ),
        )
        records.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `encoding replaces a previous checkpoint and countdown start`() {
        val prefs = mutablePreferencesOf()
        val pending = FocusSession.restore(FocusState.ExitPending(unlockAt), policy, ExitDelay.ONE_DAY)
        FocusRecordCodec.encode(FocusRecord(pending, TimeCheckpoint(unlockAt, Duration.ZERO, 1), unlockAt), prefs)
        val focused = FocusRecord(FocusSession.restore(FocusState.Focused, policy, ExitDelay.ONE_DAY))
        FocusRecordCodec.encode(focused, prefs)
        assertEquals(focused, FocusRecordCodec.decode(prefs))
    }

    @Test
    fun `unreadable data fails closed and never unlocks`() {
        val unknownState = mutablePreferencesOf(stringPreferencesKey("state") to "garbage")
        assertEquals(FocusState.Focused, FocusRecordCodec.decode(unknownState).session.state)

        val pendingWithoutUnlockAt = mutablePreferencesOf(
            stringPreferencesKey("state") to "exit_pending",
            stringPreferencesKey("unlock_at") to "not a time",
            stringPreferencesKey("exit_delay") to "PT-5S",
            stringSetPreferencesKey("policy") to setOf("com.app=Whatever", "org.phone=Allowed", "broken"),
        )
        val decoded = FocusRecordCodec.decode(pendingWithoutUnlockAt).session
        assertEquals(FocusState.Focused, decoded.state)
        assertEquals(ExitDelay.ONE_DAY, decoded.exitDelay)
        assertEquals(AppPolicy(mapOf("com.app" to AppRule.Blocked, "org.phone" to AppRule.Allowed)), decoded.policy)
    }

    @Test
    fun `the corruption fallback restores a Focused session, never Unlocked`() {
        val session = FocusRecordCodec.decode(FocusRecordCodec.corruptionFallback()).session
        assertEquals(FocusState.Focused, session.state)
        assertEquals(AppPolicy.EMPTY, session.policy)
        assertEquals(ExitDelay.ONE_DAY, session.exitDelay)
    }
}
