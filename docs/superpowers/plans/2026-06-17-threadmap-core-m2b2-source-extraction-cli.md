# Threadmap Core M2b-2 — 源码抽取 + 流水接线 + CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 M2b-1 的真实 Qwen 标注栈接进流水,跑通端到端:用 JavaParser 按 `signature` 在源码根里抽取方法源码 + 直接被调方法名填进 `AnnotationRequest`,`AnnotationPipeline` 注入"请求构造器",再加一个 CLI 把 `trace.json` → `.threadmap/annotated-tree.json`。顺带做掉 M2a/M2b-1 终审带来的两个小加固。

**Architecture:** 新增 `MethodSourceExtractor`(JavaParser,按签名定位方法、取 `toString()` 源码 + `MethodCallExpr` 名)。新增 `AnnotationRequestBuilder` 接缝:默认仅签名(`ofSignature`),`SourceBackedRequestBuilder` 用抽取器填 source/callees。`AnnotationPipeline` 加一个注入了请求构造器的构造器(2-arg 旧构造器保留,默认仅签名,M2a 测试不破)。`ThreadmapCli` 提供可测的 `run(...)`(注入 pipeline)+ `main(...)`(读 env/参数、组装真实 Qwen 链)。

**Tech Stack:** Java 17、Gradle、Jackson、JUnit 5、**新增** `com.github.javaparser:javaparser-core`。

**这是 M2b 的第 2 份(收尾):** 完成后可跑真实链路;再做 M4(拿 AECLAW 一条链路端到端验证)。

**承接的接缝(M2b-1 已就绪,不改其文件):** `Annotator` / `AnnotationRequest(signature, source, calleeSignatures)` / `QwenChatModels.create(key, model)` / `QwenAnnotator(ChatFn, fallback)` / `CachingAnnotator(delegate)` / `FakeAnnotator` / `AnnotationPipeline(PackageFolder, Annotator)` / `AnnotatedTreeJsonWriter` / `TraceJsonParser`。

**仍然推迟(需要日志框架,本计划不引入):** `QwenAnnotator` 里 `catch` 的 `TODO(M2b-2)` 日志 —— 等决定引入 SLF4J 再做,本计划只把它从"无声"降级保持现状。

---

## 文件结构

```
threadmap-core/build.gradle                                    # 改:加 javaparser-core 依赖
threadmap-core/src/main/java/com/threadmap/core/annotate/
  TraceJsonParser.java          # 改:entry_signature/captured_at 字段校验加固
  CachingAnnotator.java         # 改:缓存键加 "src:"/"sig:" 前缀(防理论碰撞)
  MethodSourceExtractor.java    # 创建:JavaParser 按签名抽方法源码 + 被调名
  AnnotationRequestBuilder.java # 创建:AnnotatedNode → AnnotationRequest 接缝
  SourceBackedRequestBuilder.java # 创建:用抽取器填 source/callees
  AnnotationPipeline.java       # 改:加 3-arg 构造器(注入请求构造器),annotate 用之
  ThreadmapCli.java             # 创建:可测 run(...) + main(...) 组装真实链
threadmap-core/src/test/java/com/threadmap/core/annotate/
  TraceJsonParserTest.java          # 改:加缺字段→IOException 测试
  MethodSourceExtractorTest.java    # 创建
  SourceBackedRequestBuilderTest.java # 创建
  AnnotationPipelineTest.java       # 改:加注入请求构造器的测试
  ThreadmapCliTest.java             # 创建(用 FakeAnnotator,不联网)
```

---

## Task 1: 加 JavaParser 依赖

**Files:**
- Modify: `threadmap-core/build.gradle`

- [ ] **Step 1: 加依赖**

在 `dependencies { ... }` 块里 `implementation 'dev.langchain4j:langchain4j-community-dashscope:1.9.0-beta16'` 之后加:
```groovy
    implementation 'com.github.javaparser:javaparser-core:3.27.0'
```

- [ ] **Step 2: 验证构建**

Run: `./gradlew :threadmap-core:build`
Expected: `BUILD SUCCESSFUL`(依赖下载,既有 42 测试仍全绿)。
**版本不确定:** 若 `3.27.0` 解析失败,`curl -s https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/maven-metadata.xml` 取最新 3.x 版本改之并重试;报告所用版本。其它原因失败则 BLOCKED 报告。

- [ ] **Step 3: 提交**

```bash
git add threadmap-core/build.gradle
git commit -m "build(core): add javaparser-core dependency" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 终审遗留加固(TraceJsonParser 校验 + CachingAnnotator 键前缀)

**Files:**
- Modify: `threadmap-core/src/main/java/com/threadmap/core/annotate/TraceJsonParser.java`
- Modify: `threadmap-core/src/main/java/com/threadmap/core/annotate/CachingAnnotator.java`
- Modify: `threadmap-core/src/test/java/com/threadmap/core/annotate/TraceJsonParserTest.java`

- [ ] **Step 1: 写失败测试**(给 `TraceJsonParserTest` 加缺字段校验)

在 `TraceJsonParserTest` 类中加:
```java
    @Test
    void rejectsMissingRootWithClearError() {
        String bad = "{\"entry_signature\":\"A#a()\",\"captured_at\":\"t\"}";
        IOException e = assertThrows(IOException.class, () -> new TraceJsonParser().parse(bad));
        assertTrue(e.getMessage().contains("root"));
    }

    @Test
    void rejectsMissingEntrySignatureWithClearError() {
        String bad = """
            {"captured_at":"t","root":{"id":0,"signature":"A#a()","file":"A.java","line":0,"self_ms":1,"children":[]}}
            """;
        IOException e = assertThrows(IOException.class, () -> new TraceJsonParser().parse(bad));
        assertTrue(e.getMessage().contains("entry_signature"));
    }
```
(确保该测试文件已 `import java.io.IOException;` 和 `import static org.junit.jupiter.api.Assertions.*;`。)

- [ ] **Step 2: 运行,确认新测试失败**

Run: `./gradlew :threadmap-core:test --tests '*TraceJsonParserTest'`
Expected: `rejectsMissingEntrySignatureWithClearError` 失败(当前 `entry_signature` 缺失时 `asText()` 返回 "null" 而不抛)。

- [ ] **Step 3: 加固实现**

`TraceJsonParser.java` —— 把 `parse` 改成对三个顶层字段都用 `requireField` 校验:
```java
    public AnnotatedTree parse(String traceJson) throws IOException {
        JsonNode root = MAPPER.readTree(traceJson);
        JsonNode rootField = requireField(root, "root", traceJson);
        return new AnnotatedTree(
                requireField(root, "entry_signature", traceJson).asText(),
                requireField(root, "captured_at", traceJson).asText(),
                node(rootField));
    }

    private static JsonNode requireField(JsonNode obj, String name, String json) throws IOException {
        JsonNode f = obj.get(name);
        if (f == null || f.isNull()) {
            throw new IOException("trace.json missing required field: " + name);
        }
        return f;
    }
```
(删掉原先单独的 root 守卫 `if (rootField == null || rootField.isNull()) ...`,统一走 `requireField`。`node(JsonNode)` 私有方法不变。)

`CachingAnnotator.java` —— 缓存键加模式前缀,防 source 文本恰好等于某 signature 串的理论碰撞。把 `cacheKey` 改为:
```java
    private static String cacheKey(AnnotationRequest request) {
        boolean hasSource = request.source() != null && !request.source().isBlank();
        String basis = hasSource ? request.source() : request.signature();
        return sha256((hasSource ? "src:" : "sig:") + basis);
    }
```

- [ ] **Step 4: 运行全部测试**

Run: `./gradlew :threadmap-core:test`
Expected: 全绿(新增 2 个 parser 测试通过;既有 CachingAnnotator 测试仍通过——键内部一致)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/TraceJsonParser.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/CachingAnnotator.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/TraceJsonParserTest.java
git commit -m "harden(core): validate trace.json top-level fields, prefix cache keys" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: MethodSourceExtractor(JavaParser 抽取)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/MethodSourceExtractor.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/MethodSourceExtractorTest.java`

给定源码根列表 + trace 签名(`com.example.Foo#bar(String, int)`),定位 `.java` 文件、用 JavaParser 找到方法、返回方法源码(`toString()`)+ 直接被调方法名(去重)。按 **方法名 + 参数个数** 匹配(非重载场景足够;重载时取首个,属已知局限)。找不到/解析失败 → `Optional.empty()`。

- [ ] **Step 1: 写失败测试**

`MethodSourceExtractorTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MethodSourceExtractorTest {

    private Path writeSample(Path root) throws Exception {
        Path dir = root.resolve("com/example");
        Files.createDirectories(dir);
        Path file = dir.resolve("Sample.java");
        Files.writeString(file, """
                package com.example;
                class Sample {
                    String greet(String name) {
                        String v = normalize(name);
                        return repo.save(v);
                    }
                    String normalize(String s) { return s.trim(); }
                }
                """);
        return file;
    }

    @Test
    void extractsMethodSourceAndCalleeNames(@TempDir Path root) throws Exception {
        writeSample(root);
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));

        Optional<MethodSourceExtractor.Extracted> got =
                extractor.extract("com.example.Sample#greet(String)");

        assertTrue(got.isPresent());
        assertTrue(got.get().source().contains("greet"), "源码应含方法名");
        assertTrue(got.get().source().contains("normalize"), "源码应含方法体内容");
        assertTrue(got.get().calleeNames().contains("normalize"), "被调名应含 normalize");
        assertTrue(got.get().calleeNames().contains("save"), "被调名应含 save");
    }

    @Test
    void returnsEmptyWhenFileMissing(@TempDir Path root) {
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));
        assertTrue(extractor.extract("com.example.Nope#x()").isEmpty());
    }

    @Test
    void returnsEmptyOnMalformedSignature(@TempDir Path root) {
        MethodSourceExtractor extractor = new MethodSourceExtractor(List.of(root));
        assertTrue(extractor.extract("garbage").isEmpty());
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*MethodSourceExtractorTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`MethodSourceExtractor.java`:
```java
package com.threadmap.core.annotate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用 JavaParser 按 trace 签名(类#方法(简单参数类型))在源码根里定位方法,
 * 抽取方法源码 + 直接被调方法名。按 方法名 + 参数个数 匹配(重载时取首个,已知局限)。
 */
public class MethodSourceExtractor {
    private final List<Path> sourceRoots;

    public MethodSourceExtractor(List<Path> sourceRoots) {
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    public Optional<Extracted> extract(String signature) {
        Parsed sig = parseSignature(signature);
        if (sig == null) {
            return Optional.empty();
        }
        Path file = locate(sig.fqcn());
        if (file == null) {
            return Optional.empty();
        }
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(file));
        } catch (Exception e) {
            return Optional.empty();
        }
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(sig.method()))
                .filter(m -> m.getParameters().size() == sig.arity())
                .findFirst()
                .map(MethodSourceExtractor::toExtracted);
    }

    private static Extracted toExtracted(MethodDeclaration method) {
        Set<String> callees = new LinkedHashSet<>();
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            callees.add(call.getNameAsString());
        }
        return new Extracted(method.toString(), new ArrayList<>(callees));
    }

    /** FQCN(取 # 前;内部类 $ 截到外层)→ 在各 source root 下找 a/b/C.java。 */
    private Path locate(String fqcn) {
        int dollar = fqcn.indexOf('$');
        String outer = dollar >= 0 ? fqcn.substring(0, dollar) : fqcn;
        String relative = outer.replace('.', '/') + ".java";
        for (Path root : sourceRoots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Parsed parseSignature(String signature) {
        int hash = signature.indexOf('#');
        if (hash < 0) {
            return null;
        }
        int open = signature.indexOf('(', hash + 1);
        int close = signature.lastIndexOf(')');
        if (open < hash || close < open) {
            return null;
        }
        String fqcn = signature.substring(0, hash);
        String method = signature.substring(hash + 1, open);
        String params = signature.substring(open + 1, close).trim();
        int arity = params.isEmpty() ? 0 : params.split(",").length;
        return new Parsed(fqcn, method, arity);
    }

    private record Parsed(String fqcn, String method, int arity) {
    }

    /** 抽取结果:方法源码 + 直接被调方法名(去重,保序)。 */
    public record Extracted(String source, List<String> calleeNames) {
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*MethodSourceExtractorTest'`
Expected: PASS。若某 JavaParser API 与上述签名不符导致编译失败,核对 javaparser-core 该版本的对应 API 并修正(StaticJavaParser.parse(String) / findAll / getNameAsString / getParameters / MethodCallExpr 均为 3.x 稳定 API),报告任何偏差。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/MethodSourceExtractor.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/MethodSourceExtractorTest.java
git commit -m "feat(core): add JavaParser method-source extractor" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 请求构造器接缝 + 流水接线

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationRequestBuilder.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/SourceBackedRequestBuilder.java`
- Modify: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationPipeline.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/SourceBackedRequestBuilderTest.java`
- Modify: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationPipelineTest.java`

- [ ] **Step 1: 写失败测试**

`SourceBackedRequestBuilderTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceBackedRequestBuilderTest {

    @Test
    void fillsSourceAndCalleesWhenFound(@TempDir Path root) throws Exception {
        Path dir = root.resolve("com/example");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("S.java"), """
                package com.example;
                class S { int run() { return helper(); } int helper() { return 1; } }
                """);
        SourceBackedRequestBuilder builder =
                new SourceBackedRequestBuilder(new MethodSourceExtractor(List.of(root)));

        AnnotatedNode node = new AnnotatedNode(0, "com.example.S#run()", "com/example/S.java", 0, 1);
        AnnotationRequest req = builder.build(node);

        assertEquals("com.example.S#run()", req.signature());
        assertNotNull(req.source());
        assertTrue(req.source().contains("helper"));
        assertTrue(req.calleeSignatures().contains("helper"));
    }

    @Test
    void fallsBackToSignatureWhenNotFound(@TempDir Path root) {
        SourceBackedRequestBuilder builder =
                new SourceBackedRequestBuilder(new MethodSourceExtractor(List.of(root)));
        AnnotatedNode node = new AnnotatedNode(0, "com.example.Missing#x()", "x", 0, 1);

        AnnotationRequest req = builder.build(node);

        assertEquals("com.example.Missing#x()", req.signature());
        assertNull(req.source());
        assertTrue(req.calleeSignatures().isEmpty());
    }
}
```

在 `AnnotationPipelineTest` 中加一个验证"注入的请求构造器被用于未折叠节点"的测试:
```java
    @Test
    void usesInjectedRequestBuilderForVisibleNodes() throws Exception {
        java.util.List<String> asked = new java.util.ArrayList<>();
        AnnotationRequestBuilder recording = node -> {
            asked.add(node.getSignature());
            return AnnotationRequest.ofSignature(node.getSignature());
        };
        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(java.util.List.of("com.example"), 10),
                new FakeAnnotator(),
                recording);

        pipeline.run(TRACE_JSON);  // 复用本类已有的 TRACE_JSON 常量

        // 未折叠的业务节点被请求,折叠的库节点不被请求
        assertTrue(asked.contains("com.example.Controller#handle()"));
        assertTrue(asked.contains("com.example.Service#work()"));
        assertFalse(asked.contains("org.springframework.Tx#run()"), "折叠节点不应构造请求");
    }
```
(`TRACE_JSON` 是 `AnnotationPipelineTest` 已有的私有常量,直接复用。)

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*SourceBackedRequestBuilderTest' --tests '*AnnotationPipelineTest'`
Expected: 编译失败(`AnnotationRequestBuilder` / `SourceBackedRequestBuilder` / 3-arg 构造器 不存在)。

- [ ] **Step 3: 实现**

`AnnotationRequestBuilder.java`:
```java
package com.threadmap.core.annotate;

/** 把一个节点变成标注请求。默认仅签名;源码版填充 source + 被调名(M2b-2)。 */
@FunctionalInterface
public interface AnnotationRequestBuilder {
    AnnotationRequest build(AnnotatedNode node);
}
```

`SourceBackedRequestBuilder.java`:
```java
package com.threadmap.core.annotate;

import java.util.Objects;

/** 用 MethodSourceExtractor 按签名抽取源码 + 被调名;抽不到时退化为仅签名。 */
public class SourceBackedRequestBuilder implements AnnotationRequestBuilder {
    private final MethodSourceExtractor extractor;

    public SourceBackedRequestBuilder(MethodSourceExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    @Override
    public AnnotationRequest build(AnnotatedNode node) {
        String signature = node.getSignature();
        return extractor.extract(signature)
                .map(e -> new AnnotationRequest(signature, e.source(), e.calleeNames()))
                .orElseGet(() -> AnnotationRequest.ofSignature(signature));
    }
}
```

`AnnotationPipeline.java` —— 加 3-arg 构造器 + 用注入的请求构造器(2-arg 旧构造器保留,默认仅签名):
```java
package com.threadmap.core.annotate;

import java.io.IOException;
import java.util.Objects;

/** M2 处理流水:trace.json → 折叠 → 逐节点标注(懒标)→ AnnotatedTree。 */
public class AnnotationPipeline {
    private final TraceJsonParser parser = new TraceJsonParser();
    private final PackageFolder folder;
    private final Annotator annotator;
    private final AnnotationRequestBuilder requestBuilder;

    public AnnotationPipeline(PackageFolder folder, Annotator annotator) {
        this(folder, annotator, node -> AnnotationRequest.ofSignature(node.getSignature()));
    }

    public AnnotationPipeline(PackageFolder folder, Annotator annotator,
                              AnnotationRequestBuilder requestBuilder) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.annotator = Objects.requireNonNull(annotator, "annotator");
        this.requestBuilder = Objects.requireNonNull(requestBuilder, "requestBuilder");
    }

    public AnnotatedTree run(String traceJson) throws IOException {
        AnnotatedTree tree = parser.parse(traceJson);
        folder.fold(tree);
        annotate(tree.getRoot());
        return tree;
    }

    private void annotate(AnnotatedNode node) {
        if (!node.isCollapsed()) {
            node.setAnnotation(annotator.annotate(requestBuilder.build(node)));
        }
        for (AnnotatedNode child : node.getChildren()) {
            annotate(child);
        }
    }
}
```

- [ ] **Step 4: 运行,确认通过 + 全量**

Run: `./gradlew :threadmap-core:test --tests '*SourceBackedRequestBuilderTest' --tests '*AnnotationPipelineTest'`
Expected: PASS(含 M2a 既有的流水测试 —— 它用 2-arg 构造器,仍通过)。
Run: `./gradlew :threadmap-core:test`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationRequestBuilder.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/SourceBackedRequestBuilder.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationPipeline.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/SourceBackedRequestBuilderTest.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationPipelineTest.java
git commit -m "feat(core): wire source-backed request builder into pipeline" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: ThreadmapCli(端到端入口)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/ThreadmapCli.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/ThreadmapCliTest.java`

可测的 `run(...)` 注入 pipeline(测试用 `FakeAnnotator`,不联网);`main(...)` 读环境变量/参数,组装真实 Qwen 链(`CachingAnnotator(QwenAnnotator(QwenChatModels.create(key, DEFAULT_MODEL), FakeAnnotator))`)+ 源码抽取器,跑完写 `.threadmap/annotated-tree.json`。

- [ ] **Step 1: 写失败测试**

`ThreadmapCliTest.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThreadmapCliTest {

    private static final String TRACE_JSON = """
            {"entry_signature":"com.example.A#a()","captured_at":"2026-06-17T00:00:00Z",
             "root":{"id":0,"signature":"com.example.A#a()","file":"com/example/A.java",
                     "line":0,"self_ms":5,"children":[]}}
            """;

    @Test
    void readsTraceRunsPipelineAndWritesAnnotatedTree(@TempDir Path dir) throws Exception {
        Path trace = dir.resolve("trace.json");
        Files.writeString(trace, TRACE_JSON);
        Path out = dir.resolve(".threadmap/annotated-tree.json");

        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of("com.example"), 50),
                new FakeAnnotator());

        ThreadmapCli.run(trace, out, pipeline);

        assertTrue(Files.exists(out));
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(out));
        assertEquals("com.example.A#a()", parsed.get("entry_signature").asText());
        assertTrue(parsed.get("root").has("summary"), "未折叠根节点应已标注");
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*ThreadmapCliTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`ThreadmapCli.java`:
```java
package com.threadmap.core.annotate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 端到端入口:trace.json → 折叠 + 标注 → .threadmap/annotated-tree.json。
 * run(...) 可测(注入 pipeline);main(...) 组装真实 Qwen 链(读 DASHSCOPE_API_KEY)。
 */
public final class ThreadmapCli {

    private ThreadmapCli() {
    }

    /** 可测核心:读 trace、跑 pipeline、写 annotated-tree。 */
    public static void run(Path traceJson, Path outputJson, AnnotationPipeline pipeline) throws IOException {
        String trace = Files.readString(traceJson);
        AnnotatedTree tree = pipeline.run(trace);
        if (outputJson.getParent() != null) {
            Files.createDirectories(outputJson.getParent());
        }
        Files.writeString(outputJson, new AnnotatedTreeJsonWriter().toJson(tree));
    }

    /**
     * 用法: ThreadmapCli <trace.json> <out.json> <includePackage> [sourceRoot...]
     * 读环境变量 DASHSCOPE_API_KEY;无 key 时退化为离线 FakeAnnotator。
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("用法: ThreadmapCli <trace.json> <out.json> <includePackage> [sourceRoot...]");
            System.exit(2);
            return;
        }
        Path trace = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String includePackage = args[2];

        List<Path> sourceRoots = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            sourceRoots.add(Path.of(args[i]));
        }

        Annotator annotator = buildAnnotator();
        AnnotationRequestBuilder requestBuilder = sourceRoots.isEmpty()
                ? node -> AnnotationRequest.ofSignature(node.getSignature())
                : new SourceBackedRequestBuilder(new MethodSourceExtractor(sourceRoots));

        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of(includePackage), 50),
                annotator,
                requestBuilder);

        run(trace, out, pipeline);
        System.out.println("已写出: " + out);
    }

    private static Annotator buildAnnotator() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("未设置 DASHSCOPE_API_KEY,使用离线 FakeAnnotator。");
            return new FakeAnnotator();
        }
        ChatFn chat = QwenChatModels.create(key, QwenChatModels.DEFAULT_MODEL);
        return new CachingAnnotator(new QwenAnnotator(chat, new FakeAnnotator()));
    }
}
```

- [ ] **Step 4: 运行,确认通过 + 全量**

Run: `./gradlew :threadmap-core:test --tests '*ThreadmapCliTest'`
Expected: PASS。
Run: `./gradlew :threadmap-core:test`
Expected: 全绿(M1 + M2a + M2b-1 + M2b-2)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/ThreadmapCli.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/ThreadmapCliTest.java
git commit -m "feat(core): add end-to-end CLI (trace.json -> annotated-tree.json)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准(M2b-2 Done）

- `./gradlew :threadmap-core:test` 全绿(离线;CLI/抽取器测试用临时源码与 FakeAnnotator,不联网)。
- 端到端可跑:给定 `trace.json` + 源码根 + 包白名单,`ThreadmapCli` 产出 `annotated-tree.json`,未折叠节点的标注由 JavaParser 抽到的真实方法源码驱动(设了 `DASHSCOPE_API_KEY` 则走 `qwen3.6-flash`,否则离线 fake)。
- M2(core 引擎)至此完整:采集(M1)→ 折叠 + 真实标注 + 缓存(M2a/M2b)→ `annotated-tree.json`。

## 后续

- **M4:** 拿 AECLAW 一条真实链路端到端跑通(`trace.json` 由 M1 采集 → `ThreadmapCli` + 真实 `qwen3.6-flash`),人工核对标注质量,迭代 prompt / 折叠规则。
- **M3:** IntelliJ 插件(读 `annotated-tree.json` 渲染)。
- 仍推迟:`QwenAnnotator` 降级日志(待引入日志框架);重载方法的签名级精确匹配(当前按名+参数个数);`LexicalPreservingPrinter` 保留原始格式(当前用 `toString()`)。
