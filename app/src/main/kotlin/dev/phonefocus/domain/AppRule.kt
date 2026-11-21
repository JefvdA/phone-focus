package dev.phonefocus.domain

/**
 * How a single app may be used while focused.
 *
 * Rules are ordered by [strictness]: [Allowed] < [ShortVideoBlocked] < [Blocked]. While focused,
 * a rule may only move towards a stricter one (invariant 1 in #1).
 */
enum class AppRule(val strictness: Int) {
    /** The app can be launched and used normally. */
    Allowed(0),

    /** The app can be launched, but its short-video feed (Reels, Shorts, ...) is blocked. */
    ShortVideoBlocked(1),

    /** The app cannot be launched. */
    Blocked(2);

    /** True when moving from this rule to [other] does not loosen anything. */
    fun allowsChangeTo(other: AppRule): Boolean = other.strictness >= strictness

    /** True when this rule lets the app be launched. */
    val isLaunchable: Boolean get() = this != Blocked
}
