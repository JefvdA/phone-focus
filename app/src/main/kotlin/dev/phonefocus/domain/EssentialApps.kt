package dev.phonefocus.domain

/**
 * Value object: the packages that must stay usable while focused so the phone is never
 * bricked (#5): this app itself (the focus home screen and the only way out), the system
 * Settings and the default Phone/dialer app.
 *
 * Which packages these are depends on the device, so infrastructure resolves them and the
 * domain only holds the result. Essential apps are always launchable, whatever the policy says
 * (see [AppAccess]), and [AppRule.Allowed] is the only rule offered for them ([AppRuleOptions]).
 */
class EssentialApps(packages: Set<String>) {
    val packages: Set<String> = packages.toSet()

    operator fun contains(packageName: String): Boolean = packageName in packages

    override fun equals(other: Any?): Boolean = other is EssentialApps && other.packages == packages

    override fun hashCode(): Int = packages.hashCode()

    override fun toString(): String = "EssentialApps($packages)"

    companion object {
        val NONE: EssentialApps = EssentialApps(emptySet())
    }
}
