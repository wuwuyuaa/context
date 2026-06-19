# 脉络 — 调用图视图(Call Graph View)设计

> 关联:`2026-06-16-threadmap-intellij-plugin-design.md`(§5 UI)。本文是它的一个新增视图,不改动已有的树/详情/待查。

## 0. 背景与目标

用户反馈:现在的**缩进树**表达的是"包含关系"(谁套在谁里),但人理解"一条调用链"靠的是**顺序 + 数据 + 整体故事**;树里 `RuleEngineService#evaluate` 重复出现 6 次形成"墙",更看不清。

**目标:** 给同一条标注链加一个**节点图(call graph)**视图 —— 像 IntelliJ 的 Spring Beans 依赖图那样,方法是节点、调用是有向边,自动布局 + 缩放 + 拖拽。它顺手解决"重复墙":**同签名合并成一个节点**,重复调用在边上标 `×N`。

**一句话:** 把"调用链"从一棵缩进树,变成一张能一眼看清流向的图。

## 1. 范围 / 非目标

**做:**
- 方法级节点图(去重),作为左侧面板「树 / 图」的另一种视图。
- 节点带:状态色条、`类#方法`、AI 摘要、副作用药丸。
- 点节点 → 右侧详情联动;双击 → 跳源码;缩放/拖拽/框选。

**不做(本期):**
- Bean/类级图(已选方法级)。
- 边上的数据流标注(数据怎么变)—— 留待后续。
- 图内编辑/重新布局持久化。
- 链路叙事条(另一条改进线,不在本 spec)。

**基座:** 维持 `intellijIdeaCommunity` —— 自绘走 JCEF,社区版/专业版都内置,社区版编译的插件在 Ultimate 照常运行。**不切 Ultimate**(省沙箱授权;以后若需 Ultimate 独有 API 再议)。

## 2. 放置与交互

- 左侧面板由「树」单视图改为**可切换的双视图**:`CardLayout`,两张卡 —— `tree`(现有 TreeTable)、`graph`(新 GraphPanel)。
- 工具栏加一个 **「树 / 图」切换**(ToggleAction 或两段式按钮)。默认仍是树。
- 右侧「详情 / 待查清单」**不变**,两个视图共用。
- **选中联动:** 图里点节点 → 按 signature 找到 `AnnotatedNode` → 调用与树相同的 `showNodeDetails(...)`(详情面板更新)。反向(树选中→图高亮)本期不做。
- **双击节点 → 跳源码**:复用现有 `SourceNavigator.navigate(project, node)`。
- 布局:自动层次布局(调用自上而下流),支持缩放、拖拽画布、拖动节点、框选。

## 3. 数据模型:`CallGraphBuilder`(可测核心)

纯逻辑,放 `threadmap-intellij/.../model/CallGraphBuilder.kt`(与 `CallTreeNodeBuilder` 并列),Kotlin,完全单测覆盖。

```
data class GraphNode(
    val id: String,            // = signature(唯一键)
    val label: String,         // 紧凑签名 类#方法(复用 NodePresentation.compactSignature)
    val summary: String,       // AI 摘要(无则 "—")
    val status: StatusStyle,   // UNKNOWN/HALF/MASTERED/RISKY(承自 understanding)
    val sideEffects: List<String>,
    val digWorthy: Boolean,
    val file: String,
    val line: Int,
)

data class GraphEdge(
    val from: String,          // caller signature
    val to: String,            // callee signature
    val count: Int,            // 该 caller→callee 调用出现次数(×N)
)

data class CallGraph(val nodes: List<GraphNode>, val edges: List<GraphEdge>, val rootId: String)
```

**构建算法(DFS over AnnotatedTree):**
- 每个唯一 `signature` → 一个 `GraphNode`;重复出现只建一次(注解取首个非空;同签名注解一致,因 core 的 CachingAnnotator 已按签名去重)。
- 每条 `parent→child` 边按 `(fromSig,toSig)` 聚合计数;`count` = 出现次数。
- 自递归(`parentSig==childSig`)→ 自环边,正常计数。
- 根节点(入口)`rootId = tree.root.signature`,无入边。
- `status` 来自 `AnnotatedNode.understanding`(progress.json 已按签名合并,故同签名状态一致)。
- 折叠/未标注节点仍建节点:status=UNKNOWN、summary="—"、label 取签名。

**单测覆盖:** 同一 callee 在同一 caller 下出现多次→count 累加;同签名在不同 caller 下→单节点多入边;自递归→自环;空注解节点→ UNKNOWN;边/节点数量与 rootId 正确。

## 4. 节点 / 边视觉

**节点(卡片):**
- 左侧**状态色条**:灰 UNKNOWN / 黄 HALF / 绿 MASTERED / 红 RISKY(与树/详情同一套 `SideEffectStyle`/状态色)。
- 标题:`类#方法`(等宽)。
- 一行灰色 **AI 摘要**(截断,hover 看全)。
- 有副作用 → 一排**药丸**(DB写/外部API/消息…),配色复用现有规则。
- `dig_worthy` → 角标 ★。

**边:**
- 有向箭头,调用方向。
- `count>1` → 边标签 `×N`。
- 自环单独绘制。

**布局:** 层次(dagre),根在上,被调在下。

## 5. 渲染技术(JCEF + Cytoscape)

- **载体:** `com.intellij.ui.jcef.JBCefBrowser`(IDE 内嵌 Chromium)。`GraphPanel` 持有一个 browser,塞进左侧 `graph` 卡。
- **图库:** **Cytoscape.js** + **cytoscape-dagre**(层次布局)+ **dagre**;富节点卡片用 **cytoscape-node-html-label**(每个节点渲染成 HTML:色条+标题+摘要+药丸)。若 html-label 集成不顺,**降级**为 Cytoscape 原生样式节点(背景按状态着色 + 多行 label),不阻塞主路。
- **离线:** 上述 JS 全部作为插件资源(`resources/web/`)打包;加载时把 JS **内联进 HTML 字符串**,`browser.loadHTML(html)`(避免 JCEF file:// 限制 / 不连网)。
- **数据注入:** IDE 侧 `CallGraphBuilder` 产出 `CallGraph` → 序列化为 JSON(Jackson)→ `browser.cefBrowser.executeJavaScript("renderGraph(<json>)", ...)`(或直接内联进初始 HTML)。重新加载/刷新时重渲染。

## 6. IDE ↔ JS 桥

- `JBCefJSQuery`:注册一个查询处理器,JS 侧得到一个注入函数。
- 节点 **tap** → JS 调注入函数传 `signature` → IDE 收到 → 按签名找 `AnnotatedNode` → `showNodeDetails`。
- 节点 **dbltap** → 另一个查询(或带类型前缀)→ `SourceNavigator.navigate`。
- 线程:JS 回调在 IDE 侧切到 EDT 再动 UI。

## 7. 离线与降级

- `JBCefApp.isSupported()` 为 false(极少数环境)→ 「图」切换**禁用**并提示"当前 IDE 不支持内嵌浏览器,请用树视图";树视图照常。
- 浏览器加载失败 / JS 异常 → 面板显示友好错误,不影响树/详情。

## 8. 测试策略

- **`CallGraphBuilder`**:JUnit 纯逻辑全覆盖(见 §3)。这是本期唯一能自动化测的核心,必须 TDD。
- **GraphPanel / JS 渲染**:无法单测,`runIde` 手动验:节点去重正确、色条/药丸/×N 显示、点节点联动详情、双击跳源码、缩放拖拽。
- 回归:树/详情/待查不受影响(切换卡不改它们)。

## 9. 里程碑 / 任务拆分

- **G1 `CallGraphBuilder` + 单测**(纯逻辑,先 TDD)。
- **G2 GraphPanel 骨架**:JBCefBrowser + 打包的 cytoscape/dagre + HTML 模板 + `renderGraph(json)`,先渲染出"节点+边+×N"。
- **G3 富节点 + 桥**:html-label 卡片(色条/摘要/药丸/★)+ JBCefJSQuery(点节点→详情、双击→源码)。
- **G4 左侧「树/图」切换 + 接线**:CardLayout + 工具栏 ToggleAction;`isSupported` 降级。
- **G5 `runIde` 验收 + 打磨**(布局间距、节点尺寸、颜色与树一致)。

## 10. 已定决策

- 渲染:**自绘(JCEF + Cytoscape.js)**,非原生 Diagram 框架(原生节点样式塞不进色条/药丸,且 Ultimate-only)。
- 粒度:**方法级**(去重后约 8 节点),非 Bean/类级。
- 基座:**维持社区版**(JCEF 两版通用)。
- 位置:左侧**树/图切换**,共用右侧详情/待查。
- 数据流标注、链路叙事、类级图:**后续**,不在本期。
