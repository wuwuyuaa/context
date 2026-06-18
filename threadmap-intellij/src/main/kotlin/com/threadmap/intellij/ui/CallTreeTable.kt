package com.threadmap.intellij.ui

import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.intellij.model.StatusStyle
import com.threadmap.core.annotate.AnnotatedNode
import javax.swing.tree.DefaultMutableTreeNode

private fun annotatedOf(value: Any?): AnnotatedNode? =
    ((value as? DefaultMutableTreeNode)?.userObject) as? AnnotatedNode

class SignatureColumn : ColumnInfo<DefaultMutableTreeNode, String>("签名") {
    override fun valueOf(item: DefaultMutableTreeNode): String {
        val n = annotatedOf(item) ?: return ""
        val star = if (NodePresentation.isDigWorthy(n)) " ★" else ""
        return NodePresentation.shortSignature(n) + star
    }
    override fun getColumnClass(): Class<*> = TreeTableModel::class.java
}

class SummaryColumn : ColumnInfo<DefaultMutableTreeNode, String>("摘要") {
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
    table.setTreeCellRenderer(StatusStripeRenderer())
    return table
}
