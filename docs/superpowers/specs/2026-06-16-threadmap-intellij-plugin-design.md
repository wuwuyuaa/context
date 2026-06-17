# 脉络 (Threadmap) — IntelliJ 插件形态设计

- 日期:2026-06-16
- 状态:设计已对齐,待用户最终评审 → 进入实现计划
- 关联:产品设计文档 v0.2(存于 Mem 知识库「脉络 Threadmap」集合)。本 spec 是在 v0.2 基础上的**产品形态转向**:主交付形态从「CLI + 离线 HTML」改为「IntelliJ 插件」。

---

## 0. 本设计的范围与不变量

**本 spec 覆盖:** 脉络 IntelliJ 插件 v1(及紧随的 v1.1)的产品形态、模块架构、UI、数据流与持久化、错误处理、测试策略、范围边界与里程碑。

**保持不变的内核(来自 v0.2,不因转向而动摇):**
- 脊梁:**动态单链路,不做静态全量**。
- AI 标注**只说"做了什么"、必须绑定证据、禁止空话**。
- 产品灵魂:**待查清单 + 接管进度 + 绑证据标注**——这是把"trace 查看器"变成"接管工具"的东西。
- Non-goals 不变:不做静态全量调用图、不做 profiler、只支持 Java/Spring。

---

## 1. 产品定位(简述)

脉络是一个**代码接管工具**,不是代码可视化工具。它解决的问题是:用 coding agent 生成大量代码后,"脑子里只有功能地图、没有实现地图"的失控感。可视化(导图)只是手段,目的是让人**沿一条真实请求链路重新建立掌控**:读懂骨架 → 把没懂的记进待查 → 逐个攻克 → 标记已掌握。

目标用户:接手陌生/agent 代码、需要快速建立掌控感的中级工程师。

---

## 2. 形态决策

**主形态:IntelliJ 插件(先做)。** VSCode 列入远景(v3+)。理由:作者与目标项目(AECLAW)都是 Java/Spring,读 Java 代码的人几乎都在 IntelliJ;插件与被分析代码同处一个 IDE,跳转与触发最顺。

**为什么插件成立而不破坏既有设计:** v0.2 的三层架构(采集 → 处理 → 渲染)天生把渲染层做成可替换的。插件本质就是**另一个读 `annotated-tree.json` 的渲染器**,核心层(采集 + 标注 + 进度)完全复用。四个驱动动机里:
- A 体验一体化、C 分发、D 插件即主形态 —— 只需在 IDE 内加一个读同一份标注树的视图即可拿到。
- B 在 IDE 内触发采集 —— 是唯一**反向驱动采集层**的能力,工作量更大且与 IDE 强绑定。

**采用方案 A:渲染器优先,触发(B)作 v1.1。** 先证明"看得爽",再证明"跑得动",把最容易拖垮进度的 B 单独隔出来。

---

## 3. 架构与模块切分

**硬隔离为两个模块:**

### `threadmap-core` —— 无头引擎(Java,零 IntelliJ 依赖)
- 职责:采集适配器(测试触发默认 / java-agent 兜底 / OTel 复用)→ `trace.json`;处理(包名折叠 + 逐节点绑证据 LLM 标注 + 哈希缓存)→ `annotated-tree.json`。
- 形态:一个纯 JVM 库 + 一个薄 CLI 入口。
- 它不知道 IntelliJ 存在。这是 v0.2 的 M1+M2 的落点,**转向后原样保留**。

### `threadmap-intellij` —— 插件(Kotlin,依赖 core 为库)
- v1:读 `annotated-tree.json`,渲染原生工具窗口、跳源码、进度、待查清单。
- v1.1:调用 core 在 IDE 内触发采集(B)。

**核心原则:** 所有前端(今天的 CLI、现在的插件、未来的 VSCode / MCP Server)都只是同一个引擎的**客户端**。插件永远只是壳;引擎保持无头,可脱离 IDE 单测、可任意复用。这是整个设计的隔离骨架。

**技术栈:**
- 语言:core = Java(作者主场、长期维护的引擎、且分析对象就是 Java),插件 = Kotlin(IntelliJ 官方默认,工具窗口/Action 样板代码更少)。两边同为 JVM,互调无缝。
- 构建:Gradle + IntelliJ Platform Gradle Plugin(2.x)。
- 分发:JetBrains Marketplace(对应动机 C)。
- 基线 IDE 版本:待定(盯较新的 2024.x+,落地时定)。

---

## 4. 数据模型(承自 v0.2)

**节点字段:** `id` / `signature`(`类#方法(签名)`) / `file` / `line` / `children` / `collapsed` / `self_ms`

**标注追加字段:** `summary`(≤25字)/ `inputs` / `outputs` / `side_effects[]` / `evidence{file, lines, calls}` / `dig_worthy` / `dig_reason` / `understanding`(`unknown` | `half` | `mastered` | `risky`,用户标记)

**关键约束:** `understanding`(接管进度)**与生成的树分离存储,按 `signature` 关联**,重生成树不丢进度;且按方法记录(同一方法出现在多条 trace 中状态一致)。

---

## 5. UI 设计(v1)

**载体:** IntelliJ 停靠式工具窗口,标题「脉络 (Threadmap)」。**默认右侧停靠**(用户可拖至底部),Darcula 深色主题为主,原生 Swing 控件质感,代码/签名用等宽字体。

**布局(版本2 路线):** 树在左、详情面板在右(窄时上下排)。

**调用链路树:**
- 列式呈现:`签名 | 摘要(AI) | 状态 | 副作用`。
- 每行:`类#方法` 签名(等宽)、灰色 AI 摘要、掌握状态、副作用徽章(DB写 / 外部API / 消息)、待查节点带 ★。
- **每行左侧用状态色条**表示掌握状态(灰=未知、黄=半懂、绿=已掌握、红=高风险)——管"一眼扫进度";行内文字状态管"精确"。
- 被自动折叠的工具类节点灰显、可展开。默认全折叠 + 最大深度(由 core 折叠时定)。

**详情面板(选中节点时,单个可滚动面板,非多 tab):**
- 完整签名
- 位置 `file:line`(可点击 / Ctrl+Click 在编辑器中打开)
- 摘要(AI)
- 输入 / 输出
- 副作用列表
- **证据(表格):`文件 | 行号区间 | 关键被调方法`**
- 待查理由(若 `dig_worthy`)
- 掌握状态选择器(未知 / 半懂 / 已掌握 / 高风险)

**待查清单(独立面板/标签):** 带计数角标(如 `待查清单(1)`),列出所有 `dig_worthy` 节点 + 理由,点击定位到树节点;右上角「导出 todo.md」。

**工具栏:** 加载 Trace、刷新、全部展开/折叠、按掌握状态过滤、只看待查项、文本过滤(方法/类名)、设置。

**跳源码:** 双击 / Enter / 右键 → **优先用 `signature` 经 PSI 反查方法**(抗行号漂移)→ 退回记录的 `file:line` → 再退回文件头 + 非阻塞提示"源码可能已变动"。

**v1 明确砍掉(避免首版过重):**
- 版本3 的 inline 内联展开 → v2(深树展开推挤兄弟节点、易乱;v1 用侧边详情面板)。
- 详情面板的多 tab(概览/输入输出/副作用/证据/待查理由) → v1 用单个可滚动面板。

---

## 6. 数据流与进度持久化

**v1 数据流:**
1. 触发采集(测试默认 / agent 兜底 / OTel)→ `trace.json` …… core
2. core 处理:包名折叠 + 逐节点绑证据 LLM 标注 + 哈希缓存 → `annotated-tree.json` …… core
3. 插件加载 `annotated-tree.json` → 构建 IntelliJ 树模型 → 渲染
4. 用户读图、标记掌握状态 → 写入本地进度存储
5. 下次重新采集/重生成树时,按 `signature` 把旧进度重新贴回新树 → **进度不丢**

**进度持久化(已定):** 存于项目根目录 `.threadmap/progress.json`,按 `signature` 关联,与生成的树分离。理由:人类可读、可被任何前端复用(CLI / 插件 / 未来 VSCode、MCP 读同一份)、与"引擎 + 可替换客户端"架构一致。

**`.threadmap/` 作为项目工作目录:** 存放 `trace.json`、`annotated-tree.json`、`progress.json`。默认进 `.gitignore`;未来团队共享时可选择提交(v3+)。

**进度文件并发(v1 简单处理):** JSON 末次写入为准 + 窗口聚焦时重载。

---

## 7. 错误处理与边界情况

- **`annotated-tree.json` 缺失/损坏** → 工具窗口显示友好空状态 + "去加载 trace" 引导,不向 IDE 抛崩溃。
- **标注失败**(LLM 超时/限额)→ 该节点显示"标注失败" + 重标入口(v1 重标走 core/CLI;插件内一键重标为 v1.1);哈希缓存保证已成功节点不重复计费。
- **跳源码代码已漂移** → 见 §5 跳源码的三级回退;真正的"过时检测"(源码哈希比对、标红失效节点)放 **v2**,v1 只保证跳转不崩、能容错。
- **超大树** → 默认全折叠 + 最大深度 + 懒构建子节点,避免卡 UI。

---

## 8. 测试策略

**核心原则:能不碰 IntelliJ API 的逻辑就抽成纯类,绝大多数测试不挂 IDE 测试框架。**

- **core(Java,测试大头,纯 JUnit):** trace 解析/schema、包名折叠逻辑、标注 prompt 拼装(断言只喂"本方法源码 + 直接被调签名",LLM 用 mock)、哈希缓存命中、绑证据输出 schema 校验、**进度按 signature 合并**(重生成后标记正确贴回、孤儿签名处理)。
- **插件(Kotlin):** UI 无关逻辑(树模型构建、进度读写、signature→PSI 解析)抽纯类 → 普通单测;再用薄层 `BasePlatformTestCase` 测工具窗口建模、PSI 导航、进度持久化往返。
- **集成(对应 M4):** 拿 AECLAW 真实接口跑通 core → 插件加载 → 人工核对。

---

## 9. 范围(YAGNI)与里程碑

**v1 IN:** 加载 `annotated-tree.json`、原生可折叠列式树(含折叠 + 左侧状态色条)、节点详情面板、签名感知的跳源码、进度标记 + `.threadmap/progress.json` 持久化、待查清单面板 + 导出 `todo.md`、Darcula 样式。

**往后放:**
- **v1.1(M3.5):** IDE 内触发(B)—— run-config/gutter "Run with Threadmap"、插件内重标/刷新。
- **v2:** inline 内联展开、多 trace 对比/合并、数据流叠加、节点搜索 + 笔记、增量刷新、过时检测。
- **v3+:** 影响分析/组件依赖/风险雷达(⚠️ 仍卡在静态全量图,定位不变)、MCP Server、团队共享进度、VSCode。

**Non-goals(不变):** MVP 不做静态全量图、不做 profiler、只 Java/Spring。

**里程碑映射(转向只波及 M3):**

| 里程碑 | 状态 | 内容 |
|---|---|---|
| M1 | 不变 | 测试触发真实请求 → `trace.json`,验证调用树(Bean 级) |
| M2 | 不变 | core:包名折叠 + 绑证据标注 + 哈希缓存 → `annotated-tree.json`(+ 按签名合并进度) |
| M3 | **改** | 原"HTML 导图" → 现"**IntelliJ 工具窗口**":列式树 + 跳源码 + 进度 + 待查导出 |
| M3.5 | **新增 (v1.1)** | IDE 内一键触发(B) |
| M4 | 不变 | 拿 AECLAW 一条真实链路跑通,迭代折叠规则与标注 prompt(改在插件里看) |

---

## 10. 已定决策 & 仍待定

**已定:**
- 插件为主形态;IntelliJ 先做。
- 方案 A:渲染器优先,B(IDE 内触发)作 v1.1。
- 语言:core Java + 插件 Kotlin。
- UI:默认右侧停靠;版本2 布局 + 列式树 + 左侧状态色条 + 表格证据 + 待查独立面板;原生 IntelliJ 质感。
- 进度存 `.threadmap/progress.json`,按 signature,与树分离。
- inline 展开/详情多 tab → v2;过时检测 → v2;IDE 内触发 → v1.1。
- **M2 标注栈:LangChain4j + DashScope(Qwen)**(用户定,非 Anthropic);LLM 不可用时离线降级到 `FakeAnnotator`。

**仍待定(部分承自 v0.2):**
- 基线 IDE 版本号。
- 埋点粒度默认 Bean 级 vs 方法级(倾向 Bean 级)。
- Qwen 的确切 DashScope 模型 id(用户指 "qwen3.6flash";`qwen-flash` 为占位默认,落地核对)。
- `.threadmap/progress.json` 是否默认进 VCS(倾向默认 gitignore,团队共享时再放开)。
- **M2 签名碰撞策略**:`signature`(`类#方法(简单参数类型)`)是进度合并 / 标注缓存 / PSI 反查的关联键。当前用简单参数类型,**重载方法**或不同包同名类型参数会产生相同签名而被合并。M2 需定碰撞策略(参数用 FQN、或加 arity、或文档化唯一性假设)。

---

## 11. 实现进度

- **M1(core 追踪)已实现** —— `threadmap-core` 模块(Java/Gradle):测试触发一次真实 Spring 请求 → Bean 级 AOP 采集调用树 → `trace.json`(`entry_signature` / `captured_at` / `root{id, signature, file, line, self_ms, children[]}`)。15 个测试全绿,含一个端到端 `@SpringBootTest`(`SampleController → SampleService → SampleRepository`)。计划见 [plans/2026-06-16-threadmap-core-m1-bean-level-tracing.md](../plans/2026-06-16-threadmap-core-m1-bean-level-tracing.md)。
- **下一步:** M2 已拆为 **M2a**(处理骨架 + 离线标注,计划见 [plans/2026-06-17-threadmap-core-m2a-annotation-skeleton.md](../plans/2026-06-17-threadmap-core-m2a-annotation-skeleton.md))+ **M2b**(真实 Qwen 标注 + JavaParser 源码抽取 + 哈希缓存);随后 M3(IntelliJ 插件)。
- **M1 已知/接受的采集限制**(M2/M3 不应假设其反面):AOP 拦不到 Bean 内自调用、普通(非 Bean)对象、跨线程异步分支;`line` 恒为 0,由插件用 PSI 按 `signature` 反查。`TraceRecorder` 是单例,靠 `start()/stop()` + 测试触发/入口隔离保证"同时只录一条",多触发并发需另行设计。
