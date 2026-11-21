package dev.phonefocus.application

/**
 * Something the device must be set up for before focus can start (#7): this app as the default
 * home screen (the focus launcher, #8) and its accessibility service enabled (#11).
 */
enum class StartPrerequisite { DefaultHome, AccessibilityService }

/** Whether a [StartPrerequisite] is in place. */
enum class PrerequisiteStatus {
    /** Set up. */
    Met,

    /** The feature exists in this build but the user has not set it up yet: guide them. */
    Missing,

    /**
     * This build does not contain the feature yet (no home activity / no accessibility service
     * declared), so it cannot be set up and is not required. Becomes [Met] or [Missing]
     * automatically once the feature ships.
     */
    NotAvailable,
}

/** Checks the [StartPrerequisite]s on the device. Implemented in `infrastructure`. */
interface StartPrerequisites {
    suspend fun status(prerequisite: StartPrerequisite): PrerequisiteStatus

    /** Every prerequisite with its status, in declaration order. */
    suspend fun statuses(): Map<StartPrerequisite, PrerequisiteStatus> =
        StartPrerequisite.entries.associateWith { status(it) }

    /** The prerequisites that block starting focus: only [PrerequisiteStatus.Missing] ones. */
    suspend fun missing(): List<StartPrerequisite> =
        statuses().filterValues { it == PrerequisiteStatus.Missing }.keys.toList()
}
