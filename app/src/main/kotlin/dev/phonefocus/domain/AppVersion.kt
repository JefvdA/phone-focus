package dev.phonefocus.domain

/**
 * Placeholder so the `domain` layer exists from the start.
 *
 * The domain layer is pure Kotlin: it must never import `android.*` or `androidx.*`.
 * The real model (FocusSession, AppPolicy, ExitDelay, ...) arrives with later issues.
 */
object AppVersion {
    const val NAME: String = "phone-focus"

    fun describe(versionName: String): String = "$NAME $versionName"
}
