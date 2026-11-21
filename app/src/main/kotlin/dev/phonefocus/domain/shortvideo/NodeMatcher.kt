package dev.phonefocus.domain.shortvideo

/**
 * A condition on a single [ScreenNode]. The building blocks of the detection rules in
 * [ShortVideoRules]: plain data, so rules read like a table and are easy to update when an
 * app changes its UI.
 */
sealed interface NodeMatcher {
    fun matches(node: ScreenNode): Boolean

    /** The full view id equals [id], e.g. `com.instagram.android:id/clips_tab`. */
    data class ViewId(val id: String) : NodeMatcher {
        override fun matches(node: ScreenNode): Boolean = node.viewId == id
    }

    /** The full view id starts with [prefix], e.g. `com.instagram.android:id/clips_viewer_`. */
    data class ViewIdPrefix(val prefix: String) : NodeMatcher {
        override fun matches(node: ScreenNode): Boolean = node.viewId?.startsWith(prefix) == true
    }

    /** The content description equals [text], ignoring case and surrounding blanks. */
    data class ContentDescription(val text: String) : NodeMatcher {
        override fun matches(node: ScreenNode): Boolean =
            node.contentDescription?.trim()?.equals(text, ignoreCase = true) == true
    }

    /** The class name equals [name], e.g. `androidx.viewpager.widget.ViewPager`. */
    data class ClassName(val name: String) : NodeMatcher {
        override fun matches(node: ScreenNode): Boolean = node.className == name
    }

    /** The node is selected (the current tab). */
    data object Selected : NodeMatcher {
        override fun matches(node: ScreenNode): Boolean = node.isSelected
    }

    /** Every one of [matchers] holds for the same node. */
    data class AllOf(val matchers: List<NodeMatcher>) : NodeMatcher {
        constructor(vararg matchers: NodeMatcher) : this(matchers.toList())

        override fun matches(node: ScreenNode): Boolean = matchers.all { it.matches(node) }
    }
}

/**
 * One way to recognise a short-video screen: the screen shows one when any visible node
 * matches [matcher]. [name] says which rule fired (logs, debug screen).
 */
data class ShortVideoRule(val name: String, val matcher: NodeMatcher)
