package dev.phonefocus.domain.shortvideo

/** A short-video screen was recognised by [rule]. */
data class ShortVideoMatch(val rule: ShortVideoRule)

/** Recognises one app's short-video screens (strategy per app, see [ShortVideoDetectors]). */
fun interface ShortVideoDetector {
    /** The rule that recognised a short-video screen in [root], or null when there is none. */
    fun detect(root: ScreenNode): ShortVideoMatch?
}

/** A [ShortVideoDetector] that fires when any visible node matches one of [rules]. */
class RuleBasedDetector(
    val rules: List<ShortVideoRule>,
    private val maxNodes: Int = ScreenTree.DEFAULT_MAX_NODES,
) : ShortVideoDetector {
    override fun detect(root: ScreenNode): ShortVideoMatch? {
        if (rules.isEmpty()) return null
        var matched: ShortVideoRule? = null
        ScreenTree.firstVisible(root, maxNodes) { node ->
            matched = rules.firstOrNull { it.matcher.matches(node) }
            matched != null
        }
        return matched?.let(::ShortVideoMatch)
    }
}

/** The [ShortVideoDetector] of each app, registered by package name. */
class ShortVideoDetectors(private val byPackage: Map<String, ShortVideoDetector>) {
    /** The packages with a detector. */
    val packages: Set<String> get() = byPackage.keys

    /** What recognised a short-video screen of [packageName] in [root]; null for unknown apps. */
    fun detect(packageName: String, root: ScreenNode): ShortVideoMatch? = byPackage[packageName]?.detect(root)

    companion object {
        /** Rule-based detectors for every app in [ShortVideoRules]. */
        val DEFAULT: ShortVideoDetectors =
            ShortVideoDetectors(ShortVideoRules.BY_PACKAGE.mapValues { (_, rules) -> RuleBasedDetector(rules) })
    }
}
