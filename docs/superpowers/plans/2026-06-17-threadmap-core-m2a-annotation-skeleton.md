# Threadmap Core M2a — 标注处理骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `threadmap-core` 里把 M1 产出的 `trace.json` 处理成 `annotated-tree.json` —— 解析调用树、按包名/深度折叠、给每个未折叠节点附结构化标注(本阶段用离线 `FakeAnnotator`),序列化输出。

**Architecture:** 纯 JVM(Java),零网络、零外部 LLM 依赖。`Annotator` 接口隔离标注来源:本阶段提供离线 `FakeAnnotator`;M2b 再换成真实的 LangChain4j + DashScope(Qwen)实现 + JavaParser 源码抽取 + 哈希缓存。流水:`trace.json → 解析 → 折叠 → 逐节点标注 → AnnotatedTree → annotated-tree.json`。

**Tech Stack:** Java 17、Gradle(wrapper 已就绪)、Jackson(已是依赖)、JUnit 5。**本阶段不引入新依赖。**

**这是 M2 的第 1 份(共 2 份):** M2a = 处理骨架 + 离线标注(本文档);M2b = 真实 Qwen 标注 + 源码抽取 + 哈希缓存。

**M2b 已勘定的接入点(本阶段不实现,仅备忘):**
- 依赖:`dev.langchain4j:langchain4j-community-dashscope:1.9.0-beta16`(用前在 Maven Central 核对最新版)。
- `ChatModel model = QwenChatModel.builder().apiKey(key).modelName(model).temperature(0.2).maxTokens(1024).build();`(`dev.langchain4j.community.model.dashscope.QwenChatModel` → `dev.langchain4j.model.chat.ChatModel`)。
- 调用:`String reply = model.chat(prompt);`(`String chat(String)` 便捷方法)。
- API key:环境变量 `DASHSCOPE_API_KEY`。默认模型名:**`qwen3.6-flash`**(百炼/Model Studio 文本生成;模型名是配置项)。

**范围边界:**
- 不调用任何 LLM、不读源码文件:`FakeAnnotator` 从签名派生确定性占位标注。
- 不做进度合并:`understanding` 一律默认 `unknown`(进度由 M3 插件产生并在重渲染时合并)。
- 懒标:只标注**未折叠**节点;折叠(工具类)节点不标注。

---

## 文件结构

```
threadmap-core/src/main/java/com/threadmap/core/annotate/
  Understanding.java          # 创建:掌握状态枚举
  Evidence.java               # 创建:证据 record(file/lines/calls)
  Annotation.java             # 创建:AI 标注 record
  AnnotationRequest.java      # 创建:标注请求 record(signature/source/calleeSignatures)
  Annotator.java              # 创建:标注器接口
  FakeAnnotator.java          # 创建:离线占位标注器
  AnnotatedNode.java          # 创建:标注树节点(可变:折叠/标注/掌握状态)
  AnnotatedTree.java          # 创建:标注树(入口签名+时间+root)
  TraceJsonParser.java        # 创建:trace.json → 未折叠未标注 AnnotatedTree
  PackageFolder.java          # 创建:折叠规则(白名单包 + 最大深度)
  AnnotatedTreeJsonWriter.java# 创建:AnnotatedTree → annotated-tree.json
  AnnotationPipeline.java     # 创建:编排 解析→折叠→标注
threadmap-core/src/test/java/com/threadmap/core/annotate/
  FakeAnnotatorTest.java
  AnnotatedNodeTest.java
  TraceJsonParserTest.java
  PackageFolderTest.java
  AnnotatedTreeJsonWriterTest.java
  AnnotationPipelineTest.java
```

`com.threadmap.core.trace`(M1)保持不变。

---

## Task 1: 标注数据模型(Understanding / Evidence / Annotation)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/Understanding.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/Evidence.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/Annotation.java`

这些是纯数据载体(record/enum),无独立测试;由后续任务(FakeAnnotator/writer)覆盖。

- [ ] **Step 1: 创建三个类型**

`Understanding.java`:
```java
package com.threadmap.core.annotate;

/** 节点掌握状态(用户进度)。 */
public enum Understanding {
    UNKNOWN, HALF, MASTERED, RISKY
}
```

`Evidence.java`:
```java
package com.threadmap.core.annotate;

import java.util.List;

/** 标注证据:源文件、行号区间(如 "32-62")、关键被调方法。 */
public record Evidence(String file, String lines, List<String> calls) {}
```

`Annotation.java`:
```java
package com.threadmap.core.annotate;

import java.util.List;

/** 一个节点的 AI 标注:只描述"做了什么",绑定证据,禁止空话。 */
public record Annotation(
        String summary,
        String inputs,
        String outputs,
        List<String> sideEffects,
        Evidence evidence,
        boolean digWorthy,
        String digReason
) {}
```

- [ ] **Step 2: 确认编译**

Run: `./gradlew :threadmap-core:compileJava`
Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/Understanding.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/Evidence.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/Annotation.java
git commit -m "feat(core): add annotation data model" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: AnnotatedNode + AnnotatedTree

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedNode.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTree.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedNodeTest.java`

- [ ] **Step 1: 写失败测试**

`AnnotatedNodeTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedNodeTest {

    @Test
    void defaultsAndMutationsWork() {
        AnnotatedNode n = new AnnotatedNode(0, "A#a()", "A.java", 0, 12);
        assertEquals(0, n.getId());
        assertEquals("A#a()", n.getSignature());
        assertEquals(12, n.getSelfMs());
        assertFalse(n.isCollapsed());
        assertNull(n.getAnnotation());
        assertEquals(Understanding.UNKNOWN, n.getUnderstanding());

        n.setCollapsed(true);
        n.setUnderstanding(Understanding.MASTERED);
        assertTrue(n.isCollapsed());
        assertEquals(Understanding.MASTERED, n.getUnderstanding());

        AnnotatedNode child = new AnnotatedNode(1, "B#b()", "B.java", 0, 3);
        n.addChild(child);
        assertEquals(1, n.getChildren().size());
        assertSame(child, n.getChildren().get(0));
    }

    @Test
    void getChildrenIsUnmodifiable() {
        AnnotatedNode n = new AnnotatedNode(0, "A#a()", "A.java", 0, 1);
        assertThrows(UnsupportedOperationException.class,
                () -> n.getChildren().add(n));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedNodeTest'`
Expected: 编译失败(类不存在)。

- [ ] **Step 3: 实现**

`AnnotatedNode.java`:
```java
package com.threadmap.core.annotate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 标注树节点:trace 节点数据 + 折叠标记 + (可选)AI 标注 + 用户掌握状态。 */
public class AnnotatedNode {
    private final int id;
    private final String signature;
    private final String file;
    private final int line;
    private final long selfMs;
    private boolean collapsed;
    private Annotation annotation;                       // nullable: 未标注
    private Understanding understanding = Understanding.UNKNOWN;
    private final List<AnnotatedNode> children = new ArrayList<>();

    public AnnotatedNode(int id, String signature, String file, int line, long selfMs) {
        this.id = id;
        this.signature = signature;
        this.file = file;
        this.line = line;
        this.selfMs = selfMs;
    }

    public void addChild(AnnotatedNode child) { children.add(child); }
    public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
    public void setAnnotation(Annotation annotation) { this.annotation = annotation; }
    public void setUnderstanding(Understanding understanding) { this.understanding = understanding; }

    public int getId() { return id; }
    public String getSignature() { return signature; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    public long getSelfMs() { return selfMs; }
    public boolean isCollapsed() { return collapsed; }
    public Annotation getAnnotation() { return annotation; }
    public Understanding getUnderstanding() { return understanding; }
    public List<AnnotatedNode> getChildren() { return Collections.unmodifiableList(children); }
}
```

`AnnotatedTree.java`:
```java
package com.threadmap.core.annotate;

/** 一棵标注树:入口签名 + 采集时间 + 根节点。 */
public class AnnotatedTree {
    private final String entrySignature;
    private final String capturedAt;
    private final AnnotatedNode root;

    public AnnotatedTree(String entrySignature, String capturedAt, AnnotatedNode root) {
        this.entrySignature = entrySignature;
        this.capturedAt = capturedAt;
        this.root = root;
    }

    public String getEntrySignature() { return entrySignature; }
    public String getCapturedAt() { return capturedAt; }
    public AnnotatedNode getRoot() { return root; }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedNodeTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedNode.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTree.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedNodeTest.java
git commit -m "feat(core): add annotated tree model" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Annotator 接口 + FakeAnnotator

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationRequest.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/Annotator.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/FakeAnnotator.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/FakeAnnotatorTest.java`

- [ ] **Step 1: 写失败测试**

`FakeAnnotatorTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakeAnnotatorTest {

    @Test
    void derivesDeterministicAnnotationFromSignature() {
        Annotator annotator = new FakeAnnotator();
        AnnotationRequest req = AnnotationRequest.ofSignature(
                "com.example.MerchantService#recruit(RecruitCommand)");

        Annotation a = annotator.annotate(req);

        assertTrue(a.summary().contains("recruit"), "summary 应含方法名,实际:" + a.summary());
        assertNotNull(a.evidence());
        assertEquals("com/example/MerchantService.java", a.evidence().file());
        assertFalse(a.digWorthy());
        assertTrue(a.sideEffects().isEmpty());

        // 确定性:同输入同输出
        Annotation b = annotator.annotate(req);
        assertEquals(a.summary(), b.summary());
    }

    @Test
    void ofSignatureLeavesSourceAndCalleesEmpty() {
        AnnotationRequest req = AnnotationRequest.ofSignature("A#a()");
        assertEquals("A#a()", req.signature());
        assertNull(req.source());
        assertTrue(req.calleeSignatures().isEmpty());
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*FakeAnnotatorTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`AnnotationRequest.java`:
```java
package com.threadmap.core.annotate;

import java.util.List;

/**
 * 标注请求:方法签名 + (可选)方法源码 + 直接被调方法签名。
 * M2a 只用 signature;M2b 填充 source / calleeSignatures 供真实 LLM 标注。
 */
public record AnnotationRequest(String signature, String source, List<String> calleeSignatures) {
    public static AnnotationRequest ofSignature(String signature) {
        return new AnnotationRequest(signature, null, List.of());
    }
}
```

`Annotator.java`:
```java
package com.threadmap.core.annotate;

/** 把一个节点标注成结构化 Annotation。实现可为离线 Fake 或真实 LLM(M2b)。 */
public interface Annotator {
    Annotation annotate(AnnotationRequest request);
}
```

`FakeAnnotator.java`:
```java
package com.threadmap.core.annotate;

import java.util.List;

/**
 * 离线占位标注器:不调用任何外部服务,从签名派生确定性标注。
 * 用途:测试、离线模式、以及 LLM 不可用时的降级路径。
 */
public class FakeAnnotator implements Annotator {

    @Override
    public Annotation annotate(AnnotationRequest request) {
        String signature = request.signature();
        return new Annotation(
                "调用 " + methodName(signature),
                "—",
                "—",
                List.of(),
                new Evidence(fileOf(signature), "", List.of()),
                false,
                null);
    }

    private static String methodName(String signature) {
        int hash = signature.indexOf('#');
        int paren = signature.indexOf('(', hash + 1);
        if (hash >= 0 && paren > hash) {
            return signature.substring(hash + 1, paren);
        }
        return signature;
    }

    private static String fileOf(String signature) {
        int hash = signature.indexOf('#');
        String fqcn = hash >= 0 ? signature.substring(0, hash) : signature;
        int dollar = fqcn.indexOf('$');
        if (dollar >= 0) {
            fqcn = fqcn.substring(0, dollar);
        }
        return fqcn.replace('.', '/') + ".java";
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*FakeAnnotatorTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationRequest.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/Annotator.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/FakeAnnotator.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/FakeAnnotatorTest.java
git commit -m "feat(core): add annotator interface and offline fake" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: TraceJsonParser(trace.json → 未折叠 AnnotatedTree)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/TraceJsonParser.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/TraceJsonParserTest.java`

- [ ] **Step 1: 写失败测试**

`TraceJsonParserTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceJsonParserTest {

    private static final String TRACE_JSON = """
        {
          "entry_signature" : "A#a()",
          "captured_at" : "2026-06-17T00:00:00Z",
          "root" : {
            "id" : 0,
            "signature" : "A#a()",
            "file" : "com/example/A.java",
            "line" : 0,
            "self_ms" : 70,
            "children" : [ {
              "id" : 1,
              "signature" : "B#b()",
              "file" : "com/example/B.java",
              "line" : 0,
              "self_ms" : 30,
              "children" : [ ]
            } ]
          }
        }
        """;

    @Test
    void parsesStructureAndSelfMs() throws Exception {
        AnnotatedTree tree = new TraceJsonParser().parse(TRACE_JSON);

        assertEquals("A#a()", tree.getEntrySignature());
        assertEquals("2026-06-17T00:00:00Z", tree.getCapturedAt());

        AnnotatedNode root = tree.getRoot();
        assertEquals(0, root.getId());
        assertEquals("A#a()", root.getSignature());
        assertEquals("com/example/A.java", root.getFile());
        assertEquals(70, root.getSelfMs());
        assertEquals(1, root.getChildren().size());

        AnnotatedNode child = root.getChildren().get(0);
        assertEquals("B#b()", child.getSignature());
        assertEquals(30, child.getSelfMs());
        assertTrue(child.getChildren().isEmpty());

        // 解析出的节点默认未折叠、未标注
        assertFalse(root.isCollapsed());
        assertNull(root.getAnnotation());
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*TraceJsonParserTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`TraceJsonParser.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 解析 M1 产出的 trace.json → 未折叠未标注的 AnnotatedTree(只含结构 + self_ms)。 */
public class TraceJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnnotatedTree parse(String traceJson) throws IOException {
        JsonNode root = MAPPER.readTree(traceJson);
        AnnotatedNode rootNode = node(root.get("root"));
        return new AnnotatedTree(
                root.get("entry_signature").asText(),
                root.get("captured_at").asText(),
                rootNode);
    }

    private AnnotatedNode node(JsonNode n) {
        AnnotatedNode an = new AnnotatedNode(
                n.get("id").asInt(),
                n.get("signature").asText(),
                n.get("file").asText(),
                n.get("line").asInt(),
                n.get("self_ms").asLong());
        JsonNode children = n.get("children");
        if (children != null) {
            for (JsonNode c : children) {
                an.addChild(node(c));
            }
        }
        return an;
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*TraceJsonParserTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/TraceJsonParser.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/TraceJsonParserTest.java
git commit -m "feat(core): parse trace.json into annotated tree" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: PackageFolder(包名白名单 + 最大深度折叠)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/PackageFolder.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/PackageFolderTest.java`

折叠规则:**根节点(深度 0)永不折叠**;其余节点当 `深度 > maxDepth` 或 `声明类不在任一白名单包前缀下` 时标记 `collapsed=true`(自动折叠的工具/库节点)。

- [ ] **Step 1: 写失败测试**

`PackageFolderTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PackageFolderTest {

    private static AnnotatedNode n(int id, String sig) {
        return new AnnotatedNode(id, sig, "f", 0, 1);
    }

    @Test
    void collapsesOutOfWhitelistButNotRootOrIncluded() {
        AnnotatedNode root = n(0, "com.example.Controller#handle()");
        AnnotatedNode svc = n(1, "com.example.Service#work()");
        AnnotatedNode lib = n(2, "org.springframework.Tx#run()");
        root.addChild(svc);
        svc.addChild(lib);
        AnnotatedTree tree = new AnnotatedTree("com.example.Controller#handle()", "t", root);

        new PackageFolder(List.of("com.example"), 10).fold(tree);

        assertFalse(root.isCollapsed(), "根永不折叠");
        assertFalse(svc.isCollapsed(), "白名单内不折叠");
        assertTrue(lib.isCollapsed(), "白名单外应折叠");
    }

    @Test
    void collapsesBeyondMaxDepth() {
        AnnotatedNode root = n(0, "com.example.A#a()");
        AnnotatedNode d1 = n(1, "com.example.B#b()");
        AnnotatedNode d2 = n(2, "com.example.C#c()");
        root.addChild(d1);
        d1.addChild(d2);
        AnnotatedTree tree = new AnnotatedTree("com.example.A#a()", "t", root);

        new PackageFolder(List.of("com.example"), 1).fold(tree);

        assertFalse(root.isCollapsed());  // depth 0
        assertFalse(d1.isCollapsed());    // depth 1 == maxDepth
        assertTrue(d2.isCollapsed());     // depth 2 > maxDepth
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*PackageFolderTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`PackageFolder.java`:
```java
package com.threadmap.core.annotate;

import java.util.List;

/**
 * 折叠规则:根节点永不折叠;其余节点当深度超过 maxDepth 或声明类不在任一
 * 白名单包前缀下时,标记为 collapsed(自动折叠的工具/库节点)。
 */
public class PackageFolder {
    private final List<String> includePackages;
    private final int maxDepth;

    public PackageFolder(List<String> includePackages, int maxDepth) {
        this.includePackages = includePackages;
        this.maxDepth = maxDepth;
    }

    public void fold(AnnotatedTree tree) {
        foldNode(tree.getRoot(), 0);
    }

    private void foldNode(AnnotatedNode node, int depth) {
        boolean collapse = depth > 0 && (depth > maxDepth || !included(node.getSignature()));
        node.setCollapsed(collapse);
        for (AnnotatedNode child : node.getChildren()) {
            foldNode(child, depth + 1);
        }
    }

    private boolean included(String signature) {
        int hash = signature.indexOf('#');
        String fqcn = hash >= 0 ? signature.substring(0, hash) : signature;
        for (String prefix : includePackages) {
            if (fqcn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*PackageFolderTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/PackageFolder.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/PackageFolderTest.java
git commit -m "feat(core): add package-name folding" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: AnnotatedTreeJsonWriter

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTreeJsonWriter.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedTreeJsonWriterTest.java`

字段:每节点 `id / signature / file / line / self_ms / collapsed / understanding / children[]`;若有标注再追加 `summary / inputs / outputs / side_effects[] / evidence{file,lines,calls} / dig_worthy / dig_reason`(无标注则省略这些键)。

- [ ] **Step 1: 写失败测试**

`AnnotatedTreeJsonWriterTest.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedTreeJsonWriterTest {

    @Test
    void serializesNodesWithSnakeCaseAndOptionalAnnotation() throws Exception {
        AnnotatedNode root = new AnnotatedNode(0, "A#a()", "com/example/A.java", 5, 70);
        root.setUnderstanding(Understanding.MASTERED);
        root.setAnnotation(new Annotation(
                "创建任务并置为 CREATED",
                "RecruitCommand",
                "void",
                List.of("DB写", "外部API"),
                new Evidence("com/example/A.java", "32-48", List.of("repo.save")),
                true,
                "含关键分支"));

        AnnotatedNode child = new AnnotatedNode(1, "B#b()", "com/example/B.java", 0, 30);
        child.setCollapsed(true);   // 折叠 → 无标注
        root.addChild(child);

        AnnotatedTree tree = new AnnotatedTree("A#a()", "2026-06-17T00:00:00Z", root);
        String json = new AnnotatedTreeJsonWriter().toJson(tree);
        JsonNode parsed = new ObjectMapper().readTree(json);

        assertEquals("A#a()", parsed.get("entry_signature").asText());
        JsonNode r = parsed.get("root");
        assertEquals(5, r.get("line").asInt());
        assertEquals(70, r.get("self_ms").asInt());
        assertFalse(r.get("collapsed").asBoolean());
        assertEquals("mastered", r.get("understanding").asText());
        assertEquals("创建任务并置为 CREATED", r.get("summary").asText());
        assertTrue(r.get("dig_worthy").asBoolean());
        assertEquals("含关键分支", r.get("dig_reason").asText());
        assertEquals("DB写", r.get("side_effects").get(0).asText());
        assertEquals("32-48", r.get("evidence").get("lines").asText());
        assertEquals("repo.save", r.get("evidence").get("calls").get(0).asText());

        JsonNode c = r.get("children").get(0);
        assertTrue(c.get("collapsed").asBoolean());
        assertEquals("unknown", c.get("understanding").asText());
        assertFalse(c.has("summary"), "折叠/未标注节点不应有 summary");
        assertFalse(c.has("dig_worthy"));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedTreeJsonWriterTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`AnnotatedTreeJsonWriter.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 把 AnnotatedTree 序列化成 annotated-tree.json(显式 snake_case 字段)。 */
public class AnnotatedTreeJsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(AnnotatedTree tree) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("entry_signature", tree.getEntrySignature());
        root.put("captured_at", tree.getCapturedAt());
        root.set("root", node(tree.getRoot()));
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unexpected annotated-tree serialization failure", e);
        }
    }

    private ObjectNode node(AnnotatedNode n) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("id", n.getId());
        o.put("signature", n.getSignature());
        o.put("file", n.getFile());
        o.put("line", n.getLine());
        o.put("self_ms", n.getSelfMs());
        o.put("collapsed", n.isCollapsed());
        o.put("understanding", n.getUnderstanding().name().toLowerCase());

        Annotation a = n.getAnnotation();
        if (a != null) {
            o.put("summary", a.summary());
            o.put("inputs", a.inputs());
            o.put("outputs", a.outputs());
            ArrayNode sideEffects = o.putArray("side_effects");
            for (String s : a.sideEffects()) {
                sideEffects.add(s);
            }
            ObjectNode evidence = o.putObject("evidence");
            evidence.put("file", a.evidence().file());
            evidence.put("lines", a.evidence().lines());
            ArrayNode calls = evidence.putArray("calls");
            for (String c : a.evidence().calls()) {
                calls.add(c);
            }
            o.put("dig_worthy", a.digWorthy());
            if (a.digReason() != null) {
                o.put("dig_reason", a.digReason());
            }
        }

        ArrayNode children = o.putArray("children");
        for (AnnotatedNode c : n.getChildren()) {
            children.add(node(c));
        }
        return o;
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*AnnotatedTreeJsonWriterTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotatedTreeJsonWriter.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotatedTreeJsonWriterTest.java
git commit -m "feat(core): add annotated-tree.json writer" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: AnnotationPipeline(端到端编排)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationPipeline.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationPipelineTest.java`

流水:解析 trace.json → 折叠 → **只标注未折叠节点**(懒标)→ 返回 AnnotatedTree。

- [ ] **Step 1: 写失败测试**

`AnnotationPipelineTest.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationPipelineTest {

    private static final String TRACE_JSON = """
        {
          "entry_signature" : "com.example.Controller#handle()",
          "captured_at" : "2026-06-17T00:00:00Z",
          "root" : {
            "id" : 0, "signature" : "com.example.Controller#handle()",
            "file" : "com/example/Controller.java", "line" : 0, "self_ms" : 10,
            "children" : [ {
              "id" : 1, "signature" : "com.example.Service#work()",
              "file" : "com/example/Service.java", "line" : 0, "self_ms" : 5,
              "children" : [ {
                "id" : 2, "signature" : "org.springframework.Tx#run()",
                "file" : "org/springframework/Tx.java", "line" : 0, "self_ms" : 1,
                "children" : [ ]
              } ]
            } ]
          }
        }
        """;

    @Test
    void foldsOutOfPackageAndAnnotatesOnlyVisibleNodes() throws Exception {
        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of("com.example"), 10),
                new FakeAnnotator());

        AnnotatedTree tree = pipeline.run(TRACE_JSON);

        AnnotatedNode root = tree.getRoot();
        AnnotatedNode service = root.getChildren().get(0);
        AnnotatedNode lib = service.getChildren().get(0);

        // 折叠:库节点折叠,业务节点不折叠
        assertFalse(root.isCollapsed());
        assertFalse(service.isCollapsed());
        assertTrue(lib.isCollapsed());

        // 懒标:未折叠节点有标注,折叠节点无标注
        assertNotNull(root.getAnnotation());
        assertNotNull(service.getAnnotation());
        assertNull(lib.getAnnotation(), "折叠节点不标注");
        assertTrue(service.getAnnotation().summary().contains("work"));

        // 可序列化为 annotated-tree.json
        String json = new AnnotatedTreeJsonWriter().toJson(tree);
        JsonNode parsed = new ObjectMapper().readTree(json);
        assertEquals("com.example.Controller#handle()", parsed.get("entry_signature").asText());
        JsonNode libNode = parsed.get("root").get("children").get(0).get("children").get(0);
        assertTrue(libNode.get("collapsed").asBoolean());
        assertFalse(libNode.has("summary"));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*AnnotationPipelineTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`AnnotationPipeline.java`:
```java
package com.threadmap.core.annotate;

import java.io.IOException;

/** M2 处理流水:trace.json → 折叠 → 逐节点标注(懒标)→ AnnotatedTree。 */
public class AnnotationPipeline {
    private final TraceJsonParser parser = new TraceJsonParser();
    private final PackageFolder folder;
    private final Annotator annotator;

    public AnnotationPipeline(PackageFolder folder, Annotator annotator) {
        this.folder = folder;
        this.annotator = annotator;
    }

    public AnnotatedTree run(String traceJson) throws IOException {
        AnnotatedTree tree = parser.parse(traceJson);
        folder.fold(tree);
        annotate(tree.getRoot());
        return tree;
    }

    private void annotate(AnnotatedNode node) {
        if (!node.isCollapsed()) {
            node.setAnnotation(annotator.annotate(
                    AnnotationRequest.ofSignature(node.getSignature())));
        }
        for (AnnotatedNode child : node.getChildren()) {
            annotate(child);
        }
    }
}
```

- [ ] **Step 4: 运行,确认通过 + 全量测试**

Run: `./gradlew :threadmap-core:test --tests '*AnnotationPipelineTest'`
Expected: PASS。
Run: `./gradlew :threadmap-core:test`
Expected: 全部测试 PASS(M1 + M2a)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationPipeline.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationPipelineTest.java
git commit -m "feat(core): add annotation pipeline (trace.json -> annotated tree)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准(M2a Done）

- `./gradlew :threadmap-core:test` 全绿。
- 给定 M1 的 `trace.json`,`AnnotationPipeline` 产出 `AnnotatedTree`,可序列化为 `annotated-tree.json`,字段含 `collapsed / understanding / summary / side_effects / evidence{file,lines,calls} / dig_worthy / dig_reason`;库/工具节点折叠且不标注。
- 标注来源经 `Annotator` 接口隔离,本阶段为离线 `FakeAnnotator`。

## 后续(M2b,另开计划)

- 引入 `dev.langchain4j:langchain4j-community-dashscope`,实现 `QwenAnnotator implements Annotator`(`QwenChatModel` + `model.chat(prompt)` → JSON → Jackson 解析,绑定证据 prompt,默认模型 `qwen3.6-flash` 可配,`DASHSCOPE_API_KEY`)。
- 用 JavaParser 按 `signature` 抽取方法源码 + 直接被调签名,填充 `AnnotationRequest`(供真实标注)。
- 方法体哈希缓存:同一方法体不重复调用 LLM。
- 离线降级:LLM 不可用时回退 `FakeAnnotator`。
- CLI 入口:`trace.json` → `annotated-tree.json` 落盘到 `.threadmap/`。
