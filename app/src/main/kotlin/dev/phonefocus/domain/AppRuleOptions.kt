package dev.phonefocus.domain

/** Which [AppRule]s can be chosen for an app, e.g. in the focus setup (#7). */
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
}
