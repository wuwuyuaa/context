package com.threadmap.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.threadmap.intellij.model.TodoItem
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

/** 当前调用树的待查节点清单；双击/Enter 回到树节点。 */
class TodoPanel(
    private val onOpen: (String) -> Unit,
    private val onExport: () -> Unit
) : JPanel(BorderLayout()) {
    private val model = object : DefaultTableModel(arrayOf<Any>("方法签名", "待查理由"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(model)
    private val tableScroll = JBScrollPane(table)
    private val empty = JLabel("当前链路没有待查节点", SwingConstants.CENTER).apply {
        foreground = UIUtil.getContextHelpForeground()
    }
    private var items: List<TodoItem> = emptyList()

    init {
        border = JBUI.Borders.empty(10, 12)
        table.rowHeight = JBUI.scale(30)
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.setShowGrid(false)
        table.tableHeader.reorderingAllowed = false
        table.columnModel.getColumn(0).preferredWidth = JBUI.scale(220)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(320)
        wireOpen()

        val actions = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(8)
            add(JPanel().apply {
                isOpaque = false
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(JLabel("待查清单").apply { font = JBFont.label().asBold() })
                add(JLabel("双击条目可回到调用树中的对应方法").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = JBFont.small()
                })
            }, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(JButton("导出 todo.md", AllIcons.Actions.Download).apply {
                    isFocusable = false
                    addActionListener { onExport() }
                })
            }, BorderLayout.EAST)
        }
        add(actions, BorderLayout.NORTH)
        add(tableScroll, BorderLayout.CENTER)
    }

    fun show(items: List<TodoItem>) {
        this.items = items
        model.rowCount = 0
        items.forEach { model.addRow(arrayOf(compactSignature(it.signature), it.reason)) }
        if (items.isEmpty()) {
            remove(tableScroll)
            if (empty.parent == null) add(empty, BorderLayout.CENTER)
        } else {
            remove(empty)
            if (tableScroll.parent == null) add(tableScroll, BorderLayout.CENTER)
        }
        revalidate()
        repaint()
    }

    private fun openSelected() {
        val row = table.selectedRow
        if (row < 0 || row >= items.size) return
        onOpen(items[row].signature)
    }

    private fun wireOpen() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) openSelected()
            }
        })
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openSelected()
            }
        })
    }

    private fun compactSignature(signature: String): String {
        val hash = signature.indexOf('#')
        if (hash < 0) return signature
        return signature.substring(0, hash).substringAfterLast('.') +
            signature.substring(hash).substringBefore('(')
    }
}
