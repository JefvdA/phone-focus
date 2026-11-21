package dev.phonefocus.domain

import java.time.Instant

/**
 * Source of the current time. The domain never reads the system clock itself; the
 * infrastructure layer provides the real implementation and tests pass a fixed one.
 */
fun interface Clock {
    fun now(): Instant
}
