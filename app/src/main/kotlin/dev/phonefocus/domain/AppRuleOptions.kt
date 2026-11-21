package dev.phonefocus.domain

/** Which [AppRule]s can be chosen for an app: in the focus setup (#7) and when making focus stricter (#9). */
object AppRuleOptions {
    private val ALWAYS_ALLOWED = listOf(AppRule.Allowed)
    private val WITH_SHORT_VIDEO = listOf(AppRule.Allowed, AppRule.ShortVideoBlocked, AppRule.Blocked)
    private val DEFAULT = listOf(AppRule.Allowed, AppRule.Blocked)

    /**
     * The rules offered for [packageName], least strict first: only [AppRule.Allowed] for an
     * essential app, all three for apps in [ShortVideoApps], otherwise Allowed and Blocked.
     */
    fun forPackage(packageName: String, essentialApps: EssentialApps): List<AppRule> = when {
        packageName in essentialApps -> ALWAYS_ALLOWED
        ShortVideoApps.supports(packageName) -> WITH_SHORT_VIDEO
        else -> DEFAULT
    }

    /**
     * The rules offered when making focus stricter (#9) for [packageName], whose rule is now
     * [current]: [current] first (keeping it changes nothing), then the stricter rules from
     * [forPackage], least strict first. Never a looser rule, so an essential app (only Allowed)
     * and a [AppRule.Blocked] app have nothing to choose. Convenience for the UI only:
     * [FocusSession.tighten] still rejects any loosening.
     */
    fun stricterThan(packageName: String, current: AppRule, essentialApps: EssentialApps): List<AppRule> =
        listOf(current) + forPackage(packageName, essentialApps).filter { it.strictness > current.strictness }
}
