package dev.phonefocus.infrastructure.system

import android.view.accessibility.AccessibilityNodeInfo
import dev.phonefocus.domain.shortvideo.ScreenNode

/**
 * Adapts an [AccessibilityNodeInfo] tree to the domain's [ScreenNode] (#12). Lazy: children are
 * only fetched from the app when the tree walk reaches them, so a detection that matches near
 * the top reads few nodes. The node's text is never read.
 */
class AccessibilityScreenNode(private val info: AccessibilityNodeInfo) : ScreenNode {
    override val viewId: String? get() = info.viewIdResourceName
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val className: String? get() = info.className?.toString()
    override val isSelected: Boolean get() = info.isSelected
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): ScreenNode? =
        runCatching { info.getChild(index) }.getOrNull()?.let(::AccessibilityScreenNode)
}
