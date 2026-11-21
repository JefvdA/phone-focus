package dev.phonefocus.domain

/**
 * Changes requested through `tighten`: new rules for some packages and/or a new exit delay.
 * Every change must be at least as strict as what it replaces.
 */
data class Tightening(
    val rules: Map<String, AppRule> = emptyMap(),
    val exitDelay: ExitDelay? = null,
)
