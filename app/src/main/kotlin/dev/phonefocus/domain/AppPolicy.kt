package dev.phonefocus.domain

/**
 * Value object: the [AppRule] per package name.
 *
 * Packages that are not listed (including newly installed apps) are [AppRule.Blocked].
 */
class AppPolicy(rules: Map<String, AppRule> = emptyMap()) {
    val rules: Map<String, AppRule> = rules.toMap()

    /** The rule for [packageName]; unlisted packages default to [AppRule.Blocked]. */
    fun ruleFor(packageName: String): AppRule = rules[packageName] ?: AppRule.Blocked

    /** Packages whose rule lets them be launched, i.e. [AppRule.Allowed] or [AppRule.ShortVideoBlocked]. */
    fun launchablePackages(): Set<String> =
        rules.filterValues { it.isLaunchable }.keys

    /**
     * The changes in [changes] that would loosen this policy, in package-name order. A change
     * loosens the policy when the new rule is less strict than [ruleFor] the package (so listing
     * an unlisted package as anything but [AppRule.Blocked] loosens it too).
     */
    fun looseningsIn(changes: Map<String, AppRule>): List<Loosening.AppRuleLoosened> =
        changes.entries
            .sortedBy { it.key }
            .filterNot { (pkg, rule) -> ruleFor(pkg).allowsChangeTo(rule) }
            .map { (pkg, rule) -> Loosening.AppRuleLoosened(pkg, ruleFor(pkg), rule) }

    /** A new policy with [changes] applied on top of this one. Does not check strictness. */
    fun with(changes: Map<String, AppRule>): AppPolicy = AppPolicy(rules + changes)

    override fun equals(other: Any?): Boolean = other is AppPolicy && other.rules == rules

    override fun hashCode(): Int = rules.hashCode()

    override fun toString(): String = "AppPolicy($rules)"

    companion object {
        /** No listed apps: everything is blocked while focused. */
        val EMPTY: AppPolicy = AppPolicy()
    }
}
