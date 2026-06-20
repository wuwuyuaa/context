# 脉络 Threadmap 插件

渲染调用链路 + AI 标注 + 接管进度。

## 静态链路 → AI 标注

1. 在任意方法标识符上右键 →「看这条链」。插件用 PSI 静态走出调用链(Bean 级过滤),在「脉络」工具窗即时渲染结构(此时尚无 AI,节点为「未知 / —」),并把调用树落到 `项目根/.threadmap/static-trace.json`。
2. (可选)补 AI 标注 —— 在引擎仓库执行现有离线管线:

   ```bash
   # 设了 DASHSCOPE_API_KEY 用真实 Qwen;不设则离线 FakeAnnotator
   DASHSCOPE_API_KEY=<你的key> ./gradlew :threadmap-core:runCli \
     --args="<项目>/.threadmap/static-trace.json <项目>/.threadmap/annotated-tree.json <基础包> <项目>/<源码根>"
   ```

   产物 `annotated-tree.json` 落在 `项目根/.threadmap/` 下。
3. 回到工具窗点「刷新」,即带上摘要 / 掌握状态 / 副作用。LLM 只在离线引擎侧,不进插件。
