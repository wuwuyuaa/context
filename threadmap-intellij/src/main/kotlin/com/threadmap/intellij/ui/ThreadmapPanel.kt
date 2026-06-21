package com.threadmap.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.jcef.JBCefApp
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
import com.threadmap.core.annotate.AnnotatedTreeJsonWriter
import com.threadmap.core.annotate.AnnotationPipeline
import com.threadmap.core.annotate.AnnotationRequest
import com.threadmap.core.annotate.AnnotationRequestBuilder
import com.threadmap.core.annotate.CachingAnnotator
import com.threadmap.core.annotate.OpenAiCompatibleChat
import com.threadmap.intellij.settings.ThreadmapConfigurable
import com.threadmap.intellij.settings.ThreadmapSettings
import com.intellij.openapi.options.ShowSettingsUtil
import com.threadmap.core.annotate.PackageFolder
import com.threadmap.core.annotate.QwenAnnotator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.threadmap.intellij.model.CallGraph
import com.threadmap.intellij.model.CallGraphBuilder
import com.threadmap.intellij.model.CallTreeNodeBuilder
import com.threadmap.intellij.model.ProgressStore
import com.threadmap.intellij.model.TodoExporter
import com.threadmap.intellij.model.TreeFilter
import com.threadmap.intellij.model.UnderstandingFilter
import com.threadmap.intellij.psi.EntryPoint
import com.threadmap.intellij.psi.EntryPointScanner
import com.threadmap.intellij.psi.StaticChain
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiMethod
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.threadmap.core.trace.TraceNode
import javax.swing.JList
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
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
    private var spineFilterOn = false
    private val search = SearchTextField(false).apply {
        textEditor.emptyText.text = "过滤方法 / 类名"
        preferredSize = Dimension(JBUI.scale(190), preferredSize.height)
    }

    private var currentTree: AnnotatedTree? = null
    private var currentTable: TreeTableView? = null
    private var currentRoot: DefaultMutableTreeNode? = null
    private var currentSplitter: OnePixelSplitter? = null
    private var selectedSignature: String? = null
    private var suppressFilterEvents = false
    private var currentGraph: CallGraph? = null
    private var graphMode = false
    private val leftCards = JPanel(CardLayout())
    private val graphPanel: GraphPanel? =
        if (JBCefApp.isSupported()) GraphPanel(project).also { gp ->
            gp.onSelect = { sig -> selectBySignature(sig) }
            gp.onOpen = { sig -> openBySignature(sig) }
        } else null

    init {
        setContent(emptyState(""))
        setToolbar(buildToolbar())
        wireFilters()
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = applyResponsiveLayout()
        })
        showEntryList()
    }

    /** 宽时左右排(树左详情右)、窄时上下排(树上详情下),随工具窗宽度自适应。 */
    private fun applyResponsiveLayout() {
        val w = width
        if (w <= 0) return
        val narrow = w < JBUI.scale(720)
        currentSplitter?.let {
            it.orientation = narrow
            it.proportion = if (narrow) 0.55f else 0.68f
        }
        currentTable?.let { applyTreeColumnLayout(it, narrow) }
    }

    /** 入口清单(正门):列出项目的 HTTP 端点,点一个走现有静态链路渲染进同一工具窗。右键是另一道门。 */
    private fun showEntryList() {
        setContent(emptyState("正在扫描 HTTP 入口…"))
        // 索引搜索是慢操作,放后台读 action;inSmartMode 自动等索引建完(替代之前的 dumb 兜底)。
        ReadAction.nonBlocking(java.util.concurrent.Callable { EntryPointScanner.scan(project) })
            .inSmartMode(project)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { all ->
                renderEntryList(all)
            }
            .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
    }

    private fun renderEntryList(all: List<EntryPoint>) {
        if (all.isEmpty()) {
            setContent(emptyState("没扫到 HTTP 入口(@RestController / @RequestMapping)。也可以直接在方法上右键「看这条链」。"))
            return
        }
        val list = JBList<EntryPoint>().apply {
            cellRenderer = EntryPointRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) selectedValue?.let { renderChainFrom(it.method) }
                }
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) selectedValue?.let { renderChainFrom(it.method) }
                }
            })
        }
        fun applyEntryFilter(query: String) {
            val q = query.trim().lowercase()
            val items = if (q.isEmpty()) all
                else all.filter { "${it.verb} ${it.path} ${it.signature}".lowercase().contains(q) }
            list.setListData(items.toTypedArray())
            if (items.isNotEmpty()) list.selectedIndex = 0
        }
        val search = SearchTextField(false).apply {
            textEditor.emptyText.text = "过滤端点(路径 / Controller / 动词)"
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = applyEntryFilter(text)
            })
        }
        applyEntryFilter("")
        val header = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(8, 10)
            add(JBLabel("选一个入口看它的链路(双击 / 回车)— 共 ${all.size} 个"), BorderLayout.NORTH)
            add(search, BorderLayout.CENTER)
        }
        setContent(JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(JBScrollPane(list).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        })
    }

    private fun renderChainFrom(method: PsiMethod) {
        setContent(emptyState("正在走链…"))
        // PSI 走链 + 接口解析是慢操作,放后台读 action,算完回 EDT 渲染。
        ReadAction.nonBlocking(java.util.concurrent.Callable {
            val root = StaticChain.walk(method)
            StaticChain.writeStaticTrace(project, root)
            StaticChain.toAnnotatedTree(root)
        }).inSmartMode(project)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { tree ->
                renderStaticTree(tree)
            }
            .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
    }

    private class EntryPointRenderer : ColoredListCellRenderer<EntryPoint>() {
        override fun customizeCellRenderer(
            list: JList<out EntryPoint>,
            value: EntryPoint,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            val verbColor = when (value.verb) {
                "GET" -> JBColor(0x2E7D32, 0x6FBF73)
                "POST" -> JBColor(0xA66A00, 0xE5B454)
                "PUT", "PATCH" -> JBColor(0x1565C0, 0x6FA8DC)
                "DELETE" -> JBColor(0xB3261E, 0xF07167)
                else -> JBColor.GRAY
            }
            append(value.verb.padEnd(7), SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, verbColor))
            append(value.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("     " + value.signature, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            border = JBUI.Borders.empty(3, 10)
        }
    }

    private fun buildToolbar(): JPanel {
        val group = DefaultActionGroup().apply {
            // 高频留前面:入口(正门)/刷新/图视图;低频收进「⋮ 更多」,给中间筛选腾地方。
            add(toolbarAction("入口", "列出项目的 HTTP 入口,点一个看它的链路", AllIcons.Actions.ListFiles) {
                showEntryList()
            })
            add(toolbarAction("刷新", "重新加载当前项目的调用树", AllIcons.Actions.Refresh) {
                loadDefault()
            })
            add(toolbarAction("标注主干", "用 AI 标注当前链的主干(里程碑+祖先),省 token", AllIcons.Actions.Lightning) {
                annotateChain(true)
            })
            if (graphPanel != null) {
                addSeparator()
                add(object : ToggleAction("图视图", "在树与调用图之间切换", AllIcons.Actions.GroupBy) {
                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                    override fun displayTextInToolbar(): Boolean = true
                    override fun isSelected(e: AnActionEvent): Boolean = graphMode
                    override fun setSelected(e: AnActionEvent, state: Boolean) { setGraphMode(state) }
                })
            }
            add(object : ToggleAction("只看主干", "只显示有副作用的主干(落库 / 调外部 / 发消息)及其路径,枝节折起", AllIcons.General.Filter) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                override fun displayTextInToolbar(): Boolean = true
                override fun isSelected(e: AnActionEvent): Boolean = spineFilterOn
                override fun setSelected(e: AnActionEvent, state: Boolean) { spineFilterOn = state; applyFilters() }
            })
            addSeparator()
            add(DefaultActionGroup("更多", true).apply {
                templatePresentation.icon = AllIcons.Actions.More
                add(toolbarAction("标注全链", "用 AI 标注当前链的全部节点(更全但更费 token)", AllIcons.Actions.Lightning) { annotateChain(false) })
                addSeparator()
                add(toolbarAction("加载", "选择 annotated-tree.json 加载", AllIcons.Actions.MenuOpen) { chooseAndLoad() })
                add(toolbarAction("展开", "展开调用树的所有节点", AllIcons.Actions.Expandall) { setAllExpanded(true) })
                add(toolbarAction("折叠", "折叠除入口外的所有节点", AllIcons.Actions.Collapseall) { setAllExpanded(false) })
                addSeparator()
                add(toolbarAction("选服务商 / API Key…", "选 LLM 服务商并填 key(标注用)", AllIcons.General.Settings) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, ThreadmapConfigurable::class.java)
                })
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
        spineOnly = spineFilterOn,
        query = search.text
    )

    private fun emptyState(message: String): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(28)
        val status = if (message.isBlank()) ""
            else "<span style='color:#8a8a8a'>$message</span><br><br>"
        add(JLabel(
            "<html><div style='text-align:center'>" +
                "<span style='font-size:15px'><b>看清一个方法到底调用了什么</b></span><br><br>" +
                status +
                "<span style='font-size:13px'>👉 在任意方法名上 <b>右键 →「看这条链」</b></span><br>" +
                "<span style='color:#8a8a8a'>零配置,立刻画出它调用了哪些方法、哪些是数据库 / 外部接口</span><br><br>" +
                "<span style='color:#6a6a6a;font-size:11px'>已经有 annotated-tree.json?点上面「加载」打开它</span>" +
                "</div></html>",
            JLabel.CENTER
        ), BorderLayout.CENTER)
    }

    private fun filteredEmptyState(): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(24)
        add(JBLabel("没有符合当前筛选条件的节点", JLabel.CENTER).apply {
            foreground = UIUtil.getContextHelpForeground()
        }, BorderLayout.CENTER)
    }

    /** 树里是否已有任意 AI 标注;静态结构(未标注)时用来决定是否提示补标注。 */
    private fun hasAnnotations(tree: AnnotatedTree): Boolean {
        fun any(n: AnnotatedNode): Boolean = n.annotation != null || n.children.any { any(it) }
        return any(tree.root)
    }

    private fun wrapBanner(banner: JComponent?, body: JComponent): JComponent =
        if (banner == null) body
        else JPanel(BorderLayout()).apply {
            add(banner, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
        }

    /** 静态结构就绪、但还没 AI 标注时,顶部挂一条「怎么补标注」提示条。 */
    private fun buildAnnotateHint(): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(5))).apply {
            background = JBColor(0xE8F5E9, 0x2C3B2E)
            border = JBUI.Borders.empty(2, 8)
            add(JBLabel("✓ 结构已就绪 — 想要每步的 AI 摘要 / 风险标注?"))
            add(HyperlinkLabel("一键标注主干").apply { addHyperlinkListener { annotateChain(true) } })
        }

    /** 一键在插件内标注当前链(直连 DashScope、不依赖 langchain4j)。spineOnly=true 只标主干省 token。 */
    private fun annotateChain(spineOnly: Boolean) {
        val settings = ThreadmapSettings.getInstance()
        val baseUrl = settings.baseUrl
        val model = settings.model
        val key = settings.apiKey
        // 本地 Ollama 可不填 key;其余服务商需要 key。baseUrl/model 始终必填。
        val needsKey = !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1")
        if (baseUrl.isBlank() || model.isBlank() || (needsKey && key.isBlank())) {
            val choice = Messages.showOkCancelDialog(
                project,
                "还没配置 LLM 服务商 / API Key。\n现在去「设置 → Tools → 脉络 Threadmap」选服务商并填 key?",
                "脉络:需要配置 LLM",
                "去设置", "取消", null)
            if (choice == Messages.OK) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, ThreadmapConfigurable::class.java)
            }
            return
        }
        val base = project.basePath ?: return
        val tracePath = Path.of(base, ".threadmap", "static-trace.json")
        if (!Files.isRegularFile(tracePath)) {
            Messages.showWarningDialog(project, "还没有可标注的链路,请先看一条链。", "脉络:无链路")
            return
        }
        val basePackage = currentTree?.let { guessBasePackage(it.root.signature) } ?: ""
        val title = if (spineOnly) "脉络:标注主干中…" else "脉络:标注全链中…"
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                val traceJson = Files.readString(tracePath)
                // 严格模式:不传 FakeAnnotator 兜底。LLM 失败就抛真错→onThrowable,
                // 绝不用假摘要冒充成功(否则用户拿到一棵「看着标注成功」实则全假的树)。
                val annotator = CachingAnnotator(
                    QwenAnnotator(OpenAiCompatibleChat(baseUrl, key, model)))
                val pipeline = AnnotationPipeline(
                    PackageFolder(listOf(basePackage), 50),
                    annotator,
                    AnnotationRequestBuilder { node -> AnnotationRequest.ofSignature(node.signature) },
                    spineOnly)
                val tree = pipeline.run(traceJson)
                Files.writeString(
                    Path.of(base, ".threadmap", "annotated-tree.json"),
                    AnnotatedTreeJsonWriter().toJson(tree))
            }
            override fun onSuccess() = loadDefault()
            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(
                    project,
                    "AI 标注失败,未写入任何标注(已有的旧标注保持不变)。\n\n" +
                        "原因:${error.message}\n\n" +
                        "请检查:服务商地址 / 模型名 / API Key 是否正确、网络是否可达;" +
                        "在 ⋮ → 选服务商 / API Key… 改好后,重新点「标注主干」即可重试。",
                    "脉络:标注失败")
            }
        })
    }

    private fun guessBasePackage(signature: String): String {
        val fqn = signature.substringBefore('#')
        val parts = fqn.split('.')
        return if (parts.size >= 3) parts.take(3).joinToString(".") else fqn.substringBeforeLast('.')
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
            setContent(emptyState(""))
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

    /** 由静态 walker 等外部来源直接渲染一棵已构建的 AnnotatedTree(无需读盘）。 */
    fun renderStaticTree(tree: AnnotatedTree) {
        currentTree = tree
        selectedSignature = tree.root.signature
        showTree(tree, selectedSignature)
    }

    private fun showTree(tree: AnnotatedTree, preferredSignature: String?) {
        val filter = activeFilter()
        val rootNode = CallTreeNodeBuilder.build(tree, filter)
        val banner = if (hasAnnotations(tree)) null else buildAnnotateHint()
        val tabs = JTabbedPane()
        val todoItems = TodoExporter.collect(tree)
        val todoPanel = TodoPanel(
            onOpen = { signature -> revealSignature(tree, signature) },
            onExport = { exportTodo(tree) }
        ).apply { show(todoItems) }
        tabs.addTab("详情", detail)
        tabs.addTab("待查清单 (${todoItems.size})", todoPanel)

        // 初值给左右排;实际朝向由 applyResponsiveLayout 按工具窗宽度切换(宽=左右,窄=上下)
        val splitter = OnePixelSplitter(false, 0.68f)
        currentSplitter = splitter
        if (rootNode == null) {
            currentTable = null
            currentRoot = null
            detail.showEmpty()
            splitter.firstComponent = filteredEmptyState()
            splitter.secondComponent = tabs
            setContent(wrapBanner(banner, splitter))
            applyResponsiveLayout()
            return
        }

        val table = buildTreeTable(rootNode)
        currentTable = table
        currentRoot = rootNode
        wireSelection(table, tree)
        wireNavigation(table)
        detail.showEmpty()

        val treeScroll = JBScrollPane(table).apply { border = JBUI.Borders.empty() }
        leftCards.removeAll()
        leftCards.add(treeScroll, "tree")
        graphPanel?.let { leftCards.add(it, "graph") }
        currentGraph = CallGraphBuilder.build(tree)
        graphPanel?.takeIf { graphMode }?.render(currentGraph!!)
        showLeftCard()
        splitter.firstComponent = leftCards
        splitter.secondComponent = tabs
        setContent(wrapBanner(banner, splitter))
        applyResponsiveLayout()

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

    private fun setGraphMode(on: Boolean) {
        graphMode = on
        if (on) currentGraph?.let { graphPanel?.render(it) }
        showLeftCard()
    }

    private fun showLeftCard() {
        (leftCards.layout as CardLayout).show(leftCards, if (graphMode && graphPanel != null) "graph" else "tree")
    }

    private fun selectBySignature(signature: String) {
        val tree = currentTree ?: return
        val node = findBySignature(tree.root, signature) ?: return
        selectedSignature = signature
        showNodeDetails(currentTable ?: return, tree, node)
    }

    private fun openBySignature(signature: String) {
        val tree = currentTree ?: return
        val node = findBySignature(tree.root, signature) ?: return
        SourceNavigator.navigate(project, node)
    }

    private fun findBySignature(node: AnnotatedNode, signature: String): AnnotatedNode? {
        if (node.signature == signature) return node
        for (child in node.children) {
            findBySignature(child, signature)?.let { return it }
        }
        return null
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
