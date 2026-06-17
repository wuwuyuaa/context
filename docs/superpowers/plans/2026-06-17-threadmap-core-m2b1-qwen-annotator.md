# Threadmap Core M2b-1 — 真实 Qwen 标注器栈 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `threadmap-core` 里实现真实的 LLM 标注器栈 —— 用 LangChain4j + DashScope(Qwen,默认 `qwen3.6-flash`)把一个 `AnnotationRequest` 标注成结构化 `Annotation`,绑证据 prompt + 容错 JSON 解析 + 失败降级 + 方法体哈希缓存。**全程离线可测**:LangChain4j 的 `ChatModel` 藏在 `ChatFn` 接缝后,单测用桩,不联网。

**Architecture:** `ChatFn`(极简对话接缝)隔离 LangChain4j;`QwenChatModels` 是唯一直接 import LangChain4j 的胶水文件。`QwenAnnotator` = 构 prompt → `ChatFn.chat` → 解析 JSON → `Annotation`,任何异常降级到注入的 `Annotator`(M2a 的 `FakeAnnotator`)。`CachingAnnotator` 是按方法体哈希缓存的装饰器。这些都通过已有的 `Annotator` 接口对接,**不改 M2a 的流水/解析/写出**。

**Tech Stack:** Java 17、Gradle、Jackson(已有)、JUnit 5、**新增** `dev.langchain4j:langchain4j-community-dashscope`。

**这是 M2b 的第 1 份(共 2 份):** M2b-1 = Qwen 标注器栈(离线可测,本文档);M2b-2 = JavaParser 源码抽取 + 流水接线 + CLI + parser 校验加固。

**M2b-2 已知的接线点(本阶段不实现):**
- `AnnotationPipeline` 目前用 `AnnotationRequest.ofSignature(...)`(source 为 null)。M2b-2 会注入一个源码抽取器,填充 `source` + `calleeSignatures`,并把标注器换成 `new CachingAnnotator(new QwenAnnotator(QwenChatModels.create(key, model), new FakeAnnotator()))`。
- CLI 读环境变量 `DASHSCOPE_API_KEY`、默认模型 `qwen3.6-flash`,`trace.json` → `.threadmap/annotated-tree.json`。

**范围边界:** 本阶段不接流水、不读源码文件、不做 CLI。只交付可独立单测的标注器组件。`QwenChatModels` 是薄胶水,仅编译验证(不写联网测试)。

---

## 文件结构

```
threadmap-core/build.gradle                                   # 改:加 langchain4j-community-dashscope 依赖
threadmap-core/src/main/java/com/threadmap/core/annotate/
  ChatFn.java                  # 创建:String chat(String) 函数式接缝
  QwenChatModels.java          # 创建:QwenChatModel → ChatFn 工厂(唯一 import LangChain4j 处)
  PromptBuilder.java           # 创建:AnnotationRequest → 绑证据 prompt
  AnnotationJsonParser.java    # 创建:LLM 回复 → Annotation(容忍围栏/前后文字)
  QwenAnnotator.java           # 创建:Annotator 实现(prompt→chat→parse,失败降级)
  CachingAnnotator.java        # 创建:按方法体哈希缓存的 Annotator 装饰器
threadmap-core/src/test/java/com/threadmap/core/annotate/
  PromptBuilderTest.java
  AnnotationJsonParserTest.java
  QwenAnnotatorTest.java
  CachingAnnotatorTest.java
```

---

## Task 1: 加 LangChain4j DashScope 依赖

**Files:**
- Modify: `threadmap-core/build.gradle`

- [ ] **Step 1: 加依赖**

在 `threadmap-core/build.gradle` 的 `dependencies { ... }` 块里,`implementation 'com.fasterxml.jackson.core:jackson-databind'` 之后加一行:
```groovy
    implementation 'dev.langchain4j:langchain4j-community-dashscope:1.9.0-beta16'
```
(该版本含 `QwenChatModel`,并传递依赖 `langchain4j-core`(提供 `dev.langchain4j.model.chat.ChatModel`)。若解析失败,去 Maven Central 查 `langchain4j-community-dashscope` 最新 `1.x.x-betaNN` 改之。)

- [ ] **Step 2: 验证依赖可解析、全量构建通过**

Run: `./gradlew :threadmap-core:build`
Expected: `BUILD SUCCESSFUL`(依赖下载成功,既有测试仍全绿;若出现 Jackson 版本冲突等问题在此暴露)。

- [ ] **Step 3: 提交**

```bash
git add threadmap-core/build.gradle
git commit -m "build(core): add langchain4j-community-dashscope dependency" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: ChatFn 接缝 + QwenChatModels 工厂

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/ChatFn.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/QwenChatModels.java`

`ChatFn` 是函数式接口,后续 `QwenAnnotator` 依赖它而非具体 SDK。`QwenChatModels` 是唯一 import LangChain4j 的文件(薄胶水,编译验证即可,不写联网测试)。

- [ ] **Step 1: 创建两个文件**

`ChatFn.java`:
```java
package com.threadmap.core.annotate;

/** 极简对话接缝:把 prompt 发给某个 LLM,返回纯文本回复。
 *  隔离具体 SDK(LangChain4j),让 QwenAnnotator 可用桩离线测试。 */
@FunctionalInterface
public interface ChatFn {
    String chat(String prompt);
}
```

`QwenChatModels.java`:
```java
package com.threadmap.core.annotate;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

/** 构造 DashScope(Qwen)的 ChatFn。本模块唯一直接依赖 LangChain4j 的地方。 */
public final class QwenChatModels {

    /** 百炼/Model Studio 文本生成默认模型。 */
    public static final String DEFAULT_MODEL = "qwen3.6-flash";

    private QwenChatModels() {
    }

    public static ChatFn create(String apiKey, String modelName) {
        ChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        return model::chat; // ChatModel.chat(String) -> String
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :threadmap-core:compileJava`
Expected: `BUILD SUCCESSFUL`(确认 `QwenChatModel` / `ChatModel` import 正确、`model::chat` 方法引用成立)。

- [ ] **Step 3: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/ChatFn.java \
        threadmap-core/src/main/java/com/threadmap/core/annotate/QwenChatModels.java
git commit -m "feat(core): add ChatFn seam and Qwen ChatModel factory" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: PromptBuilder(绑证据 prompt)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/PromptBuilder.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/PromptBuilderTest.java`

按 spec §8:只发该方法源码 + 直接被调签名,要求严格 JSON、绑证据、禁空话。源码为 null 时降级为"仅凭签名"。

- [ ] **Step 1: 写失败测试**

`PromptBuilderTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    @Test
    void includesSignatureSourceCalleesAndJsonFields() {
        AnnotationRequest req = new AnnotationRequest(
                "com.example.MerchantService#recruit(RecruitCommand)",
                "void recruit(RecruitCommand c) { repo.save(c); }",
                List.of("com.example.MerchantRepository#save(RecruitCommand)"));

        String prompt = new PromptBuilder().build(req);

        assertTrue(prompt.contains("com.example.MerchantService#recruit(RecruitCommand)"));
        assertTrue(prompt.contains("repo.save(c)"), "应含方法源码");
        assertTrue(prompt.contains("com.example.MerchantRepository#save(RecruitCommand)"), "应含被调签名");
        // 关键 JSON 字段名要在 prompt 里(指示模型输出结构)
        assertTrue(prompt.contains("summary"));
        assertTrue(prompt.contains("side_effects"));
        assertTrue(prompt.contains("evidence"));
        assertTrue(prompt.contains("dig_worthy"));
    }

    @Test
    void degradesGracefullyWhenNoSource() {
        AnnotationRequest req = AnnotationRequest.ofSignature("A#a()");
        String prompt = new PromptBuilder().build(req);

        assertTrue(prompt.contains("A#a()"));
        assertTrue(prompt.contains("源码不可用"), "无源码时应说明");
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*PromptBuilderTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`PromptBuilder.java`:
```java
package com.threadmap.core.annotate;

/** 按 spec §8 组装标注 prompt:只发方法源码 + 直接被调签名,要求严格 JSON、绑证据、禁空话。 */
public class PromptBuilder {

    private static final String INSTRUCTIONS = """
            你是资深 Java 工程师。仅根据下面给出的「方法签名 + 方法源码 + 直接被调方法签名」,
            用中文描述这个方法"做了什么"。必须绑定证据(落到行号/被调方法/副作用),禁止空话。

            严格只返回一个 JSON 对象(不要任何额外文字、不要 Markdown 围栏),字段如下:
            {
              "summary": "≤25字,点出改了什么状态/产出什么;禁止'负责核心业务逻辑'这类空话",
              "inputs": "关键入参",
              "outputs": "返回/产出",
              "side_effects": ["DB写 / 外部API / 消息 等;没有则空数组 []"],
              "evidence": { "file": "源文件相对路径", "lines": "行号区间如 32-48", "calls": ["关键被调方法"] },
              "dig_worthy": true,
              "dig_reason": "若 dig_worthy 为 true 给出原因;否则 null"
            }
            """;

    public String build(AnnotationRequest request) {
        String source = request.source() != null && !request.source().isBlank()
                ? request.source()
                : "(源码不可用,仅凭签名推断)";
        String callees = request.calleeSignatures().isEmpty()
                ? "(无)"
                : String.join("\n", request.calleeSignatures());

        return INSTRUCTIONS
                + "\n方法签名:\n" + request.signature()
                + "\n\n方法源码:\n" + source
                + "\n\n直接被调方法签名:\n" + callees + "\n";
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*PromptBuilderTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/PromptBuilder.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/PromptBuilderTest.java
git commit -m "feat(core): add evidence-bound prompt builder" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: AnnotationJsonParser(容错 JSON → Annotation)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationJsonParser.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationJsonParserTest.java`

LLM 回复可能带 ```json 围栏或前后多余文字。解析器要剥围栏、取首个 `{...}`、缺字段给默认值。

- [ ] **Step 1: 写失败测试**

`AnnotationJsonParserTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationJsonParserTest {

    private static final String CLEAN = """
            {"summary":"创建任务并置为 CREATED","inputs":"RecruitCommand","outputs":"void",
             "side_effects":["DB写","外部API"],
             "evidence":{"file":"com/example/A.java","lines":"32-48","calls":["repo.save"]},
             "dig_worthy":true,"dig_reason":"含关键分支"}
            """;

    @Test
    void parsesCleanJson() {
        Annotation a = new AnnotationJsonParser().parse(CLEAN);
        assertEquals("创建任务并置为 CREATED", a.summary());
        assertEquals(2, a.sideEffects().size());
        assertEquals("DB写", a.sideEffects().get(0));
        assertEquals("com/example/A.java", a.evidence().file());
        assertEquals("32-48", a.evidence().lines());
        assertEquals("repo.save", a.evidence().calls().get(0));
        assertTrue(a.digWorthy());
        assertEquals("含关键分支", a.digReason());
    }

    @Test
    void toleratesCodeFenceAndSurroundingText() {
        String reply = "好的,分析如下:\n```json\n" + CLEAN + "\n```\n以上。";
        Annotation a = new AnnotationJsonParser().parse(reply);
        assertEquals("创建任务并置为 CREATED", a.summary());
        assertTrue(a.digWorthy());
    }

    @Test
    void defaultsMissingFields() {
        Annotation a = new AnnotationJsonParser().parse("{\"summary\":\"只有摘要\"}");
        assertEquals("只有摘要", a.summary());
        assertEquals("", a.inputs());
        assertTrue(a.sideEffects().isEmpty());
        assertNotNull(a.evidence());
        assertEquals("", a.evidence().file());
        assertFalse(a.digWorthy());
        assertNull(a.digReason());
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*AnnotationJsonParserTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`AnnotationJsonParser.java`:
```java
package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 把 LLM 文本回复解析成 Annotation;容忍 ```json 围栏与前后多余文字,缺字段给默认值。 */
public class AnnotationJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Annotation parse(String reply) {
        String json = extractJsonObject(reply);
        JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析标注 JSON: " + reply, e);
        }
        return new Annotation(
                text(n, "summary"),
                text(n, "inputs"),
                text(n, "outputs"),
                stringList(n.get("side_effects")),
                evidence(n.get("evidence")),
                n.path("dig_worthy").asBoolean(false),
                n.hasNonNull("dig_reason") ? n.get("dig_reason").asText() : null);
    }

    /** 剥掉 ``` 围栏,截取首个 '{' 到末个 '}'。 */
    private static String extractJsonObject(String reply) {
        String s = reply.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int closingFence = s.lastIndexOf("```");
            if (closingFence >= 0) {
                s = s.substring(0, closingFence);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : "";
    }

    private static List<String> stringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode e : array) {
                out.add(e.asText());
            }
        }
        return out;
    }

    private static Evidence evidence(JsonNode ev) {
        if (ev == null || ev.isNull()) {
            return new Evidence("", "", List.of());
        }
        return new Evidence(
                ev.hasNonNull("file") ? ev.get("file").asText() : "",
                ev.hasNonNull("lines") ? ev.get("lines").asText() : "",
                stringList(ev.get("calls")));
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*AnnotationJsonParserTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/AnnotationJsonParser.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/AnnotationJsonParserTest.java
git commit -m "feat(core): add tolerant annotation JSON parser" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: QwenAnnotator(prompt→chat→parse,失败降级)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/QwenAnnotator.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/QwenAnnotatorTest.java`

通过 `ChatFn` 接缝调用 LLM;任何异常(网络/解析)降级到注入的 `Annotator`(离线降级)。测试用桩 `ChatFn`,不联网。

- [ ] **Step 1: 写失败测试**

`QwenAnnotatorTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QwenAnnotatorTest {

    @Test
    void buildsPromptCallsChatAndParsesReply() {
        ChatFn stub = prompt -> """
                {"summary":"创建并保存商户","inputs":"cmd","outputs":"void",
                 "side_effects":["DB写"],
                 "evidence":{"file":"f","lines":"1-9","calls":["repo.save"]},
                 "dig_worthy":false,"dig_reason":null}
                """;
        QwenAnnotator annotator = new QwenAnnotator(stub, new FakeAnnotator());

        Annotation a = annotator.annotate(AnnotationRequest.ofSignature("com.example.S#m()"));

        assertEquals("创建并保存商户", a.summary());
        assertEquals("DB写", a.sideEffects().get(0));
        assertFalse(a.digWorthy());
    }

    @Test
    void fallsBackWhenChatThrows() {
        ChatFn boom = prompt -> { throw new RuntimeException("network down"); };
        QwenAnnotator annotator = new QwenAnnotator(boom, new FakeAnnotator());

        Annotation a = annotator.annotate(
                AnnotationRequest.ofSignature("com.example.S#recruit()"));

        // 降级到 FakeAnnotator:summary 含方法名
        assertTrue(a.summary().contains("recruit"), "应降级到 fake,实际:" + a.summary());
    }

    @Test
    void fallsBackWhenReplyUnparseable() {
        ChatFn garbage = prompt -> "这不是 JSON";
        QwenAnnotator annotator = new QwenAnnotator(garbage, new FakeAnnotator());

        Annotation a = annotator.annotate(AnnotationRequest.ofSignature("X#y()"));
        assertTrue(a.summary().contains("y"));
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*QwenAnnotatorTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`QwenAnnotator.java`:
```java
package com.threadmap.core.annotate;

import java.util.Objects;

/**
 * 真实 LLM 标注器:构 prompt → ChatFn.chat → 解析 JSON → Annotation。
 * 任何异常(网络/解析失败)降级到注入的 fallback 标注器(离线降级)。
 */
public class QwenAnnotator implements Annotator {
    private final ChatFn chat;
    private final PromptBuilder promptBuilder;
    private final AnnotationJsonParser jsonParser;
    private final Annotator fallback;

    public QwenAnnotator(ChatFn chat, Annotator fallback) {
        this(chat, new PromptBuilder(), new AnnotationJsonParser(), fallback);
    }

    QwenAnnotator(ChatFn chat, PromptBuilder promptBuilder,
                  AnnotationJsonParser jsonParser, Annotator fallback) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.jsonParser = Objects.requireNonNull(jsonParser, "jsonParser");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Annotation annotate(AnnotationRequest request) {
        try {
            String reply = chat.chat(promptBuilder.build(request));
            return jsonParser.parse(reply);
        } catch (RuntimeException e) {
            return fallback.annotate(request);
        }
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*QwenAnnotatorTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/QwenAnnotator.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/QwenAnnotatorTest.java
git commit -m "feat(core): add Qwen annotator with offline fallback" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: CachingAnnotator(方法体哈希缓存装饰器)

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/annotate/CachingAnnotator.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/annotate/CachingAnnotatorTest.java`

按方法体(`source`)哈希缓存;无源码时退化为按 `signature`。同输入命中缓存,不重复调底层(省 LLM 调用)。

- [ ] **Step 1: 写失败测试**

`CachingAnnotatorTest.java`:
```java
package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CachingAnnotatorTest {

    private static Annotation dummy() {
        return new Annotation("s", "i", "o", List.of(),
                new Evidence("f", "", List.of()), false, null);
    }

    @Test
    void cachesByMethodBodyAndAvoidsRedundantCalls() {
        AtomicInteger calls = new AtomicInteger();
        Annotator counting = req -> { calls.incrementAndGet(); return dummy(); };
        CachingAnnotator caching = new CachingAnnotator(counting);

        AnnotationRequest r = new AnnotationRequest("A#a()", "void a(){}", List.of());

        caching.annotate(r);
        caching.annotate(r);                      // 同方法体 → 命中缓存
        assertEquals(1, calls.get(), "同方法体应只调一次底层");

        // 方法体不同 → 重新调用
        caching.annotate(new AnnotationRequest("A#a()", "void a(){ x(); }", List.of()));
        assertEquals(2, calls.get());
    }

    @Test
    void fallsBackToSignatureKeyWhenNoSource() {
        AtomicInteger calls = new AtomicInteger();
        Annotator counting = req -> { calls.incrementAndGet(); return dummy(); };
        CachingAnnotator caching = new CachingAnnotator(counting);

        caching.annotate(AnnotationRequest.ofSignature("A#a()"));
        caching.annotate(AnnotationRequest.ofSignature("A#a()"));  // 同签名 → 命中
        assertEquals(1, calls.get());

        caching.annotate(AnnotationRequest.ofSignature("B#b()"));
        assertEquals(2, calls.get());
    }
}
```

- [ ] **Step 2: 运行,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*CachingAnnotatorTest'`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`CachingAnnotator.java`:
```java
package com.threadmap.core.annotate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 缓存装饰器:按方法体(source)哈希缓存标注;无源码时退化为按 signature。
 * 同一方法体不重复调用底层标注器(省 LLM 调用 / 费用)。
 */
public class CachingAnnotator implements Annotator {
    private final Annotator delegate;
    private final Map<String, Annotation> cache = new HashMap<>();

    public CachingAnnotator(Annotator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Annotation annotate(AnnotationRequest request) {
        String key = cacheKey(request);
        Annotation cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Annotation fresh = delegate.annotate(request);
        cache.put(key, fresh);
        return fresh;
    }

    private static String cacheKey(AnnotationRequest request) {
        String basis = request.source() != null && !request.source().isBlank()
                ? request.source()
                : request.signature();
        return sha256(basis);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

- [ ] **Step 4: 运行,确认通过 + 全量测试**

Run: `./gradlew :threadmap-core:test --tests '*CachingAnnotatorTest'`
Expected: PASS。
Run: `./gradlew :threadmap-core:test`
Expected: 全部测试 PASS(M1 + M2a + M2b-1)。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/annotate/CachingAnnotator.java \
        threadmap-core/src/test/java/com/threadmap/core/annotate/CachingAnnotatorTest.java
git commit -m "feat(core): add method-body-hash caching annotator" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 完成标准(M2b-1 Done）

- `./gradlew :threadmap-core:test` 全绿(全程离线,无网络调用)。
- 具备一条可组装的真实标注链:`new CachingAnnotator(new QwenAnnotator(QwenChatModels.create(key, "qwen3.6-flash"), new FakeAnnotator()))` —— 构 prompt、调 Qwen、解析 JSON、失败降级、按方法体缓存,全部经 `Annotator` 接口对接,未触碰 M2a 的流水/写出。

## 后续(M2b-2,另开计划)

- JavaParser 按 `signature` 在项目源码根里定位方法,抽取方法源码 + 直接被调方法签名,填充 `AnnotationRequest`。
- `AnnotationPipeline` 接线:注入源码抽取器构造带 source 的 `AnnotationRequest`,标注器换成上面的缓存+Qwen 链。
- CLI 入口:读 `DASHSCOPE_API_KEY`、默认 `qwen3.6-flash`,`trace.json` → `.threadmap/annotated-tree.json`。
- `TraceJsonParser` 字段校验加固(M2a 终审 minor:`entry_signature`/`captured_at` 缺失时给清晰 IOException)。
