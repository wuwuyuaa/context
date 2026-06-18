package com.threadmap.intellij.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.threadmap.core.annotate.AnnotatedNode
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/** 选中节点的详情视图:逐字段标签 + 证据表格。空选时显示提示。 */
class NodeDetailPanel : JPanel(BorderLayout()) {
    private val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = JBUI.Borders.empty(8) }

    init {
        add(JBScrollPane(body), BorderLayout.CENTER)
        showEmpty()
    }

    fun showEmpty() {
        body.removeAll()
        body.add(JBLabel("选中左侧节点查看详情"))
        body.revalidate(); body.repaint()
    }

    fun show(node: AnnotatedNode) {
        body.removeAll()
        addRow("签名", node.signature, mono = true)
        addRow("位置", node.file + ":" + node.line)
        val a = node.annotation
        if (a != null) {
            addRow("摘要", a.summary())
            addRow("输入", a.inputs())
            addRow("输出", a.outputs())
            addRow("副作用", if (a.sideEffects().isEmpty()) "—" else a.sideEffects().joinToString(", "))
            body.add(JBLabel("证据").apply { border = JBUI.Borders.emptyTop(8) })
            body.add(evidenceTable(a.evidence().file(), a.evidence().lines(), a.evidence().calls()))
            if (a.digWorthy()) {
                addRow("待查理由", a.digReason() ?: "")
            }
        } else {
            addRow("标注", "(折叠/未标注节点)")
        }
        body.revalidate(); body.repaint()
    }

    private fun addRow(label: String, value: String, mono: Boolean = false) {
        val l = JBLabel("$label: $value")
        if (mono) l.font = com.intellij.util.ui.JBFont.create(java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, l.font.size))
        l.border = JBUI.Borders.emptyBottom(4)
        body.add(l)
    }

    private fun evidenceTable(file: String, lines: String, calls: List<String>): JBTable {
        val model = DefaultTableModel(arrayOf<Any>("文件", "行号", "关键被调"), 0)
        model.addRow(arrayOf<Any>(file, lines, calls.joinToString(", ")))
        val table = JBTable(model)
        table.isStriped = true
        return table
    }
}
