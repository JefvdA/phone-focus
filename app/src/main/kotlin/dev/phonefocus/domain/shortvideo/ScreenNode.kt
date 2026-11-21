package dev.phonefocus.domain.shortvideo

/**
 * A simplified, read-only view of one node of an app's on-screen view tree, as far as
 * short-video detection (#12) needs it. Infrastructure adapts the accessibility node tree to
 * this, lazily; tests build trees from [SimpleScreenNode].
 *
 * Only identifiers are exposed (view id, content description, class name) and two flags: the
 * text the user reads or types is never part of it.
 */
interface ScreenNode {
    /** Full resource id, e.g. `com.instagram.android:id/clips_tab`, when the app sets one. */
    val viewId: String?

    /** The accessibility label, e.g. "Reels" on a tab icon. */
    val contentDescription: String?

    /** The widget class, e.g. `android.widget.FrameLayout`. */
    val className: String?

    /** Selected, e.g. the current tab of a bottom navigation bar. */
    val isSelected: Boolean

    /** On screen now. Off-screen pages (a view pager's neighbours) are in the tree but not visible. */
    val isVisibleToUser: Boolean

    val childCount: Int

    /** The child at [index], or null when it can no longer be read (the tree changed meanwhile). */
    fun child(index: Int): ScreenNode?
}

/** A plain in-memory [ScreenNode]. */
data class SimpleScreenNode(
    override val viewId: String? = null,
    override val contentDescription: String? = null,
    override val className: String? = null,
    override val isSelected: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    val children: List<SimpleScreenNode> = emptyList(),
) : ScreenNode {
    override val childCount: Int get() = children.size

    override fun child(index: Int): ScreenNode? = children.getOrNull(index)
}

/** Walks a [ScreenNode] tree. */
object ScreenTree {
    /**
     * Upper bound of nodes looked at per walk. Keeps one inspection cheap even for a huge or
     * changing tree; the short-video markers sit near the top of the apps' trees.
     */
    const val DEFAULT_MAX_NODES: Int = 3_000

    /**
     * The first visible node (depth first, pre-order) for which [predicate] holds, or null.
     * Invisible nodes are skipped together with their subtree, and at most [maxNodes] nodes
     * are looked at.
     */
    fun firstVisible(
        root: ScreenNode,
        maxNodes: Int = DEFAULT_MAX_NODES,
        predicate: (ScreenNode) -> Boolean,
    ): ScreenNode? {
        val stack = ArrayDeque<ScreenNode>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < maxNodes) {
            val node = stack.removeLast()
            visited++
            if (!node.isVisibleToUser) continue
            if (predicate(node)) return node
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let(stack::addLast)
        }
        return null
    }
}
