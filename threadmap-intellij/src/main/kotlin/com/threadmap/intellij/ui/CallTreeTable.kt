package com.threadmap.intellij.ui

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.intellij.model.StatusStyle
import com.threadmap.core.annotate.AnnotatedNode
import java.awt.Color
import javax.swing.JTable
import javax.swing.tree.DefaultMutableTreeNode

private fun annotatedOf(value: Any?): AnnotatedNode? =
    ((value as? DefaultMutableTreeNode)?.userObject) as? AnnotatedNode

class SignatureColumn : ColumnInfo<DefaultMutableTreeNode, String>("调用链路") {
    override fun valueOf(item: DefaultMutableTreeNode): String {
        val n = annotatedOf(item) ?: return ""
        val star = if (NodePresentation.isDigWorthy(n)) " ★" else ""
        return NodePresentation.compactSignature(n) + star
    }
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
}

class SummaryColumn : ColumnInfo<DefaultMutableTreeNode, String>("摘要 (AI)") {
    override fun valueOf(item: DefaultMutableTreeNode): String =
        annotatedOf(item)?.let { NodePresentation.summary(it) } ?: ""
}

class StatusColumn : ColumnInfo<DefaultMutableTreeNode, String>("状态") {
    override fun valueOf(item: DefaultMutableTreeNode): String = when (annotatedOf(item)?.let { NodePresentation.statusStyle(it) }) {
        StatusStyle.MASTERED -> "已掌握"
        StatusStyle.HALF -> "半懂"
        StatusStyle.RISKY -> "高风险"
        else -> "未知"
    }
}

class SideEffectColumn : ColumnInfo<DefaultMutableTreeNode, String>("副作用") {
    override fun valueOf(item: DefaultMutableTreeNode): String =
        annotatedOf(item)?.let { NodePresentation.sideEffects(it) } ?: ""
}

fun buildTreeTable(root: DefaultMutableTreeNode): TreeTableView {
    val columns = arrayOf<ColumnInfo<*, *>>(
        SignatureColumn(), SummaryColumn(), StatusColumn(), SideEffectColumn())
    val model = ListTreeTableModelOnColumns(root, columns)
    val table = TreeTableView(model)
    table.tree.isRootVisible = true
    table.rowHeight = JBUI.scale(28)
    table.tree.rowHeight = JBUI.scale(28)
    table.setShowGrid(false)
    table.tableHeader.reorderingAllowed = false
    table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    table.setTreeCellRenderer(StatusStripeRenderer())
    // 签名列(树列)要容纳缩进+等宽签名+★，给足宽度避免把 check 截成 checl
    table.columnModel.getColumn(0).preferredWidth = JBUI.scale(300)
    table.columnModel.getColumn(0).minWidth = JBUI.scale(230)
    // 摘要列不设上限：作为弹性列吃掉多余宽度；窄停靠时收得最狠，把空间让给签名+副作用
    table.columnModel.getColumn(1).preferredWidth = JBUI.scale(240)
    table.columnModel.getColumn(1).minWidth = JBUI.scale(110)
    // 状态/副作用是定长标签，收窄并封顶，多余空间让给摘要
    table.columnModel.getColumn(2).preferredWidth = JBUI.scale(76)
    table.columnModel.getColumn(2).minWidth = JBUI.scale(66)
    table.columnModel.getColumn(2).maxWidth = JBUI.scale(92)
    table.columnModel.getColumn(3).preferredWidth = JBUI.scale(84)
    table.columnModel.getColumn(3).minWidth = JBUI.scale(56)
    table.columnModel.getColumn(3).maxWidth = JBUI.scale(110)
    table.columnModel.getColumn(1).cellRenderer = MutedTextRenderer()
    table.columnModel.getColumn(2).cellRenderer = StatusTextRenderer()
    table.columnModel.getColumn(3).cellRenderer = SideEffectTextRenderer()
    return table
}

private class MutedTextRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) {
        val text = value?.toString().orEmpty()
        toolTipText = text.takeIf { it.isNotBlank() }
        append(text, if (selected) {
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        } else {
            SimpleTextAttributes.GRAYED_ATTRIBUTES
        })
    }
}

private class StatusTextRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) {
        val text = value?.toString().orEmpty()
        val color = when (text) {
            "已掌握" -> JBColor(Color(0x2E7D32), Color(0x6FBF73))
            "半懂" -> JBColor(Color(0xA66A00), Color(0xE5B454))
            "高风险" -> JBColor(Color(0xB3261E), Color(0xF07167))
            else -> JBColor.GRAY
        }
        append("● ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
        append(text, if (selected) SimpleTextAttributes.REGULAR_ATTRIBUTES
        else SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
    }
}

private class SideEffectTextRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
        table: JTable,
        value: Any?,
        selected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) {
        val text = value?.toString().orEmpty()
        toolTipText = text.takeIf { it.isNotBlank() }
        val color = when {
            text.contains("DB", true) || text.contains("表") -> JBColor(Color(0xB3261E), Color(0xF07167))
            text.contains("API", true) || text.contains("外部") -> JBColor(Color(0x245EA8), Color(0x78A9E6))
            text.contains("消息") || text.contains("MQ", true) -> JBColor(Color(0x7955A6), Color(0xC39BE8))
            else -> JBColor.GRAY
        }
        append(text, if (selected) SimpleTextAttributes.REGULAR_ATTRIBUTES
        else SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color))
    }
}
