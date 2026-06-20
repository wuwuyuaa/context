# 静态调用链构建器 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 在方法上右键「看这条链」→ PSI 静态走出调用链(Bean 级过滤 + 接口实现解析)→ 即时渲染进现有图/树/详情(无 LLM);该树同时写成 trace,可走现有标注管线补 AI。

**Architecture:** 静态构建器 = 第二个 trace 生产者。`NodePolicy`(纯逻辑取舍)→ `StaticCallGraphWalker`(PSI 走,产出 core 的 `TraceNode` 树)→ 插件把它当 `AnnotatedTree`(annotation=null)喂给已有 viewer + 写 `.threadmap/static-trace.json`。下游标注/图/详情/进度全复用。

**Tech Stack:** Kotlin、IntelliJ PSI(`PsiMethod`/`PsiMethodCallExpression.resolveMethod`/`ClassInheritorsSearch`/`AnnotationUtil`/`PropertyUtilBase`)、`LightJavaCodeInsightFixtureTestCase`、复用 `threadmap-core` 的 `TraceNode`/`Trace`/`TraceJsonWriter`。

**前置确认(实现前先验证一次,写进 S2 第一步):** `threadmap-intellij` 能否 import core 的 `TraceNode`/`Trace`/`TraceJsonWriter`(它们无重依赖,插件已 `implementation(project(":threadmap-core"))`)。若包路径不符,以实际为准并在本计划内统一。

---

## 文件结构
- 新建 `threadmap-intellij/.../psi/NodePolicy.kt` —— 取舍判定(纯逻辑)。
- 新建 `threadmap-intellij/.../psi/StaticCallGraphWalker.kt` —— PSI 走 → `TraceNode` 树。
- 新建 `threadmap-intellij/.../psi/ShowChainAction.kt` —— 右键 `AnAction`。
- 改 `threadmap-intellij/.../ui/ThreadmapPanel.kt` —— 加 `renderStaticTree(AnnotatedTree)` 入口(供 action 调用)。
- 改 `threadmap-intellij/src/main/resources/META-INF/plugin.xml` —— 注册 action 到编辑器右键菜单。
- 测试:`NodePolicyTest.kt`(纯逻辑)、`StaticCallGraphWalkerTest.kt`(`LightJavaCodeInsightFixtureTestCase`)。

---

## Task S1: NodePolicy(取舍判定,纯逻辑)

**Files:** Create `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/psi/NodePolicy.kt`;Test `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/psi/NodePolicyTest.kt`

- [ ] **Step 1: 写失败测试**
```kotlin
package com.threadmap.intellij.psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NodePolicyTest {
    private fun t(
        fqn: String? = "com.ae.x.Svc", inBase: Boolean = true, iface: Boolean = false,
        comp: Boolean = false, repoPort: Boolean = false, getter: Boolean = false, priv: Boolean = false,
    ) = CallTarget(fqn, inBase, iface, comp, repoPort, getter, priv)

    @Test fun `out of scope is skipped`() {
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(fqn = "java.lang.String", inBase = false)))
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(fqn = null, inBase = false)))
    }
    @Test fun `repository or port is a leaf`() =
        assertEquals(NodeDecision.LEAF, NodePolicy.decide(t(repoPort = true)))
    @Test fun `component method is an include node`() =
        assertEquals(NodeDecision.INCLUDE, NodePolicy.decide(t(comp = true)))
    @Test fun `in-scope interface is an include node (resolved later)`() =
        assertEquals(NodeDecision.INCLUDE, NodePolicy.decide(t(iface = true)))
    @Test fun `own private helper is followed through`() =
        assertEquals(NodeDecision.FOLLOW_THROUGH, NodePolicy.decide(t(priv = true)))
    @Test fun `getter setter builder is skipped`() =
        assertEquals(NodeDecision.SKIP, NodePolicy.decide(t(getter = true)))
    @Test fun `in-scope plain method is followed through to find component calls`() =
        assertEquals(NodeDecision.FOLLOW_THROUGH, NodePolicy.decide(t()))
}
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.psi.NodePolicyTest"`(预期:未定义)

- [ ] **Step 3: 实现**
```kotlin
package com.threadmap.intellij.psi

/** 静态走链对一个被调目标的取舍。 */
enum class NodeDecision { INCLUDE, LEAF, FOLLOW_THROUGH, SKIP }

/** 被调目标的判定输入(纯数据,便于不依赖 PSI 单测)。 */
data class CallTarget(
    val classFqn: String?,
    val inBasePackage: Boolean,
    val isInterface: Boolean,
    val hasComponentStereotype: Boolean,
    val isRepositoryOrPort: Boolean,
    val isGetterSetterOrBuilder: Boolean,
    val isOwnPrivateHelper: Boolean,
)

/** 取舍规则:Bean 级过滤,复刻动态 AOP 的边界。 */
object NodePolicy {
    fun decide(t: CallTarget): NodeDecision = when {
        t.classFqn == null || !t.inBasePackage -> NodeDecision.SKIP
        t.isRepositoryOrPort -> NodeDecision.LEAF
        t.hasComponentStereotype || t.isInterface -> NodeDecision.INCLUDE
        t.isOwnPrivateHelper -> NodeDecision.FOLLOW_THROUGH
        t.isGetterSetterOrBuilder -> NodeDecision.SKIP
        else -> NodeDecision.FOLLOW_THROUGH
    }
}
```

- [ ] **Step 4: 跑测试确认通过**(预期:7 通过)
- [ ] **Step 5: 提交** — `git add` 两文件;`git commit -m "feat(intellij): NodePolicy — static call-graph node selection rules"`

---

## Task S2: StaticCallGraphWalker(PSI 走 → TraceNode 树)

**Files:** Create `threadmap-intellij/src/main/kotlin/com/threadmap/intellij/psi/StaticCallGraphWalker.kt`;Test `threadmap-intellij/src/test/kotlin/com/threadmap/intellij/psi/StaticCallGraphWalkerTest.kt`

- [ ] **Step 0: 确认 core 类路径** — 在 core 找到 `TraceNode`/`Trace`/`TraceJsonWriter` 的实际包名(`grep -rl "class TraceNode" threadmap-core/src/main/java`),本任务 import 以实际为准。已知 `TraceNode(int id, String signature, String file, int line)` + `addChild` + getter。

- [ ] **Step 1: 写失败 fixture 测试**(`LightJavaCodeInsightFixtureTestCase` 是 JUnit3 风格:方法名 `testXxx`)
```kotlin
package com.threadmap.intellij.psi

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class StaticCallGraphWalkerTest : LightJavaCodeInsightFixtureTestCase() {

    private fun addBeans() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("""
            package com.ae.x;
            import org.springframework.stereotype.Service;
            @Service public class A {
              private final B b; private final C c;
              public A(B b, C c){ this.b=b; this.c=c; }
              public void entry(){ helper(); b.run(); }   // helper 私有穿透;b.run 组件节点
              private void helper(){ c.calc(); "x".length(); }  // c.calc 组件;String.length 滤掉
            }""")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class B { public void run(){} }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class C { public void calc(){} }")
    }

    fun testWalksComponentCallsSkipsLibAndFollowsPrivateHelper() {
        addBeans()
        val a = myFixture.findClass("com.ae.x.A")
        val entry = a.findMethodsByName("entry", false).first()

        val root = StaticCallGraphWalker("com.ae.x").walk(entry)

        // entry 的直接子节点应是 B.run 与 C.calc(helper 被穿透、String.length 被滤)
        val childSigs = root.children.map { it.signature }
        assertTrue(childSigs.any { it.contains("B#run") })
        assertTrue(childSigs.any { it.contains("C#calc") })
        assertEquals(2, root.children.size)
        assertTrue(root.signature.contains("A#entry"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败** — `./gradlew :threadmap-intellij:test --tests "com.threadmap.intellij.psi.StaticCallGraphWalkerTest"`

- [ ] **Step 3: 实现 walker**
```kotlin
package com.threadmap.intellij.psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTreeUtil
import com.threadmap.core.trace.TraceNode   // S2-Step0 以实际包名为准

private val STEREOTYPES = listOf(
    "org.springframework.stereotype.Service", "org.springframework.stereotype.Component",
    "org.springframework.stereotype.Controller", "org.springframework.web.bind.annotation.RestController",
)
private val REPOSITORY = "org.springframework.stereotype.Repository"

/** 从入口 PsiMethod 静态走出与 trace 同构的调用树(应用 NodePolicy,穿透 helper/getter/库,深度+环路保护)。 */
class StaticCallGraphWalker(private val basePackage: String, private val maxDepth: Int = 12) {

    fun walk(entry: PsiMethod): TraceNode {
        val id = intArrayOf(0)
        val node = newNode(entry, id)
        collect(entry, node, 0, id, HashSet())
        return node
    }

    /** 把 method 体内的组件调用收成 parent 的子节点;FOLLOW_THROUGH 的目标就地内联走进去。 */
    private fun collect(method: PsiMethod, parent: TraceNode, depth: Int, id: IntArray, onPath: MutableSet<String>) {
        if (depth >= maxDepth) return
        val body = method.body ?: return
        for (call in PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)) {
            val callee = call.resolveMethod() ?: continue
            when (NodePolicy.decide(target(callee, method))) {
                NodeDecision.INCLUDE -> {
                    val impl = resolveImpl(callee) ?: callee     // S3 覆盖接口→实现
                    val sig = signatureOf(impl)
                    if (!onPath.add(sig)) { parent.addChild(newNode(impl, id)); continue }  // 环:成叶
                    val child = newNode(impl, id)
                    collect(impl, child, depth + 1, id, onPath)
                    onPath.remove(sig)
                    parent.addChild(child)
                }
                NodeDecision.LEAF -> parent.addChild(newNode(callee, id))
                NodeDecision.FOLLOW_THROUGH -> {
                    val sig = signatureOf(callee)
                    if (onPath.add(sig)) { collect(callee, parent, depth, id, onPath); onPath.remove(sig) }
                }
                NodeDecision.SKIP -> {}
            }
        }
    }

    private fun newNode(m: PsiMethod, id: IntArray): TraceNode =
        TraceNode(id[0]++, signatureOf(m), fileOf(m), lineOf(m))

    /** com.pkg.Cls#method(Param1, Param2) */
    private fun signatureOf(m: PsiMethod): String {
        val cls = m.containingClass?.qualifiedName ?: "?"
        val params = m.parameterList.parameters.joinToString(", ") { it.type.presentableText }
        return "$cls#${m.name}($params)"
    }

    private fun fileOf(m: PsiMethod): String =
        m.containingFile?.virtualFile?.let { vf ->
            m.project.basePath?.let { base -> vf.path.removePrefix("$base/") } ?: vf.name
        } ?: ""

    private fun lineOf(m: PsiMethod): Int {
        val file = m.containingFile ?: return 0
        val doc = PsiDocumentManager.getInstance(m.project).getDocument(file) ?: return 0
        return doc.getLineNumber(m.textOffset) + 1
    }

    /** 由 PsiMethod 组装 NodePolicy 的输入。 */
    private fun target(callee: PsiMethod, caller: PsiMethod): CallTarget {
        val cls = callee.containingClass
        val fqn = cls?.qualifiedName
        val inBase = fqn?.startsWith(basePackage) == true
        val iface = cls?.isInterface == true
        val comp = cls != null && STEREOTYPES.any { AnnotationUtil.isAnnotated(cls, it, 0) }
        val repoPort = cls != null && (
            AnnotationUtil.isAnnotated(cls, REPOSITORY, 0) ||
            (iface && cls.name?.let { it.endsWith("Repository") || it.endsWith("Port") || it.endsWith("Client") || it.endsWith("Gateway") } == true)
        )
        val getter = PropertyUtilBase.isSimplePropertyGetter(callee) ||
            PropertyUtilBase.isSimplePropertySetter(callee) || callee.name == "builder" || callee.name == "build"
        val ownPrivate = cls == caller.containingClass &&
            (callee.hasModifierProperty(PsiModifier.PRIVATE) || (!comp && !callee.hasModifierProperty(PsiModifier.PUBLIC)))
        return CallTarget(fqn, inBase, iface, comp, repoPort, getter, ownPrivate)
    }

    /** S2 占位:接口不解析(返回 null,调用方退回用接口本身)。S3 覆盖。 */
    protected open fun resolveImpl(callee: PsiMethod): PsiMethod? = null
}
```
注:`resolveImpl` 在 S2 先返回 null(用接口方法本身),S3 再实现真正解析;为可覆盖,改成 `open class` 或把 `resolveImpl` 抽成构造注入的策略(实现者择一,保持 S3 能替换)。

- [ ] **Step 4: 跑测试确认通过**(如取舍边界与断言不符,**以 NodePolicy 语义为准**调断言/`target()`,不要放宽过滤)
- [ ] **Step 5: 提交** — `git commit -m "feat(intellij): StaticCallGraphWalker — PSI walk to trace tree with bean-level filter"`

---

## Task S3: 接口 → 实现解析

**Files:** Modify `StaticCallGraphWalker.kt`;Test 同 `StaticCallGraphWalkerTest.kt` 加用例。

- [ ] **Step 1: 加失败测试**
```kotlin
    fun testResolvesSingleImplementationOfInterface() {
        myFixture.addClass("package org.springframework.stereotype; public @interface Service {}")
        myFixture.addClass("package com.ae.x; public interface P { void go(); }")
        myFixture.addClass("package com.ae.x; import org.springframework.stereotype.Service; @Service public class PImpl implements P { public void go(){} }")
        myFixture.addClass("""
            package com.ae.x; import org.springframework.stereotype.Service;
            @Service public class U { private final P p; public U(P p){this.p=p;} public void run(){ p.go(); } }""")
        val u = myFixture.findClass("com.ae.x.U")
        val root = StaticCallGraphWalker("com.ae.x").walk(u.findMethodsByName("run", false).first())
        // p.go() 接口调用应解析到 PImpl#go
        assertTrue(root.children.single().signature.contains("PImpl#go"))
    }
```

- [ ] **Step 2: 跑确认失败**(当前 resolveImpl=null → 节点是 `P#go` 而非 `PImpl#go`)

- [ ] **Step 3: 实现解析**(替换 S2 的占位 `resolveImpl`)
```kotlin
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch

    /** 接口方法 → 范围内唯一实现的对应方法;0 或多实现返回 null(调用方退回接口节点,可标多实现)。 */
    private fun resolveImpl(callee: PsiMethod): PsiMethod? {
        val cls = callee.containingClass ?: return null
        if (!cls.isInterface) return null
        val impls = ClassInheritorsSearch.search(cls, GlobalSearchScope.projectScope(cls.project), true)
            .findAll().filter { it.qualifiedName?.startsWith(basePackage) == true && !it.isInterface }
        val only = impls.singleOrNull() ?: return null
        return only.findMethodsBySignature(callee, true).firstOrNull()
    }
```
(多实现 `singleOrNull()==null` → 返回 null,节点保留为接口;给接口节点签名可加后缀标记"多实现"——实现者在 `newNode` 处按 `impls.size>1` 加 `★多实现` 之类轻标,非阻塞。)

- [ ] **Step 4: 跑确认通过**(两个 walker 测试都过)
- [ ] **Step 5: 提交** — `git commit -m "feat(intellij): resolve single interface impl in static walk"`

---

## Task S4: 右键 Action + 即时渲染

**Files:** Create `psi/ShowChainAction.kt`;Modify `ui/ThreadmapPanel.kt`、`META-INF/plugin.xml`。无单测(UI/PSI 动作),runIde 手验。

- [ ] **Step 1: ThreadmapPanel 加渲染入口**
在 `ThreadmapPanel` 加 public 方法(复用现有 `showTree`/字段;`currentTree` 等已存在):
```kotlin
    /** 由静态 walker 等外部来源直接渲染一棵已构建的 AnnotatedTree。 */
    fun renderStaticTree(tree: AnnotatedTree) {
        currentTree = tree
        selectedSignature = tree.root.signature
        showTree(tree, selectedSignature)
    }
```

- [ ] **Step 2: ShowChainAction**
```kotlin
package com.threadmap.intellij.psi

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.trace.TraceNode          // 实际包名为准
import com.threadmap.intellij.ui.ThreadmapPanel     // 取到工具窗里的 panel 实例(见下注)

class ShowChainAction : AnAction("看这条链") {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = methodAt(e) != null
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val method = methodAt(e) ?: return
        val base = guessBasePackage(method)
        val traceRoot = ReadAction.compute<TraceNode, RuntimeException> {
            StaticCallGraphWalker(base).walk(method)
        }
        val tree = AnnotatedTree(traceRoot.signature, java.time.Instant.now().toString(), toAnnotated(traceRoot))
        // 打开/聚焦脉络工具窗并渲染(panel 获取见下注)
        openThreadmapAndRender(project, tree)
    }

    private fun methodAt(e: AnActionEvent): PsiMethod? {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val el = file.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(el, PsiMethod::class.java)
    }

    private fun toAnnotated(n: TraceNode): AnnotatedNode {
        val a = AnnotatedNode(n.id, n.signature, n.file, n.line, 0)
        n.children.forEach { a.addChild(toAnnotated(it)) }
        return a
    }

    private fun guessBasePackage(m: PsiMethod): String {
        val fqn = m.containingClass?.qualifiedName ?: return ""
        val parts = fqn.split('.')
        return if (parts.size >= 3) parts.take(3).joinToString(".") else fqn.substringBeforeLast('.')
    }
}
```
**注(实现者解决):** 取到当前工具窗里的 `ThreadmapPanel` 实例 + 打开工具窗,有两种正路:(a) `ThreadmapToolWindowFactory` 里把创建的 `ThreadmapPanel` 存进一个 `project` 级 service / 注册到 `ToolWindow` 的 content user-data,action 取出后 `renderStaticTree`;(b) action 直接 `ToolWindowManager.getInstance(project).getToolWindow("脉络")?.show{...}` 后从其 content 拿 panel。择 (a)(干净)。`guessBasePackage` 是粗启发(取前 3 段包名),实现者可换成读已有配置/项目根包。

- [ ] **Step 3: plugin.xml 注册右键**
```xml
<actions>
  <action id="threadmap.ShowChain" class="com.threadmap.intellij.psi.ShowChainAction" text="看这条链">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
  </action>
</actions>
```

- [ ] **Step 4: clean 编译** — `./gradlew :threadmap-intellij:clean :threadmap-intellij:compileKotlin`(预期 BUILD SUCCESSFUL)
- [ ] **Step 5: 提交** — `git commit -m "feat(intellij): right-click 'show this chain' — static walk renders into tool window"`

---

## Task S5: 标注复用(写 trace + 接现有管线)

**Files:** Modify `ShowChainAction.kt`(渲染同时落 `static-trace.json`)。

- [ ] **Step 1: 渲染后写 trace.json**(用 core 的 `Trace` + `TraceJsonWriter`,与运行时 trace 同构,供现有 `ThreadmapCli` 标注)
在 `actionPerformed` 渲染后加:
```kotlin
        val base = project.basePath ?: return
        val out = java.nio.file.Path.of(base, ".threadmap", "static-trace.json")
        java.nio.file.Files.createDirectories(out.parent)
        java.nio.file.Files.writeString(out,
            com.threadmap.core.trace.TraceJsonWriter().toJson(
                com.threadmap.core.trace.Trace(traceRoot.signature, java.time.Instant.now().toString(), traceRoot)))
```
(类名/包以 core 实际为准。)

- [ ] **Step 2: 文档化标注步骤**
在工具窗空标注态或 README 注明:`./gradlew :threadmap-core:runCli --args="<项目>/.threadmap/static-trace.json <项目>/.threadmap/annotated-tree.json <基础包> <项目>/app/src/main/java"` → 刷新即带 AI。(插件内一键标注留作后续,LLM 不进插件。)

- [ ] **Step 3: clean 编译 + 提交** — `git commit -m "feat(intellij): write static-trace.json so static chain feeds the existing annotation pipeline"`

---

## Task S6: runIde 端到端验收 + 打磨

- [ ] **Step 1:** `./gradlew :threadmap-intellij:clean :threadmap-intellij:runIde`,在 AECLAW 打开 `NegotiationService`,在 `submitRound` 方法上右键「看这条链」。
- [ ] **Step 2: 验收清单**
  - 工具窗打开,图/树显示 `submitRound` → check/calculate/route + 仓储/端口叶子(`*Repository.save`、`complianceCenterPort.*`)。
  - getter/setter、`UUID/BigDecimal`、私有 helper **不**成节点(helper 内的组件调用挂到外层)。
  - 与动态采的那张对比:静态应**更全**(多了 DB/外部叶子),结构一致。
  - 跑一次 S5 的 runCli 标注 → 刷新 → 摘要/状态/副作用补上。
  - 接口若多实现:节点保留为接口 + 轻标。
- [ ] **Step 3:** 按观感调过滤/深度(`maxDepth`、repo/port 命名启发),改后 `clean` 再 `runIde`;`git commit -m "polish(intellij): static walk filter/depth per runIde review"`

---

## 自检
- **spec 覆盖:** §2 过滤→S1+S2(`target()`+NodePolicy);接口解析§2→S3;§3 输出 trace 同构+复用→S2(TraceNode)+S5(trace.json);§4 入口右键→S4;§5 测试→S1 纯逻辑+S2/S3 fixture+S6 手验。全覆盖。
- **占位扫描:** S1 全代码;S2/S3 给出真实 PSI API 与可跑结构;S4/S5 给出 action/注册/写盘真实代码。两处显式留给实现者的判断(panel 取实例方式、guessBasePackage 启发)已写明正路与默认,非"TODO"。
- **类型一致:** `NodeDecision`/`CallTarget`(S1)→ `target()`/`collect()`(S2)一致;`TraceNode`(core)贯穿 S2/S4/S5;`resolveImpl` S2 占位 → S3 替换,签名一致;`AnnotatedTree`/`AnnotatedNode` 复用现有。
- **风险点:** ① core 的 TraceNode/Trace/TraceJsonWriter 实际包名(S2-Step0 先核);② `LightJavaCodeInsightFixtureTestCase` 需要 `com.intellij.java` 测试夹具,确认 test classpath 有(已 `bundledPlugin("com.intellij.java")`);③ FOLLOW_THROUGH 跨类内联可能走进体量大的方法,`maxDepth` + onPath 兜底,S6 据观感收。
