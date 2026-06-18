package com.threadmap.intellij.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.intellij.model.StatusStyle
import com.threadmap.core.annotate.AnnotatedNode
import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class StatusStripeRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? AnnotatedNode ?: return
        border = JBUI.Borders.customLine(stripeColor(NodePresentation.statusStyle(node)), 0, 4, 0, 0)
        val text = NodePresentation.shortSignature(node) + if (NodePresentation.isDigWorthy(node)) " ★" else ""
        if (node.isCollapsed) {
            append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        } else {
            append(text)
        }
    }

    private fun stripeColor(style: StatusStyle): Color = when (style) {
        StatusStyle.UNKNOWN -> JBColor.GRAY
        StatusStyle.HALF -> JBColor.YELLOW
        StatusStyle.MASTERED -> JBColor.GREEN
        StatusStyle.RISKY -> JBColor.RED
    }
}
