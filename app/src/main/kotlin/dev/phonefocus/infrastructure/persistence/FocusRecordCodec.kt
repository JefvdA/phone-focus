package dev.phonefocus.infrastructure.persistence

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
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
import java.time.Duration
import java.time.Instant

/**
 * Maps a [FocusRecord] to and from DataStore [Preferences]. Instants and durations are stored
 * as ISO-8601 strings, so they round-trip exactly.
 *
 * Decoding fails closed: a stored state that cannot be read is restored as focused rather than
 * unlocked (unknown state -> Focused, ExitPending without a readable `unlockAt` -> Focused,
 * unknown app rule -> Blocked), so corrupt data never ends focus.
 */
object FocusRecordCodec {
    private const val SCHEMA_VERSION = 1

    private val VERSION = intPreferencesKey("schema_version")
    private val STATE = stringPreferencesKey("state")
    private val UNLOCK_AT = stringPreferencesKey("unlock_at")
    private val EXIT_DELAY = stringPreferencesKey("exit_delay")
    private val POLICY = stringSetPreferencesKey("policy")
    private val CHECKPOINT_TRUSTED_TIME = stringPreferencesKey("checkpoint_trusted_time")
    private val CHECKPOINT_ELAPSED = stringPreferencesKey("checkpoint_elapsed_realtime")
    private val CHECKPOINT_BOOT_COUNT = intPreferencesKey("checkpoint_boot_count")

    private const val UNLOCKED = "unlocked"
    private const val FOCUSED = "focused"
    private const val EXIT_PENDING = "exit_pending"
    private const val RULE_SEPARATOR = '='

    fun encode(record: FocusRecord, into: MutablePreferences) {
        into.clear()
        val session = record.session
        into[VERSION] = SCHEMA_VERSION
        when (val state = session.state) {
            FocusState.Unlocked -> into[STATE] = UNLOCKED
            FocusState.Focused -> into[STATE] = FOCUSED
            is FocusState.ExitPending -> {
                into[STATE] = EXIT_PENDING
                into[UNLOCK_AT] = state.unlockAt.toString()
            }
        }
        into[EXIT_DELAY] = session.exitDelay.duration.toString()
        into[POLICY] = session.policy.rules.map { (pkg, rule) -> "$pkg$RULE_SEPARATOR${rule.name}" }.toSet()
        record.checkpoint?.let {
            into[CHECKPOINT_TRUSTED_TIME] = it.trustedTime.toString()
            into[CHECKPOINT_ELAPSED] = it.elapsedRealtime.toString()
            into[CHECKPOINT_BOOT_COUNT] = it.bootCount
        }
    }

    /**
     * What replaces a corrupt DataStore file: a Focused session with an empty policy (everything
     * blocked) and the default delay. Fails closed: corruption never unlocks the phone.
     */
    fun corruptionFallback(): Preferences = mutablePreferencesOf(VERSION to SCHEMA_VERSION, STATE to FOCUSED)

    fun decode(prefs: Preferences): FocusRecord {
        val stateName = prefs[STATE] ?: return FocusRecord.INITIAL
        val unlockAt = prefs[UNLOCK_AT]?.let(::parseInstant)
        val state = when {
            stateName == UNLOCKED -> FocusState.Unlocked
            stateName == EXIT_PENDING && unlockAt != null -> FocusState.ExitPending(unlockAt)
            else -> FocusState.Focused
        }
        val delay = prefs[EXIT_DELAY]?.let(::parseDelay) ?: ExitDelay.ONE_DAY
        val policy = AppPolicy(prefs[POLICY].orEmpty().mapNotNull(::parseRule).toMap())
        val checkpoint = run {
            val trusted = prefs[CHECKPOINT_TRUSTED_TIME]?.let(::parseInstant)
            val elapsed = prefs[CHECKPOINT_ELAPSED]?.let(::parseDuration)
            val boot = prefs[CHECKPOINT_BOOT_COUNT]
            if (trusted != null && elapsed != null && boot != null) TimeCheckpoint(trusted, elapsed, boot) else null
        }
        return FocusRecord(FocusSession.restore(state, policy, delay), checkpoint)
    }

    private fun parseRule(entry: String): Pair<String, AppRule>? {
        val i = entry.lastIndexOf(RULE_SEPARATOR)
        if (i <= 0) return null
        val rule = AppRule.entries.firstOrNull { it.name == entry.substring(i + 1) } ?: AppRule.Blocked
        return entry.substring(0, i) to rule
    }

    private fun parseInstant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()

    private fun parseDuration(s: String): Duration? = runCatching { Duration.parse(s) }.getOrNull()

    private fun parseDelay(s: String): ExitDelay? = parseDuration(s)?.let { runCatching { ExitDelay(it) }.getOrNull() }
}
