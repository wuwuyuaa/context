package com.threadmap.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dualView.TreeTableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.AnnotatedTreeJsonReader
import com.threadmap.intellij.model.CallTreeNodeBuilder
import com.threadmap.intellij.model.ProgressStore
import com.threadmap.intellij.model.TodoExporter
import com.threadmap.intellij.model.TreeFilter
import com.threadmap.intellij.model.UnderstandingFilter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ThreadmapPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val detail = NodeDetailPanel()
    private val statusFilter = ComboBox(UnderstandingFilter.entries.toTypedArray()).apply {
        selectedItem = UnderstandingFilter.ALL
        setMinimumAndPreferredWidth(JBUI.scale(108))
        toolTipText = "按掌握状态过滤"
    }
    private val todoOnly = JBCheckBox("只看待查项")
    private val search = SearchTextField(false).apply {
        textEditor.emptyText.text = "过滤方法 / 类名"
        preferredSize = Dimension(JBUI.scale(190), preferredSize.height)
    }

    private var currentTree: AnnotatedTree? = null
    private var currentTable: TreeTableView? = null
    private var currentRoot: DefaultMutableTreeNode? = null
    private var selectedSignature: String? = null
    private var suppressFilterEvents = false

    init {
        setContent(emptyState("加载 annotated-tree.json 后查看真实调用链路"))
        setToolbar(buildToolbar())
        wireFilters()
        loadDefault()
    }

    private fun buildToolbar(): JPanel {
        val group = DefaultActionGroup().apply {
            add(toolbarAction("加载", "选择 annotated-tree.json 加载", AllIcons.Actions.MenuOpen) {
                chooseAndLoad()
            })
            add(toolbarAction("刷新", "重新加载当前项目的调用树", AllIcons.Actions.Refresh) {
                loadDefault()
            })
            addSeparator()
            add(toolbarAction("展开", "展开调用树的所有节点", AllIcons.Actions.Expandall) {
                setAllExpanded(true)
            })
            add(toolbarAction("折叠", "折叠除入口外的所有节点", AllIcons.Actions.Collapseall) {
                setAllExpanded(false)
            })
        }
        val actions = ActionManager.getInstance()
            .createActionToolbar("ThreadmapToolbar", group, true).apply {
                targetComponent = this@ThreadmapPanel
            }

        val filters = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(JBLabel("状态"))
            add(statusFilter)
            add(todoOnly)
            add(search)
        }

        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.empty(3, 5)
            add(actions.component, BorderLayout.WEST)
            add(filters, BorderLayout.CENTER)
        }
    }

    private fun toolbarAction(
        text: String,
        description: String,
        icon: Icon,
        action: () -> Unit
    ): AnAction = object : AnAction(text, description, icon) {
        override fun actionPerformed(e: AnActionEvent) = action()
        override fun displayTextInToolbar(): Boolean = true
    }

    private fun wireFilters() {
        statusFilter.addActionListener { applyFilters() }
        todoOnly.addActionListener { applyFilters() }
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = applyFilters()
        })
    }

    private fun applyFilters() {
        if (suppressFilterEvents) return
        currentTree?.let { showTree(it, selectedSignature) }
    }

    private fun activeFilter(): TreeFilter = TreeFilter(
        understanding = statusFilter.selectedItem as? UnderstandingFilter
            ?: UnderstandingFilter.ALL,
        todoOnly = todoOnly.isSelected,
        query = search.text
    )

    private fun emptyState(message: String): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(24)
        add(JLabel(
            "<html><div style='text-align:center'><b>$message</b><br><br>" +
                "<span style='color:#8a8a8a'>默认路径：项目根/.threadmap/annotated-tree.json</span></div></html>",
            JLabel.CENTER
        ), BorderLayout.CENTER)
    }

    private fun filteredEmptyState(): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(24)
        add(JBLabel("没有符合当前筛选条件的节点", JLabel.CENTER).apply {
            foreground = UIUtil.getContextHelpForeground()
        }, BorderLayout.CENTER)
    }

    private fun chooseAndLoad() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        val file: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        render(Path.of(file.path))
    }

    private fun loadDefault() {
        val base = project.basePath ?: return
        val path = Path.of(base, ".threadmap", "annotated-tree.json")
        if (Files.isRegularFile(path)) {
            render(path)
        } else {
            setContent(emptyState("尚未找到调用树"))
        }
    }

    private fun render(path: Path) {
        try {
            val tree = AnnotatedTreeJsonReader().read(Files.readString(path))
            progressStore()?.let { store ->
                try {
                    store.merge(tree)
                } catch (e: Exception) {
                    Messages.showWarningDialog(
                        project,
                        "无法读取 .threadmap/progress.json，调用树仍会加载。\n${e.message}",
                        "脉络进度读取失败"
                    )
                }
            }
            currentTree = tree
            selectedSignature = tree.root.signature
            showTree(tree, selectedSignature)
        } catch (e: Exception) {
            currentTree = null
            currentTable = null
            currentRoot = null
            setContent(emptyState("无法加载调用树：${e.message ?: "未知错误"}"))
            Messages.showErrorDialog(project, "无法加载 $path。\n${e.message}", "脉络加载失败")
        }
    }

    private fun showTree(tree: AnnotatedTree, preferredSignature: String?) {
        val filter = activeFilter()
        val rootNode = CallTreeNodeBuilder.build(tree, filter)
        val tabs = JTabbedPane()
        val todoItems = TodoExporter.collect(tree)
        val todoPanel = TodoPanel(
            onOpen = { signature -> revealSignature(tree, signature) },
            onExport = { exportTodo(tree) }
        ).apply { show(todoItems) }
        tabs.addTab("详情", detail)
        tabs.addTab("待查清单 (${todoItems.size})", todoPanel)

        val splitter = OnePixelSplitter(false, 0.61f)
        if (rootNode == null) {
            currentTable = null
            currentRoot = null
            detail.showEmpty()
            splitter.firstComponent = filteredEmptyState()
            splitter.secondComponent = tabs
            setContent(splitter)
            return
        }

        val table = buildTreeTable(rootNode)
        currentTable = table
        currentRoot = rootNode
        wireSelection(table, tree)
        wireNavigation(table)
        detail.showEmpty()

        splitter.firstComponent = JBScrollPane(table).apply {
            border = JBUI.Borders.empty()
        }
        splitter.secondComponent = tabs
        setContent(splitter)

        val rootPath = TreePath(rootNode.path)
        table.tree.expandPath(rootPath)
        if (!filter.isEmpty()) {
            setExpanded(table.tree, rootPath, true)
        }
        if (preferredSignature == null || !selectSignature(table, rootNode, preferredSignature)) {
            table.tree.selectionPath = rootPath
            table.tree.scrollPathToVisible(rootPath)
        }
    }

    private fun revealSignature(tree: AnnotatedTree, signature: String) {
        suppressFilterEvents = true
        statusFilter.selectedItem = UnderstandingFilter.ALL
        todoOnly.isSelected = false
        search.text = ""
        suppressFilterEvents = false
        selectedSignature = signature
        showTree(tree, signature)
    }

    private fun selectedNode(table: TreeTableView): AnnotatedNode? {
        val row = table.selectedRow
        if (row < 0) return null
        val value = table.tree.getPathForRow(row)?.lastPathComponent
        return ((value as? DefaultMutableTreeNode)?.userObject) as? AnnotatedNode
    }

    private fun wireSelection(table: TreeTableView, tree: AnnotatedTree) {
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                selectedNode(table)?.let { node ->
                    selectedSignature = node.signature
                    showNodeDetails(table, tree, node)
                }
            }
        }
    }

    private fun showNodeDetails(table: TreeTableView, tree: AnnotatedTree, node: AnnotatedNode) {
        detail.show(
            node = node,
            onUnderstandingChanged = { state ->
                val store = progressStore()
                if (store != null) {
                    try {
                        store.update(tree, node.signature, state)
                        if (activeFilter().understanding == UnderstandingFilter.ALL) {
                            table.repaint()
                        } else {
                            showTree(tree, node.signature)
                        }
                    } catch (e: Exception) {
                        Messages.showErrorDialog(
                            project,
                            "无法写入 .threadmap/progress.json。\n${e.message}",
                            "脉络进度保存失败"
                        )
                        showNodeDetails(table, tree, node)
                    }
                }
            },
            onNavigate = { SourceNavigator.navigate(project, node) }
        )
    }

    private fun progressStore(): ProgressStore? {
        val base = project.basePath ?: return null
        return ProgressStore(Path.of(base, ".threadmap", "progress.json"))
    }

    private fun exportTodo(tree: AnnotatedTree) {
        val base = project.basePath ?: return
        val output = Path.of(base, ".threadmap", "todo.md")
        try {
            TodoExporter.write(tree, output)
            Messages.showInfoMessage(project, "已导出到 $output", "脉络待查清单")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "无法导出 todo.md。\n${e.message}", "脉络导出失败")
        }
    }

    private fun selectSignature(
        table: TreeTableView,
        root: DefaultMutableTreeNode,
        signature: String
    ): Boolean {
        val target = root.depthFirstEnumeration().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .firstOrNull { (it.userObject as? AnnotatedNode)?.signature == signature }
            ?: return false
        val path = TreePath(target.path)
        table.tree.expandPath(path.parentPath)
        table.tree.selectionPath = path
        table.tree.scrollPathToVisible(path)
        return true
    }

    private fun setAllExpanded(expand: Boolean) {
        val table = currentTable ?: return
        val root = currentRoot ?: return
        setExpanded(table.tree, TreePath(root.path), expand)
        if (!expand) table.tree.expandPath(TreePath(root.path))
    }

    private fun setExpanded(tree: JTree, path: TreePath, expand: Boolean) {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        node.children().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .forEach { child -> setExpanded(tree, path.pathByAddingChild(child), expand) }
        if (expand) tree.expandPath(path) else tree.collapsePath(path)
    }

    private fun wireNavigation(table: TreeTableView) {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    selectedNode(table)?.let { SourceNavigator.navigate(project, it) }
                }
            }

            override fun mousePressed(e: MouseEvent) = showNavigationMenu(e)
            override fun mouseReleased(e: MouseEvent) = showNavigationMenu(e)

            private fun showNavigationMenu(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = table.rowAtPoint(e.point)
                if (row >= 0) table.setRowSelectionInterval(row, row)
                val node = selectedNode(table) ?: return
                JPopupMenu().apply {
                    add(JMenuItem("跳转到源码", AllIcons.Actions.EditSource).apply {
                        addActionListener { SourceNavigator.navigate(project, node) }
                    })
                    show(table, e.x, e.y)
                }
            }
        })
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    selectedNode(table)?.let { SourceNavigator.navigate(project, it) }
                }
            }
        })
    }
}
