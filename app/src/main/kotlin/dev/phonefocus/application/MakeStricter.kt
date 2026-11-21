package dev.phonefocus.application

import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.Tightening

/**
 * One app on the "Make stricter" screen (#9): its [current] rule, the [options] it can get (only
 * [current] and stricter ones, see [AppRuleOptions.stricterThan]) and the [rule] chosen now.
 */
data class StricterAppChoice(
    val app: InstalledApp,
    val current: AppRule,
    val options: List<AppRule>,
    val rule: AppRule,
) {
    val packageName: String get() = app.packageName

    val changed: Boolean get() = rule != current
}

/**
 * The choices on the "Make stricter" screen (C4, #9) while focused: a stricter rule per
 * currently allowed app and a longer exit delay.
 *
 * Built by [create] from the session, the catalogue and the user's edits, so it can be rebuilt
 * whenever either changes without losing edits. Only stricter choices are offered, so
 * [tightening] never loosens anything; the domain ([FocusSession.tighten]) is still the final
 * guard and rejects a loosening even if this form is bypassed.
 */
data class MakeStricterForm(
    val apps: List<StricterAppChoice>,
    val currentDelay: ExitDelay,
    val exitDelay: ExitDelay,
    val exitDelayOptions: List<ExitDelayChoice>,
) {
    /** The apps whose rule the user changed. */
    val changedApps: List<StricterAppChoice> get() = apps.filter { it.changed }

    val delayChanged: Boolean get() = exitDelay != currentDelay

    /** True when applying would change something. */
    val hasChanges: Boolean get() = delayChanged || changedApps.isNotEmpty()

    /** The changes to pass to `tighten`: only what differs from the session. */
    fun tightening(): Tightening = Tightening(
        rules = changedApps.associate { it.packageName to it.rule },
        exitDelay = exitDelay.takeIf { delayChanged },
    )

    companion object {
        /**
         * The form for [session] with the user's [ruleEdits] and [delayEdit] on top. Edits that
         * are not offered (anymore), e.g. because the session got stricter meanwhile, are ignored.
         *
         * Lists the installed apps in [catalogue] (in its order) that the session's policy lets
         * launch (Allowed or No short videos), except essential apps: they are always allowed
         * and cannot be made stricter. Blocked and unlisted apps are left out, since they cannot
         * get any stricter. Delays come from [ExitDelayOptions.forTightening] over [exitDelays].
         */
        fun create(
            catalogue: List<AppCatalogueEntry>,
            session: FocusSession,
            exitDelays: List<ExitDelay>,
            ruleEdits: Map<String, AppRule> = emptyMap(),
            delayEdit: ExitDelay? = null,
        ): MakeStricterForm {
            val essentialApps = EssentialApps(catalogue.filter { it.essential }.map { it.app.packageName }.toSet())
            val apps = catalogue
                .distinctBy { it.app.packageName }
                .filter { !it.essential && session.policy.ruleFor(it.app.packageName).isLaunchable }
                .map { entry ->
                    val pkg = entry.app.packageName
                    val current = session.policy.ruleFor(pkg)
                    val options = AppRuleOptions.stricterThan(pkg, current, essentialApps)
                    val rule = ruleEdits[pkg]?.takeIf { it in options } ?: current
                    StricterAppChoice(entry.app, current, options, rule)
                }
            val current = session.exitDelay
            val delayOptions = ExitDelayOptions.forTightening(current, exitDelays)
            val delay = delayEdit?.takeIf { edit -> delayOptions.any { it.enabled && it.delay == edit } } ?: current
            return MakeStricterForm(apps, current, delay, delayOptions)
        }
    }
}
