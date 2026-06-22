# 脉络 (Threadmap)

**Spring 项目接管助手** —— 一个 IntelliJ IDEA 插件。从 HTTP 入口静态走出**一条**调用链,在工具窗里把它读懂:谁调谁、碰了哪些库 / 外部 / 消息、跨没跨事务,并可一键用 LLM 为每个方法生成基于源码的摘要。

> 适合「接手一个不熟悉的 Spring 服务、要快速搞清某个接口背后发生了什么」的场景。

## 能做什么

- **入口清单**:工具窗打开即列出项目的 HTTP 端点;点一个,静态走出它的调用树(穿透接口到实现)。
- **链路可信度**:每条边标明来源 —— 确定调用 / 单实现推断 / 多实现不确定 / 未解析,**不把推断冒充真实路径**。
- **结构标签(无需 LLM、即时)**:Spring 语义(事务 / 异步 / 重试 / 缓存 / 定时 / 鉴权)+ 数据副作用(DB 读写 / 外部 API / 消息)。
- **一键 AI 标注**:基于**方法源码**为每个节点生成摘要 + 风险 + 证据;失败明确报错,**绝不伪造**;按源码 hash 磁盘缓存,跨链路复用省 token。
- **接管阅读**:「已掌握 N/M」进度 +「下一个待读」逐节点走完;源码改了自动标「可能过期」。

## 使用

1. 安装插件(`./gradlew :threadmap-intellij:buildPlugin` 产出 `build/distributions/*.zip` → IDE 里 *Install Plugin from Disk*)。
2. 打开右侧 **脉络 (Threadmap)** 工具窗 → 点一个入口走链。
3. 想要 AI 摘要:**Settings → Tools → 脉络 Threadmap** 选服务商、填 API Key(存 IDE 安全库),再点工具栏 **「标注主干」**。

支持任意 OpenAI 兼容服务商:通义 / DeepSeek / Kimi / 智谱 / OpenAI / Ollama 等。

## 🔒 隐私

- **不点「标注」时,全程本地、零外发** —— 走链、结构标签都只用本地 PSI。
- **点「标注」时**,会把**所选方法的源码**发送到你在设置里配置的 LLM 服务商,用于生成摘要。请确认你有权把相关代码发送给该服务商。
- API Key 存于 IDE 安全库(PasswordSafe),不写入任何明文文件。

## 构建

```bash
./gradlew :threadmap-core:test :threadmap-intellij:test   # 测试
./gradlew :threadmap-intellij:runIde                       # 沙箱里跑插件
./gradlew :threadmap-intellij:buildPlugin                  # 打包
```

## 许可

[MIT](LICENSE)。
