# 调用图视图(Call Graph View)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给脉络插件加一个方法级"调用图"视图(JCEF + Cytoscape.js),与现有树视图在左侧切换,同签名去重、重复调用标 ×N,节点带状态色/摘要/副作用药丸,点节点联动详情、双击跳源码。

**Architecture:** 纯逻辑 `CallGraphBuilder`(AnnotatedTree→去重节点+计数边)→ `CallGraphJson` 序列化 → `GraphPanel`(`JBCefBrowser` 内嵌 Chromium,内联打包的 Cytoscape JS 渲染)→ `ThreadmapPanel` 用 `CardLayout` 在树/图间切换,`JBCefJSQuery` 把 JS 的点击/双击回传 IDE。

**Tech Stack:** Kotlin、IntelliJ Platform(`com.intellij.ui.jcef.JBCefBrowser`/`JBCefJSQuery`/`JBCefApp`)、Jackson、Cytoscape.js 3.30 + cytoscape-dagre + dagre + cytoscape-node-html-label(打包进 `resources/web/`,离线内联)。

**基座不变:** 维持 `intellijIdeaCommunity`(JCEF 两版通用);无需改 `build.gradle.kts`(JCEF 属平台、jackson 已是依赖、`resources/` 自动上 classpath)。

---

## 文件结构

- 新建 `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/CallGraphBuilder.kt` —— `GraphNode`/`GraphEdge`/`CallGraph` + 构建器(纯逻辑)。
- 新建 `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/CallGraphBuilderTest.kt` —— 构建器单测。
- 新建 `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/CallGraphJson.kt` —— `CallGraph`→JSON(给 JS)。
- 新建 `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/ui/CallGraphJsonTest.kt` —— 序列化单测。
- 新建 `threadmap-intellij/src/main/resources/web/{cytoscape.min.js,dagre.min.js,cytoscape-dagre.js,cytoscape-node-html-label.js}` —— 第三方库(下载)。
- 新建 `threadmap-intellij/src/main/resources/web/graph.js` —— 我们的 Cytoscape 初始化脚本。
- 新建 `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/GraphPanel.kt` —— JCEF 宿主 + 建 HTML + JS 桥。
- 改 `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt` —— 树/图 CardLayout 切换 + 接线 + 降级。

---

## Task G1: CallGraphBuilder(去重节点 + 计数边)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/CallGraphBuilder.kt`
- Test: `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/CallGraphBuilderTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.Evidence
import com.threadmap.core.annotate.Understanding
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallGraphBuilderTest {

    private fun node(id: Int, sig: String) = AnnotatedNode(id, sig, "$sig.java", 0, 0)

    @Test
    fun `dedupes repeated callee under same caller into one edge with count`() {
        val root = node(0, "R#r()")
        root.addChild(node(1, "A#a()"))
        root.addChild(node(2, "A#a()"))
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(setOf("R#r()", "A#a()"), g.nodes.map { it.id }.toSet())
        assertEquals(1, g.edges.size)
        assertEquals("R#r()", g.edges[0].from)
        assertEquals("A#a()", g.edges[0].to)
        assertEquals(2, g.edges[0].count)
        assertEquals("R#r()", g.rootId)
    }

    @Test
    fun `same signature under different callers is a single node with two incoming edges`() {
        val root = node(0, "R#r()")
        val a = node(1, "A#a()")
        val b = node(2, "B#b()")
        a.addChild(node(3, "C#c()"))
        b.addChild(node(4, "C#c()"))
        root.addChild(a)
        root.addChild(b)
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(4, g.nodes.size)
        assertEquals(1, g.nodes.count { it.id == "C#c()" })
        assertEquals(2, g.edges.count { it.to == "C#c()" })
    }

    @Test
    fun `self recursion becomes a self loop edge`() {
        val root = node(0, "R#r()")
        root.addChild(node(1, "R#r()"))
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(1, g.nodes.size)
        assertEquals(1, g.edges.size)
        assertEquals("R#r()", g.edges[0].from)
        assertEquals("R#r()", g.edges[0].to)
    }

    @Test
    fun `maps understanding and side effects onto the node`() {
        val root = node(0, "R#r()")
        root.understanding = Understanding.RISKY
        root.annotation = Annotation("做了X", "in", "out", listOf("DB写", "消息"),
            Evidence("R.java", "1-9", listOf("save")), true, "值得查")
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)
        val n = g.nodes.single()

        assertEquals(StatusStyle.RISKY, n.status)
        assertEquals(listOf("DB写", "消息"), n.sideEffects)
        assertTrue(n.digWorthy)
        assertEquals("做了X", n.summary)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.model.CallGraphBuilderTest"`
Expected: 编译失败 / `CallGraphBuilder` 未定义。

- [ ] **Step 3: 实现 CallGraphBuilder**

```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree

/** 调用图的节点(按签名唯一)。 */
data class GraphNode(
    val id: String,
    val label: String,
    val summary: String,
    val status: StatusStyle,
    val sideEffects: List<String>,
    val digWorthy: Boolean,
    val file: String,
    val line: Int,
)

/** 调用图的有向边;count = 该 caller→callee 调用出现次数。 */
data class GraphEdge(val from: String, val to: String, val count: Int)

data class CallGraph(val nodes: List<GraphNode>, val edges: List<GraphEdge>, val rootId: String)

/** AnnotatedTree → 去重节点 + 计数边(纯逻辑,可测)。 */
object CallGraphBuilder {

    fun build(tree: AnnotatedTree): CallGraph {
        val nodes = LinkedHashMap<String, GraphNode>()
        val edgeCounts = LinkedHashMap<Pair<String, String>, Int>()

        fun visit(n: AnnotatedNode) {
            nodes.getOrPut(n.signature) { toGraphNode(n) }
            for (child in n.children) {
                val key = n.signature to child.signature
                edgeCounts[key] = (edgeCounts[key] ?: 0) + 1
                visit(child)
            }
        }
        visit(tree.root)

        val edges = edgeCounts.map { (k, c) -> GraphEdge(k.first, k.second, c) }
        return CallGraph(nodes.values.toList(), edges, tree.root.signature)
    }

    private fun toGraphNode(n: AnnotatedNode): GraphNode = GraphNode(
        id = n.signature,
        label = NodePresentation.compactSignature(n),
        summary = NodePresentation.summary(n),
        status = NodePresentation.statusStyle(n),
        sideEffects = n.annotation?.sideEffects() ?: emptyList(),
        digWorthy = NodePresentation.isDigWorthy(n),
        file = n.file,
        line = n.line,
    )
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.model.CallGraphBuilderTest"`
Expected: PASS(4 个用例)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/CallGraphBuilder.kt \
        threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/CallGraphBuilderTest.kt
git commit -m "feat(intellij): CallGraphBuilder — dedup nodes + counted edges from tree"
```

---

## Task G2: CallGraphJson(序列化给 JS)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/CallGraphJson.kt`
- Test: `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/ui/CallGraphJsonTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.threadmap.intellij.model.CallGraph
import com.threadmap.intellij.model.GraphEdge
import com.threadmap.intellij.model.GraphNode
import com.threadmap.intellij.model.StatusStyle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallGraphJsonTest {

    @Test
    fun `serializes nodes edges and rootId`() {
        val graph = CallGraph(
            nodes = listOf(
                GraphNode("R#r()", "R#r", "入口", StatusStyle.RISKY, listOf("DB写"), true, "R.java", 3),
                GraphNode("A#a()", "A#a", "—", StatusStyle.UNKNOWN, emptyList(), false, "A.java", 0),
            ),
            edges = listOf(GraphEdge("R#r()", "A#a()", 2)),
            rootId = "R#r()",
        )

        val json = CallGraphJson.toJson(graph, ObjectMapper())
        val parsed = ObjectMapper().readTree(json)

        assertEquals("R#r()", parsed.get("rootId").asText())
        assertEquals(2, parsed.get("nodes").size())
        val first = parsed.get("nodes").get(0)
        assertEquals("R#r()", first.get("id").asText())
        assertEquals("RISKY", first.get("status").asText())
        assertEquals("DB写", first.get("sideEffects").get(0).asText())
        assertTrue(first.get("digWorthy").asBoolean())
        val edge = parsed.get("edges").get(0)
        assertEquals("R#r()", edge.get("from").asText())
        assertEquals("A#a()", edge.get("to").asText())
        assertEquals(2, edge.get("count").asInt())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.ui.CallGraphJsonTest"`
Expected: 编译失败 / `CallGraphJson` 未定义。

- [ ] **Step 3: 实现 CallGraphJson**

```kotlin
package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.threadmap.intellij.model.CallGraph

/** 把 CallGraph 序列化成 JS 端 renderGraph 需要的 JSON。 */
object CallGraphJson {

    fun toJson(graph: CallGraph, mapper: ObjectMapper): String {
        val root = mapper.createObjectNode()
        root.put("rootId", graph.rootId)
        val nodes = root.putArray("nodes")
        graph.nodes.forEach { n ->
            val o = nodes.addObject()
            o.put("id", n.id)
            o.put("label", n.label)
            o.put("summary", n.summary)
            o.put("status", n.status.name)
            o.put("digWorthy", n.digWorthy)
            val se = o.putArray("sideEffects")
            n.sideEffects.forEach { se.add(it) }
        }
        val edges = root.putArray("edges")
        graph.edges.forEach { e ->
            val o = edges.addObject()
            o.put("from", e.from)
            o.put("to", e.to)
            o.put("count", e.count)
        }
        return mapper.writeValueAsString(root)
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.ui.CallGraphJsonTest"`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/CallGraphJson.kt \
        threadmap-intellij/src/test/kotlin/com/threadmap/intellij/ui/CallGraphJsonTest.kt
git commit -m "feat(intellij): CallGraphJson — serialize call graph for the JS renderer"
```

---

## Task G3: 打包 Cytoscape 库 + graph.js + 样式

**Files:**
- Create: `threadmap-intellij/src/main/resources/web/cytoscape.min.js`(下载)
- Create: `threadmap-intellij/src/main/resources/web/dagre.min.js`(下载)
- Create: `threadmap-intellij/src/main/resources/web/cytoscape-dagre.js`(下载)
- Create: `threadmap-intellij/src/main/resources/web/cytoscape-node-html-label.js`(下载)
- Create: `threadmap-intellij/src/main/resources/web/graph.js`(我们写)

- [ ] **Step 1: 下载三方库到 resources/web/(锁版本,离线打包)**

Run:
```bash
mkdir -p threadmap-intellij/src/main/resources/web
cd threadmap-intellij/src/main/resources/web
curl -fsSL https://unpkg.com/cytoscape@3.30.2/dist/cytoscape.min.js -o cytoscape.min.js
curl -fsSL https://unpkg.com/dagre@0.8.5/dist/dagre.min.js -o dagre.min.js
curl -fsSL https://unpkg.com/cytoscape-dagre@2.5.0/cytoscape-dagre.js -o cytoscape-dagre.js
curl -fsSL https://unpkg.com/cytoscape-node-html-label@1.2.2/dist/cytoscape-node-html-label.js -o cytoscape-node-html-label.js
ls -la
```
Expected: 4 个 .js 文件,cytoscape.min.js 体积约 400KB+,均非空。

- [ ] **Step 2: 写 graph.js(Cytoscape 初始化 + 富节点卡片 + 点击桥接)**

```javascript
(function () {
  var STATUS_COLOR = { UNKNOWN: '#9AA0A6', HALF: '#D59B22', MASTERED: '#4CAF50', RISKY: '#D64545' };

  function escapeHtml(s) {
    return (s == null ? '' : String(s)).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function render(graph) {
    var elements = [];
    graph.nodes.forEach(function (n) {
      elements.push({ data: {
        id: n.id, label: n.label, summary: n.summary, status: n.status,
        sideEffects: n.sideEffects || [], digWorthy: !!n.digWorthy
      } });
    });
    graph.edges.forEach(function (e) {
      elements.push({ data: {
        id: 'e_' + e.from + '__' + e.to, source: e.from, target: e.to,
        label: e.count > 1 ? ('×' + e.count) : ''
      } });
    });

    if (window.cytoscapeDagre) { cytoscape.use(window.cytoscapeDagre); }

    var cy = cytoscape({
      container: document.getElementById('cy'),
      elements: elements,
      style: [
        { selector: 'node', style: {
          'shape': 'round-rectangle', 'background-color': '#2b2d30',
          'width': '230px', 'height': '54px', 'border-width': 0 } },
        { selector: 'node:selected', style: { 'border-width': 2, 'border-color': '#3574f0' } },
        { selector: 'edge', style: {
          'curve-style': 'bezier', 'target-arrow-shape': 'triangle', 'width': 1.5,
          'line-color': '#6b7280', 'target-arrow-color': '#6b7280',
          'label': 'data(label)', 'font-size': '10px', 'color': '#c9ccd1',
          'text-background-color': '#1e1f22', 'text-background-opacity': 1,
          'text-background-padding': '2px' } }
      ],
      layout: { name: 'dagre', rankDir: 'TB', nodeSep: 28, rankSep: 52 },
      wheelSensitivity: 0.2
    });

    cy.nodeHtmlLabel([{
      query: 'node', halign: 'center', valign: 'center', halignBox: 'center', valignBox: 'center',
      tpl: function (d) {
        var color = STATUS_COLOR[d.status] || STATUS_COLOR.UNKNOWN;
        var pills = (d.sideEffects || []).map(function (s) {
          return '<span class="pill">' + escapeHtml(s) + '</span>';
        }).join('');
        var star = d.digWorthy ? ' <span class="star">★</span>' : '';
        return '<div class="card" style="border-left:4px solid ' + color + '">'
          + '<div class="title">' + escapeHtml(d.label) + star + '</div>'
          + '<div class="summary">' + escapeHtml(d.summary) + '</div>'
          + (pills ? '<div class="pills">' + pills + '</div>' : '')
          + '</div>';
      }
    }]);

    cy.on('tap', 'node', function (evt) {
      if (window.threadmapSelect) window.threadmapSelect(evt.target.id());
    });
    cy.on('dbltap', 'node', function (evt) {
      if (window.threadmapOpen) window.threadmapOpen(evt.target.id());
    });
  }

  if (window.__GRAPH__) render(window.__GRAPH__);
})();
```

- [ ] **Step 3: 提交**

```bash
git add threadmap-intellij/src/main/resources/web/
git commit -m "feat(intellij): bundle Cytoscape libs + graph.js for offline call-graph rendering"
```

---

## Task G4: GraphPanel(JCEF 宿主 + 建 HTML + JS 桥)

无法单测(Swing/JCEF),在 G6 用 runIde 验。本任务只保证**编译通过**并装好接线点。

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/GraphPanel.kt`

- [ ] **Step 1: 实现 GraphPanel**

```kotlin
package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.threadmap.intellij.model.CallGraph
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * 用 JCEF 内嵌 Chromium 渲染调用图。第三方 JS 库 + graph.js 内联进 HTML(离线)。
 * JS 的点击/双击经 JBCefJSQuery 回传:onSelect(签名) / onOpen(签名)。
 */
class GraphPanel(parent: Disposable) : JPanel(BorderLayout()) {

    private val browser = JBCefBrowser()
    private val selectQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val openQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val mapper = ObjectMapper()

    var onSelect: (String) -> Unit = {}
    var onOpen: (String) -> Unit = {}

    init {
        Disposer.register(parent, browser)
        selectQuery.addHandler { sig ->
            ApplicationManager.getApplication().invokeLater { onSelect(sig) }
            null
        }
        openQuery.addHandler { sig ->
            ApplicationManager.getApplication().invokeLater { onOpen(sig) }
            null
        }
        add(browser.component, BorderLayout.CENTER)
    }

    fun render(graph: CallGraph) {
        browser.loadHTML(buildHtml(graph))
    }

    private fun buildHtml(graph: CallGraph): String {
        val json = CallGraphJson.toJson(graph, mapper)
        val cytoscape = res("/web/cytoscape.min.js")
        val dagre = res("/web/dagre.min.js")
        val cytoscapeDagre = res("/web/cytoscape-dagre.js")
        val nodeHtmlLabel = res("/web/cytoscape-node-html-label.js")
        val graphJs = res("/web/graph.js")
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8"><style>
            html,body{margin:0;height:100%;background:#1e1f22;}
            #cy{width:100%;height:100vh;}
            .card{font-family:-apple-system,Segoe UI,sans-serif;color:#dfe1e5;background:#2b2d30;
                  border-radius:6px;padding:5px 8px;width:222px;box-sizing:border-box;overflow:hidden;}
            .title{font-family:ui-monospace,Menlo,monospace;font-weight:600;font-size:12px;
                   white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
            .summary{color:#9aa0a6;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
            .pills{margin-top:3px;}
            .pill{display:inline-block;font-size:10px;padding:0 5px;margin-right:3px;border-radius:3px;
                  background:#3a2c2e;border:1px solid #7c4448;color:#dfe1e5;}
            .star{color:#e5b454;}
            </style></head><body>
            <div id="cy"></div>
            <script>$cytoscape</script>
            <script>$dagre</script>
            <script>$cytoscapeDagre</script>
            <script>$nodeHtmlLabel</script>
            <script>
              window.__GRAPH__ = $json;
              window.threadmapSelect = function(sig){ ${selectQuery.inject("sig")} };
              window.threadmapOpen = function(sig){ ${openQuery.inject("sig")} };
            </script>
            <script>$graphJs</script>
            </body></html>
        """.trimIndent()
    }

    private fun res(path: String): String =
        javaClass.getResourceAsStream(path)?.readBytes()?.toString(Charsets.UTF_8)
            ?: error("缺少打包资源: $path")
}
```

- [ ] **Step 2: 编译确认通过**

Run: `./gradlew :threadmap-intellij:compileKotlin`
Expected: BUILD SUCCESSFUL(若 `JBCefJSQuery.create` 重载报错,确认入参已 `as JBCefBrowserBase`)。

- [ ] **Step 3: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/GraphPanel.kt
git commit -m "feat(intellij): GraphPanel — JCEF host rendering the call graph, JS->IDE bridge"
```

---

## Task G5: ThreadmapPanel 树/图切换 + 接线 + 降级

**Files:**
- Modify: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt`

- [ ] **Step 1: 加导入 + 字段(在现有 import 区与字段区)**

在 import 区加:
```kotlin
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.ui.jcef.JBCefApp
import com.threadmap.intellij.model.CallGraph
import com.threadmap.intellij.model.CallGraphBuilder
import java.awt.CardLayout
```

在 `private var currentSplitter ...` 附近加字段:
```kotlin
    private var currentGraph: CallGraph? = null
    private var graphMode = false
    private val leftCards = JPanel(CardLayout())
    private val graphPanel: GraphPanel? =
        if (JBCefApp.isSupported()) GraphPanel(this).also { gp ->
            gp.onSelect = { sig -> selectBySignature(sig) }
            gp.onOpen = { sig -> openBySignature(sig) }
        } else null
```

- [ ] **Step 2: 工具栏加「树/图」切换(在 `buildToolbar()` 的 `DefaultActionGroup` 里,折叠按钮之后)**

在 `add(toolbarAction("折叠", ...) { setAllExpanded(false) })` 之后加:
```kotlin
            if (graphPanel != null) {
                addSeparator()
                add(object : ToggleAction("图视图", "在树与调用图之间切换", AllIcons.Graph.Layout) {
                    override fun isSelected(e: AnActionEvent): Boolean = graphMode
                    override fun setSelected(e: AnActionEvent, state: Boolean) { setGraphMode(state) }
                })
            }
```

- [ ] **Step 3: 左侧改为 CardLayout(在 `showTree` 里把树塞进卡片,并构建图)**

把 `showTree` 中正常分支的：
```kotlin
        splitter.firstComponent = JBScrollPane(table).apply {
            border = JBUI.Borders.empty()
        }
        splitter.secondComponent = tabs
        setContent(splitter)
        applyResponsiveLayout()
```
改为：
```kotlin
        val treeScroll = JBScrollPane(table).apply { border = JBUI.Borders.empty() }
        leftCards.removeAll()
        leftCards.add(treeScroll, "tree")
        graphPanel?.let { leftCards.add(it, "graph") }
        currentGraph = CallGraphBuilder.build(tree)
        graphPanel?.takeIf { graphMode }?.render(currentGraph!!)
        showLeftCard()
        splitter.firstComponent = leftCards
        splitter.secondComponent = tabs
        setContent(splitter)
        applyResponsiveLayout()
```

并在空树分支(`rootNode == null`)保持原样(只显示 `filteredEmptyState()`,不进图模式)。

- [ ] **Step 4: 加切换 + 联动辅助方法(放在 `showTree` 之后)**

```kotlin
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
```

注:`showNodeDetails(table, tree, node)` 需要一个 `TreeTableView`;图模式下选中只更新详情面板,用现有 `currentTable`。若 `currentTable` 为 null(理论上图模式时树已构建过,不为 null)则跳过。

- [ ] **Step 5: clean 编译(Kotlin 增量易 stale,见项目记忆)**

Run: `./gradlew :threadmap-intellij:clean :threadmap-intellij:compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 跑全量测试(确保未碰坏 G1/G2 与既有用例)**

Run: `./gradlew :threadmap-intellij:test`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt
git commit -m "feat(intellij): tree/graph toggle in tool window, wire graph selection+navigation"
```

---

## Task G6: runIde 端到端验收 + 打磨

**Files:** 无新增(按需微调 `graph.js` 样式 / `GraphPanel` HTML CSS)。

- [ ] **Step 1: clean 后 runIde**

Run: `./gradlew :threadmap-intellij:clean :threadmap-intellij:runIde`
(沙箱里打开含 `.threadmap/annotated-tree.json` 的工程,如 AECLAW。)

- [ ] **Step 2: 人工验收清单**

- 工具栏出现「图视图」切换;点开 → 左侧从树切到图。
- 图中:`submitRound` 在上,`check`/`calculate`/`route` 在下,箭头方向正确。
- 重复调用:`RuleEngineService#evaluate` 等只一个节点;重复边标 `×N`。
- 节点卡片:左色条(红/黄/绿/灰)、`类#方法`、灰摘要、`DB写`/`log.info` 药丸、★。
- 单击节点 → 右侧详情面板更新为该节点。
- 双击节点 → 在编辑器打开源码。
- 缩放(滚轮)、拖拽画布、拖动节点正常。
- 切回「树视图」→ 树/详情/待查一切照旧。

- [ ] **Step 3: 按观感微调并提交**

如需调整节点尺寸/间距/配色,改 `graph.js` 的 `style`/`layout` 或 `GraphPanel` 的 CSS;每次改后 `clean` 再 `runIde`。

```bash
git add -A
git commit -m "polish(intellij): call-graph layout/colors per runIde review"
```

---

## 自检

**1. spec 覆盖:** §2 放置/切换→G5;§3 CallGraphBuilder→G1;§4 节点视觉(色条/摘要/药丸/×N)→graph.js(G3)+GraphPanel CSS(G4);§5 JCEF+Cytoscape→G3/G4;§6 JS 桥→G4(JBCefJSQuery)+G5(selectBySignature/openBySignature);§7 降级→G5(`JBCefApp.isSupported()`→graphPanel 为 null,不加切换按钮);§8 测试→G1/G2 单测 + G6 手验。全覆盖。

**2. 占位扫描:** 无 TBD/TODO;每个代码步骤含完整代码;下载库给了确切版本与 URL。

**3. 类型一致:** `CallGraph`/`GraphNode`/`GraphEdge`/`StatusStyle` 在 G1 定义,G2/G4/G5 一致引用;`CallGraphJson.toJson(graph, mapper)` 签名 G2 定义、G4 调用一致;`GraphPanel(parent)` + `onSelect`/`onOpen` + `render(graph)` G4 定义、G5 使用一致;`findBySignature`/`selectBySignature`/`openBySignature` 在 G5 内自洽;复用既有 `NodePresentation.*`、`SourceNavigator.navigate`、`showNodeDetails`。

**风险点(实现时留意):** ① `AllIcons.Graph.Layout` 图标常量名若不存在,换 `AllIcons.Actions.ShowAsTree` 之外的任意现有图标或用文字按钮;② `cytoscape-node-html-label` 的全局注册名/用法若与假设不符,降级为 Cytoscape 原生样式节点(背景按状态着色 + 多行 label),不阻塞主路;③ `JBCefJSQuery.create` 入参类型以 `as JBCefBrowserBase` 兜底。
