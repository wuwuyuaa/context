# Threadmap M3 —— IntelliJ 插件实现设计

> 承接总设计 [2026-06-16-threadmap-intellij-plugin-design.md](2026-06-16-threadmap-intellij-plugin-design.md)(§3 架构、§5 UI、§9 范围、§10 决策)。本文档只补 **M3 的实现结构与切分**,产品形态以总设计为准,不重复。

## 1. 目标 & 承接

M3 = 把 core 引擎产出的 `annotated-tree.json` 渲染成 IntelliJ 右侧停靠工具窗口「脉络 (Threadmap)」:列式可折叠调用树 + 状态色条 + 节点详情 + 签名感知跳源码 + 接管进度 + 待查清单导出。

承接已就绪的 core(M1–M4 已完成、已在 AECLAW 真实链路验证):`AnnotatedTree`/`AnnotatedNode` 数据模型、`AnnotatedTreeJsonWriter`(写 `annotated-tree.json`)、`SignatureFormatter` 的签名格式 `FQCN#method(简单参数类型)`。

**方案 A(渲染器优先)**:M3 v1 只渲染已有的 `annotated-tree.json`,不在 IDE 内触发采集(那是 v1.1/M3.5)。

## 2. 架构与分层

新增 gradle 模块 `threadmap-intellij`(Kotlin),依赖 `threadmap-core` 为库。核心原则:**可测逻辑核心 vs 薄平台壳**——平台绑定(ToolWindow、Swing、PSI、VirtualFile)尽量薄,能抽成纯函数的逻辑放可单测的 Kotlin 类。

```
threadmap-core (既有, Java)
  └─ 新增 AnnotatedTreeJsonReader   # 对称于 writer,反序列化 annotated-tree.json → AnnotatedTree。JVM 可测。
threadmap-intellij (新增, Kotlin)
  ├─ model/                         # 可测逻辑核心(无 IntelliJ 依赖,纯 JVM)
  │   ├─ TreeTableModelMapper       # AnnotatedTree → 列式行模型(签名/摘要/状态/副作用 + 缩进 + 折叠)
  │   ├─ ProgressStore              # .threadmap/progress.json 读写 + 按 signature 合并进 AnnotatedTree
  │   └─ TodoExporter               # dig_worthy 节点 → todo.md 文本
  ├─ ui/                            # 薄 Swing 壳
  │   ├─ ThreadmapToolWindowFactory # 注册工具窗(plugin.xml extension)
  │   ├─ ThreadmapPanel             # 主面板:TreeTable + 详情面板 + 待查面板 + 工具栏
  │   ├─ CallTreeTable              # ColumnTreeBuilder/TreeTable 列式树
  │   ├─ StatusStripeRenderer       # 左侧状态色条 + 列渲染
  │   └─ NodeDetailPanel            # 选中节点详情(单滚动面板)
  └─ action/                        # 加载 trace、跳源码、过滤、导出 等 AnAction
```

**关键:插件不自定义读模型**,复用 core 的 `AnnotatedTree`/`AnnotatedNode`,经 core 的 `AnnotatedTreeJsonReader` 加载。读写同源,schema 不漂移。

## 3. 数据流

```
.threadmap/annotated-tree.json
  → AnnotatedTreeJsonReader (core)        → AnnotatedTree
  → ProgressStore.merge(tree, progress)   → 把 .threadmap/progress.json 的 understanding 按 signature 覆盖回树
  → TreeTableModelMapper.map(tree)         → 列式行模型(缩进/折叠/色)
  → CallTreeTable 渲染                      → 工具窗
用户标记掌握状态
  → ProgressStore.update(signature, state) → 写回 .threadmap/progress.json(按方法,同签名各处一致)
```

进度与树**分离存储、按 signature 关联**(总设计 §4 约束):重生成树不丢进度。

## 4. M3a —— 骨架 + 列式树 + 状态色条

**IN:**
- gradle 模块 + IntelliJ Platform Gradle Plugin 2.x + `plugin.xml`,注册右侧停靠工具窗「脉络 (Threadmap)」。
- core 侧 `AnnotatedTreeJsonReader`(TDD,JVM 测:读 writer 产出能 round-trip 出等价 `AnnotatedTree`,含 collapsed/understanding/annotation 字段)。
- `TreeTableModelMapper`(TDD,JVM 测:树 → 行模型,折叠节点缩进/灰显标志、状态色映射)。
- `CallTreeTable`:用 IntelliJ `ColumnTreeBuilder`(或 `TreeTable`)做 `签名 | 摘要 | 状态 | 副作用` 四列,树折叠在签名列;`StatusStripeRenderer` 画左侧 4px 状态色条(灰=unknown/黄=half/绿=mastered/红=risky)、被折叠的工具类节点灰显、待查节点签名后缀 ★。
- 一个「加载 Trace」action:选 `.threadmap/annotated-tree.json` 加载渲染;模块启动时若工作区已有该文件则自动载入。
- 等宽字体、Darcula 适配。

**验证:** core reader + mapper 走 `gradle test`;工具窗/树/色条渲染走 `runIde` 手验(加载 M4 产出的真实 AECLAW `annotated-tree.json` 看渲染)。

## 5. M3b —— 详情面板 + 跳源码

- `NodeDetailPanel`:选中树节点 → 单个可滚动面板,显示完整签名、位置 `file:line`、摘要、输入/输出、副作用列表、**证据表格(文件 | 行号区间 | 关键被调方法)**、待查理由(若 `dig_worthy`)。
- 跳源码:双击/Enter/右键 → **优先 `signature` 经 PSI 反查方法**(`JavaPsiFacade`/`PsiClass.findMethodsByName`,抗行号漂移)→ 退回记录的 `file:line` → 再退回文件头 + 非阻塞提示"源码可能已变动"。签名解析(FQCN/方法名/参数)抽成可测纯函数。

## 6. M3c —— 进度 + 待查 + 导出 + 过滤

- 详情面板加掌握状态选择器(未知/半懂/已掌握/高风险)→ `ProgressStore` 写 `.threadmap/progress.json`(按 signature)。
- `ProgressStore`(TDD,JVM 测:合并 / 更新 / 同签名一致 / 重生成树保留进度)。
- 待查清单独立面板/标签:计数角标 `待查清单(N)`、列所有 `dig_worthy` + 理由、点击定位树节点、导出 `todo.md`(`TodoExporter` 可测)。
- 工具栏:展开/折叠全部、按掌握状态过滤、只看待查、文本过滤(方法/类名)。

## 7. 可测策略

| 层 | 怎么测 |
|---|---|
| `AnnotatedTreeJsonReader`(core) | JVM 单测:writer→reader round-trip |
| `TreeTableModelMapper` / `ProgressStore` / `TodoExporter` / 签名解析 | JVM 单测(无 IntelliJ 依赖) |
| ToolWindow / TreeTable / renderer / PSI 跳转 | `runIde` 手验(加载真实 AECLAW json);必要时少量 `BasePlatformTestCase` fixture 测 |

平台壳保持薄,使绝大部分逻辑落在第一、二行(可自动化)。

## 8. 技术栈 & 待定项决议

- 语言 Kotlin;构建 Gradle + **IntelliJ Platform Gradle Plugin 2.x**(plugin id `org.jetbrains.intellij.platform`,精确版本于 writing-plans 时锁定最新 2.x)。
- **基线 IDE:IntelliJ IDEA Community 2024.2+**(since-build 242,覆盖 2024.2→2025.x)。
- 列式树用 IntelliJ 平台自带 `ColumnTreeBuilder` / `TreeTable`(`com.intellij.ui.dualView` / `treeStructure.treetable`)——精确 API 于 writing-plans 时核对当前平台版本。
- **`.threadmap/progress.json` 默认 gitignore**(个人接管进度);团队共享放后续。
- core 与插件同为 JVM,插件 gradle 模块 `implementation project(':threadmap-core')`。

## 9. 范围(YAGNI)

- **IN(v1 = M3a+M3b+M3c):** 加载 json、列式可折叠树 + 状态色条、详情面板、签名感知跳源码、进度持久化、待查清单 + 导出 todo、Darcula。
- **OUT(往后):** IDE 内触发采集(v1.1/M3.5);inline 内联展开、多 trace 对比、过时检测、节点搜索/笔记(v2);MCP/团队共享/VSCode(v3+)。
- **Non-goals:** 不做静态全量图、不做 profiler、只 Java/Spring。
