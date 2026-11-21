package dev.phonefocus.application

import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusError
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession

/** One app in the focus setup (#7): the catalogue [entry] and the [rule] currently chosen for it. */
data class SetupAppChoice(val entry: AppCatalogueEntry, val rule: AppRule) {
    val packageName: String get() = entry.app.packageName
}

/**
 * The choices on the focus setup screen (#7): a rule per installed app and an exit delay.
 *
 * Built by [create] from the catalogue, the defaults and the user's edits, so it can be rebuilt
 * whenever the catalogue changes (an app installed while the screen is open) without losing
 * edits. Every rule is one of the app's [AppCatalogueEntry.ruleOptions] and the delay is one of
 * [exitDelayOptions], so [policy] never contains a rule the domain would reject.
 */
data class FocusSetupForm(
    val apps: List<SetupAppChoice>,
    val exitDelay: ExitDelay,
    val exitDelayOptions: List<ExitDelay>,
) {
    /**
     * The policy to start focus with: the chosen rule for every app on the screen. Apps that are
     * not installed now are left out, so they are Blocked if installed during focus.
     */
    fun policy(): AppPolicy = AppPolicy(apps.associate { it.packageName to it.rule })

    companion object {
        /**
         * The form for [catalogue], starting from the last used [defaultPolicy] and
         * [defaultDelay] (the session keeps both while Unlocked, see [FocusSession]) with the
         * user's [ruleEdits] and [delayEdit] on top. Edits that are not offered (anymore) are
         * ignored.
         *
         * Defaults per app: the rule from [defaultPolicy] (unlisted apps are Blocked, as during
         * focus) when it is offered for the app, otherwise the least strict offered rule that is
         * at least as strict (e.g. Blocked for an app that no longer supports "No short videos").
         * Essential apps only offer Allowed, so they always get it.
         */
        fun create(
            catalogue: List<AppCatalogueEntry>,
            defaultPolicy: AppPolicy,
            defaultDelay: ExitDelay,
            exitDelayOptions: List<ExitDelay>,
            ruleEdits: Map<String, AppRule> = emptyMap(),
            delayEdit: ExitDelay? = null,
        ): FocusSetupForm {
            require(exitDelayOptions.isNotEmpty()) { "No exit delays to offer" }
            val apps = catalogue.map { entry ->
                val pkg = entry.app.packageName
                val edit = ruleEdits[pkg]?.takeIf { it in entry.ruleOptions }
                SetupAppChoice(entry, edit ?: defaultRule(entry, defaultPolicy.ruleFor(pkg)))
            }
            val delay = delayEdit?.takeIf { it in exitDelayOptions } ?: defaultDelay(defaultDelay, exitDelayOptions)
            return FocusSetupForm(apps, delay, exitDelayOptions)
        }

        /** The form with the last used policy and delay of [session] as defaults. */
        fun create(
            catalogue: List<AppCatalogueEntry>,
            session: FocusSession,
            exitDelayOptions: List<ExitDelay>,
            ruleEdits: Map<String, AppRule> = emptyMap(),
            delayEdit: ExitDelay? = null,
        ): FocusSetupForm =
            create(catalogue, session.policy, session.exitDelay, exitDelayOptions, ruleEdits, delayEdit)

        private fun defaultRule(entry: AppCatalogueEntry, last: AppRule): AppRule {
            val options = entry.ruleOptions
            if (last in options) return last
            return options.filter { it.strictness >= last.strictness }.minByOrNull { it.strictness }
                ?: options.maxBy { it.strictness }
        }

        /** [last] when offered, else the shortest longer option, else the longest option. */
        private fun defaultDelay(last: ExitDelay, options: List<ExitDelay>): ExitDelay =
            if (last in options) last else options.filter { it > last }.minOrNull() ?: options.max()
    }
}

/** The outcome of [StartFocusFromSetup]. */
sealed interface SetupStartResult {
    /** Focus has started with the form's policy and delay. */
    data object Started : SetupStartResult

    /** Nothing was started: these [prerequisites] must be set up first. */
    data class PrerequisitesMissing(val prerequisites: List<StartPrerequisite>) : SetupStartResult

    /** Nothing was started: the session rejected the command (e.g. focus is already on). */
    data class Rejected(val error: FocusError) : SetupStartResult
}

/**
 * "Start focus mode" on the setup screen (#7): checks the [StartPrerequisites] and, when none is
 * missing, starts focus with the form's policy and delay.
 */
class StartFocusFromSetup(
    private val commands: FocusCommands,
    private val prerequisites: StartPrerequisites,
) {
    suspend operator fun invoke(form: FocusSetupForm): SetupStartResult {
        val missing = prerequisites.missing()
        if (missing.isNotEmpty()) return SetupStartResult.PrerequisitesMissing(missing)
        return when (val result = commands.startFocus(form.policy(), form.exitDelay)) {
            is FocusResult.Success -> SetupStartResult.Started
            is FocusResult.Failure -> SetupStartResult.Rejected(result.error)
        }
    }
}
