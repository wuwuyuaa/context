# Threadmap M3b — 节点详情面板 + PSI 跳源码 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M3a 的调用树工具窗里加:选中节点 → 右侧/下方详情面板(签名 / 位置 / 摘要 / 输入输出 / 副作用 / 证据表格 / 待查理由);双击或 Enter → 经 PSI 按 `signature` 反查方法跳到源码(抗行号漂移,退回 `file:line`)。

**Architecture:** 新增可测的 `SignatureParser`(签名 → FQCN/方法名/参数个数,供 PSI 查找)。新增 `NodeDetailPanel`(Swing,展示选中节点字段 + 证据 `JBTable`)。新增 `SourceNavigator`(用 `JavaPsiFacade` 按签名找 `PsiMethod` 跳转,退回 `OpenFileDescriptor(file,line)`)。`ThreadmapPanel` 改成 `OnePixelSplitter`(左树右详情),挂选中监听 + 双击/Enter 跳转。可测逻辑(签名解析)走 JUnit;UI/PSI 壳走 `runIde` 手验。

**Tech Stack:** Kotlin、IntelliJ Platform 2024.2(`JavaPsiFacade`/`PsiClass`/`PsiMethod`/`OpenFileDescriptor`/`OnePixelSplitter`/`JBTable`)、JUnit 5。

**承接(M3a 已就绪,不破坏):** `AnnotatedNode`(getSignature/getFile/getLine/getAnnotation/isCollapsed) / `Annotation`(summary/inputs/outputs/sideEffects/evidence/digWorthy/digReason) / `Evidence`(file/lines/calls) / `NodePresentation`(shortSignature 等) / `CallTreeTable.buildTreeTable` 返回 `TreeTableView` / `ThreadmapPanel`(SimpleToolWindowPanel,ActionToolbar + setContent)。设计见 [specs/2026-06-18-threadmap-m3-intellij-plugin-impl.md](../specs/2026-06-18-threadmap-m3-intellij-plugin-impl.md) §5。

**runIde 迭代提醒:** 改 Kotlin 代码后、`runIde` 前先 `./gradlew :threadmap-intellij:clean`(gradle 增量编译曾 stale)。

**M3b 后续:** M3c(进度持久化 + 待查清单 + 导出 todo + 过滤)。

---

## 文件结构

```
threadmap-intellij/src/main/kotlin/com/threadmap/intellij/
  model/SignatureParser.kt        # 创建:FQCN#method(P,..) → (fqcn, method, paramCount)。可测。
  ui/NodeDetailPanel.kt           # 创建:展示选中 AnnotatedNode 的详情 + 证据表格
  ui/SourceNavigator.kt           # 创建:PSI 按签名跳方法,退回 file:line
  ui/ThreadmapPanel.kt            # 改:OnePixelSplitter(树+详情) + 选中监听 + 双击/Enter 跳转
threadmap-intellij/src/test/kotlin/com/threadmap/intellij/
  model/SignatureParserTest.kt    # 创建
```

---

## Task 1: SignatureParser(可测:签名 → FQCN/方法名/参数个数)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/SignatureParser.kt`
- Test: `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/SignatureParserTest.kt`

纯逻辑(无 IntelliJ 依赖),供 `SourceNavigator` 用签名定位 PSI 方法。trace 签名形如 `com.ae.X#check(RedlineCheckContext)` / `A#m()` / `A#m(String, int)`。参数个数用于在重载里挑方法。

- [ ] **Step 1: 写失败测试**

`SignatureParserTest.kt`:
```kotlin
package com.threadmap.intellij.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SignatureParserTest {

    @Test
    fun parsesFqcnMethodAndParamCount() {
        val p = SignatureParser.parse("com.ae.negotiation.X#check(RedlineCheckContext)")!!
        assertEquals("com.ae.negotiation.X", p.fqcn)
        assertEquals("check", p.methodName)
        assertEquals(1, p.paramCount)
    }

    @Test
    fun parsesNoArgsAndMultiArgs() {
        assertEquals(0, SignatureParser.parse("A#m()")!!.paramCount)
        assertEquals(2, SignatureParser.parse("A#m(String, int)")!!.paramCount)
    }

    @Test
    fun nestedClassKeepsBinaryNameInFqcn() {
        val p = SignatureParser.parse("com.ae.Outer\$Inner#run()")!!
        assertEquals("com.ae.Outer\$Inner", p.fqcn)
        assertEquals("run", p.methodName)
    }

    @Test
    fun returnsNullOnMalformed() {
        assertNull(SignatureParser.parse("garbage"))
        assertNull(SignatureParser.parse("NoHashMethod"))
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-intellij:test --tests '*SignatureParserTest'`
Expected: 编译失败(`SignatureParser` 不存在)。

- [ ] **Step 3: 实现**

`SignatureParser.kt`:
```kotlin
package com.threadmap.intellij.model

/** 解析后的签名:全限定类名(内部类保留 $ 二进制名)、方法名、参数个数。 */
data class ParsedSignature(val fqcn: String, val methodName: String, val paramCount: Int)

/** 把 trace 签名 FQCN#method(简单参数类型, ...) 解析成结构(纯逻辑,供 PSI 查找)。 */
object SignatureParser {
    fun parse(signature: String): ParsedSignature? {
        val hash = signature.indexOf('#')
        if (hash < 0) return null
        val open = signature.indexOf('(', hash + 1)
        val close = signature.lastIndexOf(')')
        if (open < hash || close < open) return null
        val fqcn = signature.substring(0, hash)
        val method = signature.substring(hash + 1, open)
        val params = signature.substring(open + 1, close).trim()
        val paramCount = if (params.isEmpty()) 0 else params.split(",").size
        if (fqcn.isEmpty() || method.isEmpty()) return null
        return ParsedSignature(fqcn, method, paramCount)
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-intellij:test --tests '*SignatureParserTest'`
Expected: PASS(4 测试)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/SignatureParser.kt \
        threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/SignatureParserTest.kt
git commit -m "feat(intellij): add testable SignatureParser for PSI lookup" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: NodeDetailPanel(选中节点详情 + 证据表格)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/NodeDetailPanel.kt`

UI 壳,无法 JUnit 测,`buildPlugin` 编译验证 + `runIde` 手验。展示选中 `AnnotatedNode`:完整签名、位置 `file:line`、摘要、输入、输出、副作用、证据表格(文件 | 行号区间 | 关键被调方法)、待查理由(仅 `dig_worthy`)。未标注节点只显示签名 + 位置。

- [ ] **Step 1: 实现**

`NodeDetailPanel.kt`:
```kotlin
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
```
**API 注意:** `JBLabel`/`JBScrollPane`/`JBTable`/`JBUI.Borders`/`JBFont` 为平台稳定 API。编译不过时按当前平台调整,保持"签名用等宽、其余默认"的意图,报告改动。

- [ ] **Step 2: 编译验证**

Run: `./gradlew :threadmap-intellij:clean :threadmap-intellij:buildPlugin`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/NodeDetailPanel.kt
git commit -m "feat(intellij): add node detail panel with evidence table" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: SourceNavigator + ThreadmapPanel splitter 整合(选中联动 + 双击跳源码)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/SourceNavigator.kt`
- Modify: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt`

UI/PSI 壳,`buildPlugin` 编译验证 + `runIde` 手验。选中树节点 → 详情面板;双击/Enter → 按签名 PSI 跳方法,退回 `file:line`。

- [ ] **Step 1: 实现 SourceNavigator**

`SourceNavigator.kt`:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.intellij.model.SignatureParser

/** 按 trace 签名经 PSI 跳到方法;找不到则退回记录的 file:line。 */
object SourceNavigator {

    fun navigate(project: Project, node: AnnotatedNode) {
        if (navigateByPsi(project, node)) return
        navigateByFileLine(project, node)
    }

    private fun navigateByPsi(project: Project, node: AnnotatedNode): Boolean {
        val sig = SignatureParser.parse(node.signature) ?: return false
        val method = ReadAction.compute<com.intellij.psi.PsiMethod?, RuntimeException> {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(sig.fqcn.replace('$', '.'), GlobalSearchScope.allScope(project)) ?: return@compute null
            psiClass.findMethodsByName(sig.methodName, false)
                .firstOrNull { it.parameterList.parametersCount == sig.paramCount }
                ?: psiClass.findMethodsByName(sig.methodName, false).firstOrNull()
        } ?: return false
        if (!method.canNavigate()) return false
        method.navigate(true)
        return true
    }

    private fun navigateByFileLine(project: Project, node: AnnotatedNode) {
        val base = project.basePath ?: return
        // 优先在常见源码根下按 file 相对路径找;file 形如 com/ae/X.java
        val candidates = listOf("$base/${node.file}",
            "$base/app/src/main/java/${node.file}",
            "$base/src/main/java/${node.file}")
        val vFile = candidates.asSequence()
            .mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) }
            .firstOrNull() ?: return
        val line = if (node.line > 0) node.line - 1 else 0
        OpenFileDescriptor(project, vFile, line, 0).navigate(true)
    }
}
```
**API/线程注意:** PSI 读取放 `ReadAction.compute`,`navigate(true)` 在 EDT 调用(工具窗事件回调本就在 EDT)。`findClass` 用 `allScope`;内部类 `$` 转 `.` 给 PSI(PSI 用点号 qualified name)。若某 API 签名在 2024.2 不符,核对当前平台修正并报告。

- [ ] **Step 2: 改 ThreadmapPanel 为 splitter + 监听**

把 `ThreadmapPanel.render` 改成构建"左树右详情"的 `OnePixelSplitter`,并挂选中监听 + 双击/Enter 跳转。替换 `ThreadmapPanel.kt` 的 `render` 方法与相关引用:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dualView.TreeTableView
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTreeJsonReader
import com.threadmap.intellij.model.CallTreeNodeBuilder
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.tree.DefaultMutableTreeNode

class ThreadmapPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val detail = NodeDetailPanel()

    init {
        setContent(JScrollPane(JLabel("点击「加载 Trace」选择 annotated-tree.json,或把文件放到 项目根/.threadmap/annotated-tree.json")))
        val group = DefaultActionGroup()
        group.add(object : AnAction("加载 Trace", "选择 annotated-tree.json 加载", null) {
            override fun actionPerformed(e: AnActionEvent) { chooseAndLoad() }
        })
        group.add(object : AnAction("重新加载", "从 项目根/.threadmap/annotated-tree.json 重新加载", null) {
            override fun actionPerformed(e: AnActionEvent) { loadDefault() }
        })
        val actionToolbar = ActionManager.getInstance().createActionToolbar("ThreadmapToolbar", group, true)
        actionToolbar.targetComponent = this
        setToolbar(actionToolbar.component)
        loadDefault()
    }

    private fun chooseAndLoad() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        val file: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        render(Path.of(file.path))
    }

    private fun loadDefault() {
        val base = project.basePath ?: return
        val path = Path.of(base, ".threadmap", "annotated-tree.json")
        if (Files.isRegularFile(path)) render(path)
    }

    private fun render(path: Path) {
        val json = Files.readString(path)
        val tree = AnnotatedTreeJsonReader().read(json)
        val rootNode = CallTreeNodeBuilder.build(tree)
        val table = buildTreeTable(rootNode)
        wireSelection(table)
        wireNavigation(table)
        detail.showEmpty()
        val splitter = OnePixelSplitter(false, 0.55f)
        splitter.firstComponent = JBScrollPane(table)
        splitter.secondComponent = JBScrollPane(detail)
        setContent(splitter)
    }

    private fun selectedNode(table: TreeTableView): AnnotatedNode? {
        val row = table.selectedRow
        if (row < 0) return null
        val value = table.tree.getPathForRow(row)?.lastPathComponent
        return ((value as? DefaultMutableTreeNode)?.userObject) as? AnnotatedNode
    }

    private fun wireSelection(table: TreeTableView) {
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) selectedNode(table)?.let { n -> detail.show(n) }
        }
    }

    private fun wireNavigation(table: TreeTableView) {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) selectedNode(table)?.let { SourceNavigator.navigate(project, it) }
            }
        })
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) selectedNode(table)?.let { SourceNavigator.navigate(project, it) }
            }
        })
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :threadmap-intellij:clean :threadmap-intellij:buildPlugin`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 全量测试(逻辑没回归)**

Run: `./gradlew :threadmap-intellij:test`
Expected: 全绿(M3a 的 model 测试 + Task 1 的 SignatureParser 测试)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/SourceNavigator.kt \
        threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt
git commit -m "feat(intellij): split tree+detail, PSI signature navigation on double-click" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准(M3b Done)

- `./gradlew :threadmap-intellij:test` 全绿(含 SignatureParser)。
- `./gradlew :threadmap-intellij:buildPlugin` 成功。
- `runIde` 手验(加载 AECLAW `annotated-tree.json`):选中树节点 → 右侧详情面板显示签名/位置/摘要/输入输出/副作用/证据表格/(待查理由);双击或 Enter `RedlineDomainService#check` → 跳到 AECLAW 源码对应方法(PSI 命中);对一个 trace 里 `line=0` 的节点验证退回 file 头不报错。

## 后续(M3c)

- 掌握状态选择器 → `.threadmap/progress.json`(按 signature)+ 待查清单面板 + 导出 `todo.md` + 工具栏过滤。
- 打磨:列默认宽度、详情面板字段排版、PSI 未命中时的非阻塞提示气泡。
