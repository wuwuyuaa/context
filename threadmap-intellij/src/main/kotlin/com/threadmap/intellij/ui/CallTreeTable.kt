package com.threadmap.intellij.ui

import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.intellij.model.StatusStyle
import com.threadmap.core.annotate.AnnotatedNode
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
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
    // 副作用列要容纳带边框的药丸徽章，比纯文本宽一些
    table.columnModel.getColumn(3).preferredWidth = JBUI.scale(112)
    table.columnModel.getColumn(3).minWidth = JBUI.scale(64)
    table.columnModel.getColumn(3).maxWidth = JBUI.scale(140)
    table.columnModel.getColumn(1).cellRenderer = MutedTextRenderer()
    table.columnModel.getColumn(2).cellRenderer = StatusTextRenderer()
    table.columnModel.getColumn(3).cellRenderer = SideEffectPillRenderer()
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

/** 副作用类型 → 配色;树徽章与详情面板共用,保证视觉一致。 */
object SideEffectStyle {
    fun background(tag: String): Color = when {
        tag.contains("DB", true) || tag.contains("表") -> JBColor(Color(0xFDEBEC), Color(0x4A292B))
        tag.contains("API", true) || tag.contains("外部") -> JBColor(Color(0xE8F1FC), Color(0x23384F))
        tag.contains("消息") || tag.contains("MQ", true) -> JBColor(Color(0xF5EDFF), Color(0x392B49))
        else -> JBColor(Color(0xEEF0F3), Color(0x34363A))
    }

    fun border(tag: String): Color = when {
        tag.contains("DB", true) || tag.contains("表") -> JBColor(Color(0xE4A2A6), Color(0x7C4448))
        tag.contains("API", true) || tag.contains("外部") -> JBColor(Color(0x9ABCE2), Color(0x41658B))
        tag.contains("消息") || tag.contains("MQ", true) -> JBColor(Color(0xC4A4E8), Color(0x684E83))
        else -> JBColor.border()
    }
}

/** 副作用列:每个副作用渲染成带边框的小药丸,与详情面板一致。 */
private class SideEffectPillRenderer : TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        selected: Boolean,
        focus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(3)))
        panel.background = if (selected) table.selectionBackground else table.background
        val text = value?.toString().orEmpty()
        if (text.isNotBlank()) {
            panel.toolTipText = text
            text.split(", ").filter { it.isNotBlank() }.forEach { tag ->
                panel.add(JLabel(tag).apply {
                    isOpaque = true
                    background = SideEffectStyle.background(tag)
                    font = JBFont.small()
                    border = BorderFactory.createCompoundBorder(
                        JBUI.Borders.customLine(SideEffectStyle.border(tag), 1),
                        JBUI.Borders.empty(1, 6)
                    )
                })
            }
        }
        return panel
    }
}
