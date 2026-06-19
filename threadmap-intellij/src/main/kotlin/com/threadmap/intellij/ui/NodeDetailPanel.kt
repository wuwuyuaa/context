package com.threadmap.intellij.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.Understanding
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

/** 选中节点的详情视图：强调摘要、掌握状态、证据和待查理由。 */
class NodeDetailPanel : JPanel(BorderLayout()) {
    private val body = object : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int = JBUI.scale(18)

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int
        ): Int = visibleRect.height.coerceAtLeast(JBUI.scale(80))

        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
    }.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12, 14)
    }
    private val scrollPane = JBScrollPane(body).apply {
        border = JBUI.Borders.empty()
    }

    init {
        border = JBUI.Borders.empty()
        add(scrollPane, BorderLayout.CENTER)
        showEmpty()
    }

    fun showEmpty() {
        body.removeAll()
        body.add(Box.createVerticalStrut(JBUI.scale(24)))
        body.add(JBLabel("选择左侧方法查看详情", SwingConstants.CENTER).apply {
            foreground = UIUtil.getContextHelpForeground()
            alignmentX = CENTER_ALIGNMENT
        })
        refresh()
    }

    fun show(
        node: AnnotatedNode,
        onUnderstandingChanged: (Understanding) -> Unit = {},
        onNavigate: () -> Unit = {}
    ) {
        body.removeAll()
        body.add(header(node, onUnderstandingChanged, onNavigate))

        val annotation = node.annotation
        if (annotation == null) {
            body.add(sectionTitle("标注"))
            body.add(wrappedText("该节点已折叠或尚未生成 AI 标注。", muted = true))
            refresh()
            return
        }

        body.add(sectionTitle("AI 摘要"))
        body.add(wrappedText(annotation.summary(), emphasized = true))

        body.add(sectionTitle("输入 / 输出"))
        body.add(JPanel(GridLayout(1, 2, JBUI.scale(8), 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            add(valueCard("输入", annotation.inputs()))
            add(valueCard("输出", annotation.outputs()))
        })

        body.add(sectionTitle("副作用"))
        body.add(sideEffectTags(annotation.sideEffects()))

        body.add(sectionTitle("证据"))
        body.add(evidenceTable(
            annotation.evidence().file(),
            annotation.evidence().lines(),
            annotation.evidence().calls()
        ))

        if (annotation.digWorthy()) {
            body.add(sectionTitle("待查理由"))
            body.add(warningCard(annotation.digReason() ?: "值得进一步核查"))
        }
        body.add(Box.createVerticalGlue())
        refresh()
    }

    private fun header(
        node: AnnotatedNode,
        onUnderstandingChanged: (Understanding) -> Unit,
        onNavigate: () -> Unit
    ): JPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(6))).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.emptyBottom(4)

        add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel("完整签名").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.small()
                alignmentX = LEFT_ALIGNMENT
            })
            add(JBTextArea(node.signature).apply {
                isEditable = false
                isFocusable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = false
                border = JBUI.Borders.empty()
                font = JBFont.create(Font(Font.MONOSPACED, Font.BOLD, font.size + 1))
                toolTipText = node.signature
                caretPosition = 0
                minimumSize = Dimension(0, preferredSize.height)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height * 4)
                alignmentX = LEFT_ALIGNMENT
            })
            add(JButton(locationText(node)).apply {
                isBorderPainted = false
                isContentAreaFilled = false
                isFocusPainted = false
                horizontalAlignment = SwingConstants.LEFT
                foreground = JBColor.namedColor("Link.activeForeground", JBColor.BLUE)
                border = JBUI.Borders.empty(3, 0, 0, 0)
                toolTipText = "在源码中打开"
                addActionListener { onNavigate() }
                alignmentX = LEFT_ALIGNMENT
            })
            add(understandingSelector(node.understanding, onUnderstandingChanged).apply {
                alignmentX = LEFT_ALIGNMENT
            })
        }, BorderLayout.CENTER)
    }

    private fun understandingSelector(
        current: Understanding,
        onChanged: (Understanding) -> Unit
    ): JPanel {
        val combo = JComboBox(Understanding.entries.toTypedArray()).apply {
            selectedItem = current
            renderer = UnderstandingRenderer()
            addActionListener { (selectedItem as? Understanding)?.let(onChanged) }
        }
        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(5))).apply {
            isOpaque = false
            add(JBLabel("掌握状态").apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.small()
            })
            add(combo)
        }
    }

    private fun sectionTitle(title: String): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(14, 0, 6, 0)
        add(JBLabel(title).apply { font = JBFont.label().asBold() }, BorderLayout.WEST)
        add(JPanel().apply {
            background = JBColor.border()
            preferredSize = Dimension(1, JBUI.scale(1))
        }, BorderLayout.SOUTH)
    }

    private fun valueCard(label: String, value: String): JPanel =
        cardPanel().apply {
            layout = BorderLayout(0, JBUI.scale(4))
            add(JBLabel(label).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = JBFont.small()
            }, BorderLayout.NORTH)
            add(wrappedText(value.ifBlank { "—" }), BorderLayout.CENTER)
        }

    private fun wrappedText(
        value: String,
        emphasized: Boolean = false,
        muted: Boolean = false
    ): JBTextArea = JBTextArea(value.ifBlank { "—" }).apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty()
        if (emphasized) font = JBFont.label().asBold()
        if (muted) foreground = UIUtil.getContextHelpForeground()
        alignmentX = LEFT_ALIGNMENT
    }

    private fun sideEffectTags(values: List<String>): JPanel =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            if (values.isEmpty()) {
                add(JBLabel("—").apply { foreground = UIUtil.getContextHelpForeground() })
            } else {
                values.forEach { value ->
                    add(JBLabel(value).apply {
                        isOpaque = true
                        background = sideEffectColor(value)
                        border = BorderFactory.createCompoundBorder(
                            JBUI.Borders.customLine(sideEffectBorder(value), 1),
                            JBUI.Borders.empty(2, 7)
                        )
                    })
                }
            }
        }

    private fun evidenceTable(file: String, lines: String, calls: List<String>): JBScrollPane {
        val model = object : DefaultTableModel(arrayOf<Any>("文件", "行号", "关键被调方法"), 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        if (calls.isEmpty()) {
            model.addRow(arrayOf(file, lines, "—"))
        } else {
            calls.forEachIndexed { index, call ->
                model.addRow(arrayOf(
                    if (index == 0) file.substringAfterLast('/') else "",
                    if (index == 0) lines else "",
                    call
                ))
            }
        }
        val table = JBTable(model).apply {
            rowHeight = JBUI.scale(26)
            setShowGrid(false)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = JBUI.scale(150)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(70)
            columnModel.getColumn(2).preferredWidth = JBUI.scale(180)
            toolTipText = file
        }
        return JBScrollPane(table).apply {
            alignmentX = LEFT_ALIGNMENT
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale((calls.size.coerceAtLeast(1) * 26) + 28))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(150))
        }
    }

    private fun warningCard(reason: String): JPanel =
        JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = true
            background = JBColor(Color(0xFFF7E6), Color(0x3B3321))
            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor(Color(0xE8B85B), Color(0x7A6235)), 1),
                JBUI.Borders.empty(8, 10)
            )
            alignmentX = LEFT_ALIGNMENT
            add(JBLabel("★").apply { foreground = JBColor(Color(0xA66A00), Color(0xE5B454)) }, BorderLayout.WEST)
            add(wrappedText(reason), BorderLayout.CENTER)
        }

    private fun cardPanel(): JPanel = JPanel().apply {
        isOpaque = true
        background = JBColor.namedColor("EditorPane.background", UIUtil.getPanelBackground())
        border = BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8, 10)
        )
    }

    private fun locationText(node: AnnotatedNode): String =
        if (node.line > 0) "${node.file.substringAfterLast('/')}:${node.line}  ↗"
        else "${node.file.substringAfterLast('/')}  ↗"

    private fun sideEffectColor(value: String): Color = SideEffectStyle.background(value)

    private fun sideEffectBorder(value: String): Color = SideEffectStyle.border(value)

    private fun refresh() {
        body.revalidate()
        body.repaint()
        SwingUtilities.invokeLater {
            scrollPane.viewport.viewPosition = Point(0, 0)
        }
    }

    private class UnderstandingRenderer : JBLabel(), ListCellRenderer<Understanding> {
        init {
            isOpaque = true
            border = JBUI.Borders.empty(2, 6)
        }

        override fun getListCellRendererComponent(
            list: JList<out Understanding>,
            value: Understanding?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val state = value ?: Understanding.UNKNOWN
            text = "●  " + when (state) {
                Understanding.UNKNOWN -> "未知"
                Understanding.HALF -> "半懂"
                Understanding.MASTERED -> "已掌握"
                Understanding.RISKY -> "高风险"
            }
            foreground = if (isSelected) list.selectionForeground else statusColor(state)
            background = if (isSelected) list.selectionBackground else list.background
            return this
        }

        private fun statusColor(state: Understanding): Color = when (state) {
            Understanding.UNKNOWN -> JBColor.GRAY
            Understanding.HALF -> JBColor(Color(0xA66A00), Color(0xE5B454))
            Understanding.MASTERED -> JBColor(Color(0x2E7D32), Color(0x6FBF73))
            Understanding.RISKY -> JBColor(Color(0xB3261E), Color(0xF07167))
        }
    }
}
