# Threadmap M3a — 插件骨架 + 工具窗 + 列式调用树 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建起 `threadmap-intellij` 插件模块,在 IntelliJ 右侧停靠工具窗「脉络 (Threadmap)」里加载 core 产出的 `annotated-tree.json`,渲染成带左侧状态色条的列式可折叠调用树。

**Architecture:** 新增 Kotlin 插件模块(IntelliJ Platform Gradle Plugin 2.x)依赖 `threadmap-core`。core 补一个对称于 writer 的 `AnnotatedTreeJsonReader`(JVM 可测)。插件把可测逻辑(树节点构建、列文本/状态提取)与薄 UI 壳(TreeTable + renderer + ToolWindow)分离:逻辑走 JUnit,UI 走 `runIde` 手验。

**Tech Stack:** Kotlin、Gradle 8.7、IntelliJ Platform Gradle Plugin 2.x、IntelliJ IDEA Community 2024.2、Jackson(经 core)、JUnit 5、IntelliJ `TreeTable`/`ListTreeTableModel`/`ColumnInfo`。

**承接(M1–M4 已就绪,不改其文件):** `AnnotatedTree(getEntrySignature/getCapturedAt/getRoot)` / `AnnotatedNode(id,signature,file,line,selfMs + setCollapsed/setAnnotation/setUnderstanding/addChild)` / `Annotation(summary,inputs,outputs,sideEffects,evidence,digWorthy,digReason)` / `Evidence(file,lines,calls)` / `Understanding{UNKNOWN,HALF,MASTERED,RISKY}` / `AnnotatedTreeJsonWriter`。设计见 [specs/2026-06-18-threadmap-m3-intellij-plugin-impl.md](../specs/2026-06-18-threadmap-m3-intellij-plugin-impl.md)。

**M3a 后续:** M3b(详情面板 + PSI 跳源码)、M3c(进度持久化 + 待查清单 + 导出)。

---

## 文件结构

```
settings.gradle                                              # 改:include ':threadmap-intellij'
threadmap-core/src/main/java/com/threadmap/core/annotate/
  AnnotatedTreeJsonReader.java                               # 创建:annotated-tree.json → AnnotatedTree
threadmap-core/src/test/java/com/threadmap/core/annotate/
  AnnotatedTreeJsonReaderTest.java                           # 创建:writer→reader round-trip
threadmap-intellij/build.gradle.kts                          # 创建:插件模块构建
threadmap-intellij/src/main/resources/META-INF/plugin.xml    # 创建:插件描述 + 工具窗注册
threadmap-intellij/src/main/kotlin/com/threadmap/intellij/
  model/CallTreeNodeBuilder.kt                               # 创建:AnnotatedTree → DefaultMutableTreeNode(可测)
  model/NodePresentation.kt                                  # 创建:节点→列文本/短签名/状态(可测)
  ui/ThreadmapToolWindowFactory.kt                           # 创建:注册工具窗内容
  ui/ThreadmapPanel.kt                                       # 创建:主面板(TreeTable 容器 + 加载)
  ui/CallTreeTable.kt                                        # 创建:ListTreeTableModel + ColumnInfo + TreeTable
  ui/StatusStripeRenderer.kt                                 # 创建:左侧状态色条渲染
threadmap-intellij/src/test/kotlin/com/threadmap/intellij/
  model/CallTreeNodeBuilderTest.kt                           # 创建
  model/NodePresentationTest.kt                              # 创建
```

---

## Task 1: 插件 gradle 模块 + 空工具窗(能装、能 runIde)

**Files:**
- Modify: `settings.gradle`
- Create: `threadmap-intellij/build.gradle.kts`
- Create: `threadmap-intellij/src/main/resources/META-INF/plugin.xml`
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapToolWindowFactory.kt`

- [ ] **Step 1: 注册模块**

`settings.gradle` 末尾追加一行(已有 `include ':threadmap-core'`):
```groovy
include ':threadmap-intellij'
```

- [ ] **Step 2: 写插件 build.gradle.kts**

`threadmap-intellij/build.gradle.kts`:
```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.threadmap"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":threadmap-core"))
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        instrumentationTools()
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
```
**版本不确定:** 若 `2.5.0` / `intellijIdeaCommunity` / `instrumentationTools()` 与当前平台不符导致同步失败,核对 https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform 取最新 2.x、并按 https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html 调整 DSL(2.x 稳定 API);Kotlin 版本与所选 IDE 捆绑版对齐。报告所用版本组合。

- [ ] **Step 3: 写 plugin.xml**

`threadmap-intellij/src/main/resources/META-INF/plugin.xml`:
```xml
<idea-plugin>
    <id>com.threadmap.intellij</id>
    <name>脉络 Threadmap</name>
    <vendor>threadmap</vendor>
    <description>渲染 threadmap 引擎产出的 annotated-tree.json:列式调用树 + 标注 + 接管进度。</description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.java</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow id="脉络 (Threadmap)" anchor="right" secondary="false"
                    factoryClass="com.threadmap.intellij.ui.ThreadmapToolWindowFactory"/>
    </extensions>
</idea-plugin>
```

- [ ] **Step 4: 写最小 ToolWindowFactory(空面板占位)**

`threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapToolWindowFactory.kt`:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel

class ThreadmapToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val placeholder = JLabel("脉络:加载 annotated-tree.json 后显示调用树")
        val content = ContentFactory.getInstance().createContent(placeholder, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

- [ ] **Step 5: 验证构建 + 手验工具窗**

Run: `./gradlew :threadmap-intellij:buildPlugin`
Expected: `BUILD SUCCESSFUL`(首次会下载 IDE 依赖,较慢)。
手验(可选,本步不阻塞自动化): `./gradlew :threadmap-intellij:runIde` 启动沙箱 IDE,右侧应出现「脉络 (Threadmap)」工具窗,显示占位文字。**报告 buildPlugin 是否成功**;runIde 若环境无图形界面可跳过,留待有界面时手验。

- [ ] **Step 6: 提交**

```bash
git add settings.gradle threadmap-intellij/build.gradle.kts \
        threadmap-intellij/src/main/resources/META-INF/plugin.xml \
        threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapToolWindowFactory.kt
git commit -m "feat(intellij): scaffold plugin module + Threadmap tool window" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: core AnnotatedTreeJsonReader(对称 writer)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTreeJsonReader.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedTreeJsonReaderTest.java`

读 `AnnotatedTreeJsonWriter` 产出的 json,反序列化回 `AnnotatedTree`。折叠节点无标注字段(`summary` 等缺失)→ `annotation` 留 null;`understanding` 总存在(小写枚举名);`dig_reason` 可缺失。

- [ ] **Step 1: 写失败测试**

`AnnotatedTreeJsonReaderTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedTreeJsonReaderTest {

    private AnnotatedTree sampleTree() {
        AnnotatedNode root = new AnnotatedNode(0, "com.example.A#a()", "com/example/A.java", 0, 5);
        root.setUnderstanding(Understanding.MASTERED);
        root.setAnnotation(new Annotation("做 A", "无", "B 结果",
                List.of("DB写"), new Evidence("com/example/A.java", "10-20", List.of("b")),
                true, "核心逻辑"));
        AnnotatedNode lib = new AnnotatedNode(1, "org.lib.X#y()", "org/lib/X.java", 0, 1);
        lib.setCollapsed(true); // 折叠节点:无标注
        root.addChild(lib);
        return new AnnotatedTree("com.example.A#a()", "2026-06-18T00:00:00Z", root);
    }

    @Test
    void roundTripsWriterOutput() throws IOException {
        String json = new AnnotatedTreeJsonWriter().toJson(sampleTree());

        AnnotatedTree tree = new AnnotatedTreeJsonReader().read(json);

        assertEquals("com.example.A#a()", tree.getEntrySignature());
        assertEquals("2026-06-18T00:00:00Z", tree.getCapturedAt());

        AnnotatedNode root = tree.getRoot();
        assertEquals(0, root.getId());
        assertEquals("com.example.A#a()", root.getSignature());
        assertEquals(5, root.getSelfMs());
        assertFalse(root.isCollapsed());
        assertEquals(Understanding.MASTERED, root.getUnderstanding());
        assertNotNull(root.getAnnotation());
        assertEquals("做 A", root.getAnnotation().summary());
        assertTrue(root.getAnnotation().sideEffects().contains("DB写"));
        assertEquals("10-20", root.getAnnotation().evidence().lines());
        assertTrue(root.getAnnotation().digWorthy());
        assertEquals("核心逻辑", root.getAnnotation().digReason());

        assertEquals(1, root.getChildren().size());
        AnnotatedNode lib = root.getChildren().get(0);
        assertTrue(lib.isCollapsed());
        assertNull(lib.getAnnotation(), "折叠节点无标注");
        assertEquals(Understanding.UNKNOWN, lib.getUnderstanding());
    }

    @Test
    void rejectsMissingRoot() {
        String bad = "{\"entry_signature\":\"A#a()\",\"captured_at\":\"t\"}";
        assertThrows(IOException.class, () -> new AnnotatedTreeJsonReader().read(bad));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedTreeJsonReaderTest'`
Expected: 编译失败(`AnnotatedTreeJsonReader` 不存在)。

- [ ] **Step 3: 实现**

`AnnotatedTreeJsonReader.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把 annotated-tree.json 反序列化回 AnnotatedTree(对称于 AnnotatedTreeJsonWriter)。 */
public class AnnotatedTreeJsonReader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnnotatedTree read(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode rootNode = requireField(root, "root");
        return new AnnotatedTree(
                requireField(root, "entry_signature").asText(),
                requireField(root, "captured_at").asText(),
                node(rootNode));
    }

    private static JsonNode requireField(JsonNode obj, String name) throws IOException {
        JsonNode f = obj.get(name);
        if (f == null || f.isNull()) {
            throw new IOException("annotated-tree.json missing required field: " + name);
        }
        return f;
    }

    private AnnotatedNode node(JsonNode n) {
        AnnotatedNode an = new AnnotatedNode(
                n.get("id").asInt(),
                n.get("signature").asText(),
                n.get("file").asText(),
                n.get("line").asInt(),
                n.get("self_ms").asLong());

        JsonNode collapsed = n.get("collapsed");
        if (collapsed != null) {
            an.setCollapsed(collapsed.asBoolean());
        }
        JsonNode understanding = n.get("understanding");
        if (understanding != null && !understanding.isNull()) {
            an.setUnderstanding(Understanding.valueOf(understanding.asText().toUpperCase(Locale.ROOT)));
        }
        if (n.has("summary")) {
            an.setAnnotation(annotation(n));
        }

        JsonNode children = n.get("children");
        if (children != null) {
            for (JsonNode c : children) {
                an.addChild(node(c));
            }
        }
        return an;
    }

    private Annotation annotation(JsonNode n) {
        JsonNode ev = n.get("evidence");
        Evidence evidence = new Evidence(
                textOrEmpty(ev, "file"),
                textOrEmpty(ev, "lines"),
                stringList(ev == null ? null : ev.get("calls")));
        return new Annotation(
                textOrEmpty(n, "summary"),
                textOrEmpty(n, "inputs"),
                textOrEmpty(n, "outputs"),
                stringList(n.get("side_effects")),
                evidence,
                n.path("dig_worthy").asBoolean(false),
                n.has("dig_reason") ? n.get("dig_reason").asText() : null);
    }

    private static String textOrEmpty(JsonNode obj, String name) {
        if (obj == null) return "";
        JsonNode f = obj.get(name);
        return f == null || f.isNull() ? "" : f.asText();
    }

    private static List<String> stringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode e : arr) {
                out.add(e.asText());
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: 运行,确认通过 + 全量**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedTreeJsonReaderTest'`
Expected: PASS。
Run: `./gradlew :threadmap-core:test`
Expected: 全绿(原 53 + 新 2 = 55)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTreeJsonReader.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedTreeJsonReaderTest.java
git commit -m "feat(core): add AnnotatedTreeJsonReader (symmetric to writer)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 插件可测逻辑(树构建 + 列文本/状态提取)

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/CallTreeNodeBuilder.kt`
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/NodePresentation.kt`
- Test: `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/CallTreeNodeBuilderTest.kt`
- Test: `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/NodePresentationTest.kt`

纯逻辑,不依赖 IntelliJ runtime(`DefaultMutableTreeNode` 来自 `javax.swing.tree`,JVM 可用),可 JUnit 测。`NodePresentation` 把状态映射成语义枚举 `StatusStyle`(UI 层再映射 JBColor),保持逻辑层不碰 IntelliJ UI 类。

- [ ] **Step 1: 写失败测试**

`CallTreeNodeBuilderTest.kt`:
```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

class CallTreeNodeBuilderTest {

    @Test
    fun buildsMirroringTreeWithAnnotatedNodeUserObjects() {
        val root = AnnotatedNode(0, "A#a()", "A.java", 0, 5)
        val child = AnnotatedNode(1, "B#b()", "B.java", 0, 1)
        root.addChild(child)
        val tree = AnnotatedTree("A#a()", "t", root)

        val node: DefaultMutableTreeNode = CallTreeNodeBuilder.build(tree)

        assertSame(root, node.userObject)
        assertEquals(1, node.childCount)
        val childNode = node.getChildAt(0) as DefaultMutableTreeNode
        assertSame(child, childNode.userObject)
    }
}
```

`NodePresentationTest.kt`:
```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NodePresentationTest {

    private fun annotated(): AnnotatedNode {
        val n = AnnotatedNode(0, "com.example.svc.Order#place(Cart, int)", "x", 0, 5)
        n.understanding = Understanding.MASTERED
        n.setAnnotation(Annotation("下单并扣库存", "Cart", "Order",
                listOf("DB写", "外部API"),
                Evidence("x", "1-9", listOf("save")), true, "核心"))
        return n
    }

    @Test
    fun shortSignatureDropsPackage() {
        assertEquals("Order#place(Cart, int)", NodePresentation.shortSignature(annotated()))
    }

    @Test
    fun summaryFallsBackToDashWhenUnannotated() {
        val bare = AnnotatedNode(0, "A#a()", "x", 0, 1)
        assertEquals("—", NodePresentation.summary(bare))
        assertEquals("下单并扣库存", NodePresentation.summary(annotated()))
    }

    @Test
    fun sideEffectsJoinedAndStatusMapped() {
        assertEquals("DB写, 外部API", NodePresentation.sideEffects(annotated()))
        assertEquals("", NodePresentation.sideEffects(AnnotatedNode(0, "A#a()", "x", 0, 1)))
        assertEquals(StatusStyle.MASTERED, NodePresentation.statusStyle(annotated()))
        assertEquals(StatusStyle.UNKNOWN, NodePresentation.statusStyle(AnnotatedNode(0, "A#a()", "x", 0, 1)))
    }

    @Test
    fun digWorthyFlag() {
        assertTrue(NodePresentation.isDigWorthy(annotated()))
        assertFalse(NodePresentation.isDigWorthy(AnnotatedNode(0, "A#a()", "x", 0, 1)))
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-intellij:test`
Expected: 编译失败(类不存在)。

- [ ] **Step 3: 实现**

`CallTreeNodeBuilder.kt`:
```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import javax.swing.tree.DefaultMutableTreeNode

/** 把 AnnotatedTree 镜像成 Swing 树:每个 DefaultMutableTreeNode 的 userObject 是对应 AnnotatedNode。 */
object CallTreeNodeBuilder {
    fun build(tree: AnnotatedTree): DefaultMutableTreeNode = node(tree.root)

    private fun node(an: AnnotatedNode): DefaultMutableTreeNode {
        val dmtn = DefaultMutableTreeNode(an)
        for (child in an.children) {
            dmtn.add(node(child))
        }
        return dmtn
    }
}
```

`NodePresentation.kt`:
```kotlin
package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.Understanding

/** 掌握状态的 UI 语义(UI 层映射到具体 JBColor)。 */
enum class StatusStyle { UNKNOWN, HALF, MASTERED, RISKY }

/** 从 AnnotatedNode 提取列展示文本与状态(纯逻辑,可测)。 */
object NodePresentation {

    /** 去包前缀的短签名:com.example.svc.Order#place(Cart, int) → Order#place(Cart, int)。 */
    fun shortSignature(node: AnnotatedNode): String {
        val sig = node.signature
        val hash = sig.indexOf('#')
        if (hash < 0) return sig
        val fqcn = sig.substring(0, hash)
        val simple = fqcn.substringAfterLast('.')
        return simple + sig.substring(hash)
    }

    fun summary(node: AnnotatedNode): String = node.annotation?.summary() ?: "—"

    fun sideEffects(node: AnnotatedNode): String =
        node.annotation?.sideEffects()?.joinToString(", ") ?: ""

    fun statusStyle(node: AnnotatedNode): StatusStyle = when (node.understanding) {
        Understanding.UNKNOWN -> StatusStyle.UNKNOWN
        Understanding.HALF -> StatusStyle.HALF
        Understanding.MASTERED -> StatusStyle.MASTERED
        Understanding.RISKY -> StatusStyle.RISKY
    }

    fun isDigWorthy(node: AnnotatedNode): Boolean = node.annotation?.digWorthy() ?: false
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-intellij:test`
Expected: PASS(4 测试)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/model/ \
        threadmap-intellij/src/test/kotlin/com/threadmap/intellij/model/
git commit -m "feat(intellij): add testable call-tree model + node presentation" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: UI 组装(列式 TreeTable + 状态色条 + 加载),runIde 手验

**Files:**
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/CallTreeTable.kt`
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/StatusStripeRenderer.kt`
- Create: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapPanel.kt`
- Modify: `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/ThreadmapToolWindowFactory.kt`

UI 壳,无法 JUnit 自动测,靠 `runIde` 手验。代码基于 IntelliJ `ListTreeTableModel` + `ColumnInfo` + `TreeTable`;若某 API 与当前平台版本签名不符,核对当前平台源码修正(这些为平台稳定 API),报告偏差。

- [ ] **Step 1: 列式 TreeTable**

`CallTreeTable.kt`:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.ui.dualView.TreeTableView
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.util.ui.ColumnInfo
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.core.annotate.AnnotatedNode
import javax.swing.tree.DefaultMutableTreeNode

private fun annotatedOf(value: Any?): AnnotatedNode? =
    ((value as? DefaultMutableTreeNode)?.userObject) as? AnnotatedNode

/** 第一列(树列):短签名 + 待查 ★。其余列:摘要 / 状态 / 副作用。 */
class SignatureColumn : ColumnInfo<DefaultMutableTreeNode, String>("签名") {
    override fun valueOf(item: DefaultMutableTreeNode): String {
        val n = annotatedOf(item) ?: return ""
        val star = if (NodePresentation.isDigWorthy(n)) " ★" else ""
        return NodePresentation.shortSignature(n) + star
    }
    override fun getColumnClass(): Class<*> = com.intellij.ui.treeStructure.treetable.TreeTableModel::class.java
}

class SummaryColumn : ColumnInfo<DefaultMutableTreeNode, String>("摘要") {
    override fun valueOf(item: DefaultMutableTreeNode): String =
        annotatedOf(item)?.let { NodePresentation.summary(it) } ?: ""
}

class StatusColumn : ColumnInfo<DefaultMutableTreeNode, String>("状态") {
    override fun valueOf(item: DefaultMutableTreeNode): String = when (annotatedOf(item)?.let { NodePresentation.statusStyle(it) }) {
        com.threadmap.intellij.model.StatusStyle.MASTERED -> "已掌握"
        com.threadmap.intellij.model.StatusStyle.HALF -> "半懂"
        com.threadmap.intellij.model.StatusStyle.RISKY -> "高风险"
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
    table.tree.setCellRenderer(StatusStripeRenderer())
    return table
}
```
**API 注意:** `ListTreeTableModelOnColumns` + `TreeTableView` 是 IntelliJ 平台列式树标准组合;`getColumnClass` 返回 `TreeTableModel.class` 的列即为树列(渲染折叠箭头)。若包路径/类名在所选平台版本不同,在 `external libraries` 里搜 `ListTreeTableModel*` / `TreeTableView` 核对。

- [ ] **Step 2: 状态色条 renderer**

`StatusStripeRenderer.kt`:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.threadmap.intellij.model.NodePresentation
import com.threadmap.intellij.model.StatusStyle
import com.threadmap.core.annotate.AnnotatedNode
import java.awt.Color
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** 树列单元渲染:左侧 4px 状态色条(借边框实现) + 短签名;折叠工具类节点灰显。 */
class StatusStripeRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? AnnotatedNode ?: return
        border = com.intellij.util.ui.JBUI.Borders.customLine(stripeColor(NodePresentation.statusStyle(node)), 0, 4, 0, 0)
        val text = NodePresentation.shortSignature(node) + if (NodePresentation.isDigWorthy(node)) " ★" else ""
        if (node.isCollapsed) {
            append(text, com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
        } else {
            append(text)
        }
    }

    private fun stripeColor(style: StatusStyle): Color = when (style) {
        StatusStyle.UNKNOWN -> JBColor.GRAY
        StatusStyle.HALF -> JBColor.YELLOW
        StatusStyle.MASTERED -> JBColor.GREEN
        StatusStyle.RISKY -> JBColor.RED
    }
}
```

- [ ] **Step 3: 主面板 + 加载**

`ThreadmapPanel.kt`:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.threadmap.core.annotate.AnnotatedTreeJsonReader
import com.threadmap.intellij.model.CallTreeNodeBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JLabel

/** 工具窗主面板:工具栏(加载) + 调用树滚动区。 */
class ThreadmapPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    init {
        setContent(JScrollPane(JLabel("点击「加载 Trace」选择 .threadmap/annotated-tree.json")))
        val load = JButton("加载 Trace")
        load.addActionListener { chooseAndLoad() }
        // 简单起见用按钮工具条;后续 M3c 换成 AnAction 工具栏
        val top = javax.swing.JToolBar()
        top.isFloatable = false
        top.add(load)
        toolbar = top
        // 启动时尝试自动加载工作区默认路径
        project.basePath?.let { autoLoad(Path.of(it, ".threadmap", "annotated-tree.json")) }
    }

    private fun chooseAndLoad() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        val file: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        render(Path.of(file.path))
    }

    private fun autoLoad(path: Path) {
        if (Files.isRegularFile(path)) render(path)
    }

    private fun render(path: Path) {
        val json = Files.readString(path)
        val tree = AnnotatedTreeJsonReader().read(json)
        val rootNode = CallTreeNodeBuilder.build(tree)
        val table = buildTreeTable(rootNode)
        setContent(JScrollPane(table))
    }
}
```

`ThreadmapToolWindowFactory.kt` 改为挂主面板:
```kotlin
package com.threadmap.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ThreadmapToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ThreadmapPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

- [ ] **Step 4: 构建 + 手验**

Run: `./gradlew :threadmap-intellij:buildPlugin`
Expected: `BUILD SUCCESSFUL`。
手验: `./gradlew :threadmap-intellij:runIde`,在沙箱 IDE 打开任意项目 → 右侧「脉络 (Threadmap)」→「加载 Trace」选 M4 产出的 `aliexpress-business-partner/app/target/threadmap/annotated-tree-qwen.json` → 应渲染出列式调用树(签名/摘要/状态/副作用四列、左侧状态色条、可折叠)。**报告 buildPlugin 结果**;runIde 手验若无图形环境留待用户执行,在报告中说明。

- [ ] **Step 5: 提交**

```bash
git add threadmap-intellij/src/main/kotlin/com/threadmap/intellij/ui/
git commit -m "feat(intellij): columnar call-tree TreeTable with status stripe + loader" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准(M3a Done)

- `./gradlew :threadmap-core:test` 全绿(含新增 reader 测试,55)。
- `./gradlew :threadmap-intellij:test` 全绿(树构建 + 节点展示逻辑)。
- `./gradlew :threadmap-intellij:buildPlugin` 成功产出插件 zip。
- `runIde` 手验:工具窗加载真实 AECLAW `annotated-tree.json`,渲染列式可折叠调用树 + 左侧状态色条(灰/黄/绿/红)+ 待查 ★ + 折叠节点灰显。
- 可测逻辑(reader / 树构建 / 列展示)与平台壳(TreeTable / renderer / toolwindow)清晰分离。

## 后续(M3b / M3c)

- **M3b:** 选中节点详情面板(证据表格等) + 双击经 PSI 按 signature 反查跳源码。
- **M3c:** 掌握状态选择器 + `.threadmap/progress.json` 持久化(按 signature) + 待查清单面板 + 导出 `todo.md` + 工具栏过滤。
- 打包优化:插件现依赖整个 `threadmap-core`(含 langchain4j/javaparser),v1 发布前考虑拆出轻量 `threadmap-model`(模型 + json reader/writer,仅 jackson)供插件依赖。
