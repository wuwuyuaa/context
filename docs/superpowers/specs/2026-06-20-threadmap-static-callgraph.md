# 脉络 — 静态调用链构建器(Static Call-Graph)设计

> 关联:`2026-06-16-...-plugin-design.md`(整体)、`2026-06-19-...-callgraph-view.md`(图视图,本特性复用它)。

## 0. 背景与核心架构

**产品形态收敛(经多轮 brainstorm):**
- **静态优先**:"接管一个项目、先把核心链路弄清楚"是本工具的命脉用例。该场景下动态(运行时 trace)有"鸡生蛋"硬伤——要采集必先能跑起来,而接管者恰恰还不会跑。**静态只要源码**,零负担、永远反映当前代码,负担 ≈ IDE 的 Call Hierarchy,价值多了 AI 标注 + 图 + 副作用/掌握状态。
- **动态冻结为可选精确逃生口**:已建好的 AOP 采集管线不删、不再投入;未来只在"静态对接口多态认不准"时,就地冒出"采一次真实运行看跑哪个实现"。本期**不做**动态 UI。

**核心架构(关键):**
> 静态构建器是**第二个 trace 生产者**——用 PSI 静态走出**与运行时 AOP 同形**的调用树(`signature / file / line / children`)。**下游(包名折叠 → LLM 标注 → 图/树/详情/进度持久化)整套原样复用,零改动。** 运行时 AOP 是第一个生产者(冻结);用户选数据源,标注与 viewer 不关心来源。

## 1. 范围 / 非目标

**做:**
- PSI 从一个入口方法静态走调用链(**过滤规则 + 接口实现解析**)→ 产出 trace 形调用树。
- **即时渲染结构**(无 LLM,秒级):插件直接用走出的结构建 `AnnotatedTree`(`annotation=null`)→ 喂现有树/图/详情 viewer。
- **AI 标注 opt-in**:对该静态树跑**现有**标注管线 → `annotated-tree.json` → 重载;LLM **不进插件**。
- 入口:在方法上右键「看这条链」。

**非目标(本期):**
- 动态采集 UI(冻结)。
- 入口发现/覆盖清单(后续小增量)。
- 接口多实现的"运行时消歧逃生口"(只留 hook,不实现)。
- 跨链全局图、数据流标注。

## 2. 过滤规则(命门——决定图干不干净)

静态从方法往下走会爆炸(JDK/getter/Lombok/库)。**复刻动态那套赢了的过滤器 = Bean 级粒度**(只看组件间调用),静态化推导。

**成为节点(收):**
- **基础包内**(可配,如 `com.ae.negotiation`)、带 `@Service/@Component/@Controller/@RestController/@Repository` 语义的类的方法 —— **递归走进去**。
- **仓储 / 端口 / 外部接口的调用 → 叶子节点**(`roundRepository.save`、`sellerCenterPort.fetch…`):DB写/外部 的边界。**静态在这点更丰富**——动态当年把它们 mock 掉了、图里没有。
- 入口方法本身(根)。

**穿过去、不成节点(滤,但要跟进解析其内部组件调用,归到外层 public 方法):**
- 私有 / 自身(同实例)helper —— 同 Spring AOP:不单独成节点,走进去把它的组件调用挂到外层。
- 实体/DTO 的 getter / setter / builder(POJO,非组件)。
- JDK / 三方库(String/Optional/Stream/BigDecimal/UUID/Jackson…)。

**旋钮:**
- 范围 = 基础包,可配(沿用标注用的 includePackage)。
- 最大深度上限 + **环路保护**(递归调用/回边)。

**多态(静态唯一判断点):**
- 接口调用 → 在基础包范围内**只有一个实现 → 直接解析为该实现**。
- **多实现 → 画成接口节点 + 标记"多实现"**(并埋 hook:未来此处给"采一次真实运行"逃生口)。

## 3. 输出与下游复用

- **产出 = 与现有 trace 同构**的调用树(`TraceNode`:id/signature/file/line/children)。即时在内存建 `AnnotatedTree`(`annotation=null`、`understanding=UNKNOWN`)→ 现有 viewer 已能渲染(签名/`—`摘要/未知状态)。
- 同时把该树写到 `项目根/.threadmap/static-trace.json`(trace 同构格式)。
- **标注(opt-in 动作「标注此链」):** 对 `static-trace.json` 跑**现有**标注管线(`ThreadmapCli` / `AnnotationPipeline` + `MethodSourceExtractor` + Qwen)→ `annotated-tree.json` → 插件重载。**复用 §2026-06-19 callgraph-view 的图 + 详情 + 进度。**
- LLM 始终在 core/CLI 离线侧,不进插件(维持插件轻量、不接 API key)。

## 4. 入口 UX

- 在方法标识符上**右键 → 「看这条链」**(`AnAction`,启用条件:光标在一个 PSI 方法上)。触发:静态走 → 即时渲染 → 打开/聚焦「脉络」工具窗(默认图视图)。
- 工具窗内可一键「标注此链」补 AI。
- **后续(非本期):** 基础包内入口发现清单(controller/service)+ 覆盖标记。

## 5. 测试策略

- **过滤分类规则**抽成**纯逻辑可测**:`NodePolicy`——输入(目标类的包/注解/是否接口/方法是否 getter|private)→ 输出 `INCLUDE_NODE | FOLLOW_THROUGH | LEAF | SKIP`。JUnit 全覆盖,不依赖 PSI。
- **PSI 静态走**:用 IntelliJ 轻量 fixture(`LightJavaCodeInsightFixtureTestCase`),喂几个代表性源码(组件调组件、穿 helper、调 getter、接口单/多实现、递归),断言走出的树形与节点取舍正确。
- **端到端**:runIde 手验——右键真实方法 → 图渲染、过滤干净、标注可补。

## 6. 里程碑

- **S1** `NodePolicy` 分类规则(纯逻辑 + 单测)。
- **S2** PSI 静态走 `StaticCallGraphWalker`:从 `PsiMethod` 走出 `TraceNode` 树(应用 NodePolicy、穿透 helper/getter、深度+环路),fixture 测。
- **S3** 接口实现解析(单实现解析 / 多实现标记),fixture 测。
- **S4** 右键 `AnAction` + 即时渲染接线(建 AnnotatedTree → 现有 viewer + 写 static-trace.json)。
- **S5** 「标注此链」复用现有标注管线 → 重载。
- **S6** runIde 端到端验收 + 过滤/布局打磨。

## 7. 已定决策

- 静态 = **第二 trace 生产者**,下游标注/图/详情/进度**全复用**。
- 过滤**同动态(Bean 级)**;仓储/端口作叶子(比动态更全)。
- 结构**即时无 LLM**;标注 **opt-in 复用现有离线管线**,LLM 不进插件。
- 多态:**单实现解析 / 多实现标记 + hook**;运行时消歧逃生口本期冻结。
- 动态采集冻结、不删不投。
