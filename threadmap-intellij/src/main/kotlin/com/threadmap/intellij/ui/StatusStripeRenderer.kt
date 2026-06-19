package com.threadmap.intellij.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBFont
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
        border = JBUI.Borders.customLine(stripeColor(NodePresentation.statusStyle(node)), 0, 3, 0, 0)
        font = JBFont.create(java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, font.size))
        val text = NodePresentation.compactSignature(node)
        toolTipText = node.signature
        if (node.isCollapsed) {
            append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        } else {
            append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        if (NodePresentation.isDigWorthy(node)) {
            append("  ★", SimpleTextAttributes(
                SimpleTextAttributes.STYLE_BOLD,
                JBColor(Color(0xA66A00), Color(0xE5B454))
            ))
        }
    }

    private fun stripeColor(style: StatusStyle): Color = when (style) {
        StatusStyle.UNKNOWN -> JBColor(Color(0x9AA0A6), Color(0x6B7280))
        StatusStyle.HALF -> JBColor(Color(0xD59B22), Color(0xE5B454))
        StatusStyle.MASTERED -> JBColor(Color(0x4CAF50), Color(0x6FBF73))
        StatusStyle.RISKY -> JBColor(Color(0xD64545), Color(0xF07167))
    }
}
