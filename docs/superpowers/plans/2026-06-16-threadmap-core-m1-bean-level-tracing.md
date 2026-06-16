# Threadmap Core M1 — Bean 级动态追踪 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在新建的 `threadmap-core` 模块里实现一个 Spring AOP 的 Bean 级追踪器,由测试触发跑一次真实调用,把这一次走过的调用树导出为 `trace.json`。

**Architecture:** 纯 JVM 库(Java),零 IntelliJ 依赖。一个线程内栈式 `TraceRecorder` 记录进入/退出的节点并组成树;一个 Spring `@Around` 切面在 Bean 间方法调用上调用 recorder;一个 `TraceJsonWriter` 把树序列化成约定 schema。用一个 test 作用域里的小 Spring Boot 应用做端到端验证。

**Tech Stack:** Java 17、Gradle(wrapper)、Spring AOP / AspectJ、Jackson、JUnit 5、Spring Boot 3.2(仅测试作用域,用于织入与 fixture)。

**这是 3 份计划中的第 1 份:** M1 = core 追踪(本文档)→ M2 = core 折叠+绑证据 LLM 标注 → M3 = IntelliJ 插件渲染。

**本计划的范围边界(对齐 spec §9):**
- 只做 **测试触发** 这一种采集方式(MVP 默认)。java-agent / OTel 适配器留到后续。
- 只做 **Bean 级**(AOP 拦 Bean 间调用)。方法级须 java-agent,不在此。
- 已知并接受的限制:AOP 拦不到 Bean 内自调用与普通对象;跨线程的异步分支不在本次捕获内(单线程请求路径)。`line` 行号留待下游(插件用 PSI 按签名反查),trace 里记 `0`。

---

## 文件结构

```
<repo root>/
  settings.gradle                                  # 创建:多模块根,include ':threadmap-core'
  gradlew, gradlew.bat, gradle/wrapper/...         # 创建:Gradle wrapper
  threadmap-core/
    build.gradle                                   # 创建:java-library + spring/jackson 依赖
    src/main/java/com/threadmap/core/
      trace/TraceNode.java                         # 创建:树节点(id/signature/file/line/elapsed/children)
      trace/Trace.java                             # 创建:一次追踪(入口签名 + 时间 + root)
      trace/TraceRecorder.java                     # 创建:线程内栈式记录器(start/stop/enter/exit)
      trace/SignatureFormatter.java                # 创建:Method → "FQN#方法(简单参数类型)"
      trace/SourceLocation.java                    # 创建:Class → 源码相对路径
      trace/TraceJsonWriter.java                   # 创建:Trace → trace.json 字符串
      aspect/ThreadmapAspect.java                  # 创建:Spring @Around 切面(Task 6 stub → Task 7 实现)
    src/test/java/com/threadmap/core/
      trace/TraceRecorderTest.java                 # 创建
      trace/SignatureFormatterTest.java            # 创建
      trace/SourceLocationTest.java                # 创建
      trace/TraceJsonWriterTest.java               # 创建
      integration/SampleComponents.java            # 创建:@RestController/@Service/@Repository fixture
      integration/SampleApp.java                   # 创建:@SpringBootApplication + 切面/recorder Bean
      integration/BeanLevelTraceIntegrationTest.java  # 创建:端到端验证调用树 + 出 trace.json
```

每个文件单一职责:模型、记录、格式化、序列化、织入分开;测试与被测同包不同源根。

---

## Task 1: 脚手架 — Gradle 多模块 + threadmap-core 模块

**Files:**
- Create: `settings.gradle`
- Create: `threadmap-core/build.gradle`
- Create: Gradle wrapper(`gradlew`, `gradle/wrapper/*`)

- [ ] **Step 1: 初始化 git(本目录尚不是 git 仓库)**

Run:
```bash
git init
printf '.gradle/\nbuild/\n.idea/\n.threadmap/\n' > .gitignore
```

- [ ] **Step 2: 写根 `settings.gradle`**

`settings.gradle`:
```groovy
rootProject.name = 'threadmap'
include ':threadmap-core'
```

- [ ] **Step 3: 写 `threadmap-core/build.gradle`**

`threadmap-core/build.gradle`:
```groovy
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.threadmap'
version = '0.1.0-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:3.2.5"
    }
}

dependencies {
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver'
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    testImplementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

test { useJUnitPlatform() }
```

- [ ] **Step 4: 生成 Gradle wrapper**

Run:
```bash
gradle wrapper --gradle-version 8.7
```
Expected: 生成 `gradlew`、`gradle/wrapper/gradle-wrapper.properties` 等。
(若本机没有 `gradle`:`brew install gradle` 后重试。)

- [ ] **Step 5: 验证空构建通过**

Run:
```bash
./gradlew :threadmap-core:build
```
Expected: `BUILD SUCCESSFUL`(无源码,仅校验脚手架可构建)。

- [ ] **Step 6: 提交**

```bash
git add settings.gradle threadmap-core/build.gradle gradlew gradlew.bat gradle .gitignore
git commit -m "chore: scaffold threadmap-core gradle module"
```

---

## Task 2: 追踪树模型 TraceNode + Trace

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/TraceNode.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/Trace.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/trace/TraceNodeTest.java`

- [ ] **Step 1: 写失败测试**

`threadmap-core/src/test/java/com/threadmap/core/trace/TraceNodeTest.java`:
```java
package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceNodeTest {

    @Test
    void selfMsExcludesChildrenElapsed() {
        TraceNode root = new TraceNode(0, "A#a()", "A.java", 0);
        root.setElapsedMs(100);
        TraceNode child = new TraceNode(1, "B#b()", "B.java", 0);
        child.setElapsedMs(30);
        root.addChild(child);

        assertEquals(70, root.selfMs());
        assertEquals(30, child.selfMs());
        assertEquals(1, root.getChildren().size());
    }

    @Test
    void selfMsNeverNegative() {
        TraceNode root = new TraceNode(0, "A#a()", "A.java", 0);
        root.setElapsedMs(10);
        TraceNode child = new TraceNode(1, "B#b()", "B.java", 0);
        child.setElapsedMs(50); // 子节点计时大于父(时钟抖动)
        root.addChild(child);

        assertEquals(0, root.selfMs());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*TraceNodeTest'`
Expected: 编译失败(`TraceNode` 不存在)。

- [ ] **Step 3: 写最小实现**

`threadmap-core/src/main/java/com/threadmap/core/trace/TraceNode.java`:
```java
package com.threadmap.core.trace;

import java.util.ArrayList;
import java.util.List;

/** 调用树的一个节点;一个 Bean 方法调用对应一个节点。 */
public class TraceNode {
    private final int id;
    private final String signature;
    private final String file;
    private final int line;            // 0 = 未解析(下游用 PSI 按签名反查)
    private long elapsedMs;             // 含子节点的总耗时
    private final List<TraceNode> children = new ArrayList<>();

    public TraceNode(int id, String signature, String file, int line) {
        this.id = id;
        this.signature = signature;
        this.file = file;
        this.line = line;
    }

    public void addChild(TraceNode child) { children.add(child); }
    public void setElapsedMs(long ms) { this.elapsedMs = ms; }

    /** 自身耗时 = 总耗时 - 直接子节点总耗时,负数归零。 */
    public long selfMs() {
        long childSum = 0;
        for (TraceNode c : children) childSum += c.elapsedMs;
        return Math.max(0, elapsedMs - childSum);
    }

    public int getId() { return id; }
    public String getSignature() { return signature; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    public long getElapsedMs() { return elapsedMs; }
    public List<TraceNode> getChildren() { return children; }
}
```

`threadmap-core/src/main/java/com/threadmap/core/trace/Trace.java`:
```java
package com.threadmap.core.trace;

/** 一次追踪:入口签名 + 采集时间 + 调用树根节点。 */
public class Trace {
    private final String entrySignature;
    private final String capturedAt;   // ISO-8601
    private final TraceNode root;

    public Trace(String entrySignature, String capturedAt, TraceNode root) {
        this.entrySignature = entrySignature;
        this.capturedAt = capturedAt;
        this.root = root;
    }

    public String getEntrySignature() { return entrySignature; }
    public String getCapturedAt() { return capturedAt; }
    public TraceNode getRoot() { return root; }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*TraceNodeTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/trace/TraceNode.java \
        threadmap-core/src/main/java/com/threadmap/core/trace/Trace.java \
        threadmap-core/src/test/java/com/threadmap/core/trace/TraceNodeTest.java
git commit -m "feat(core): add trace tree model"
```

---

## Task 3: 线程内栈式记录器 TraceRecorder

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/TraceRecorder.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/trace/TraceRecorderTest.java`

- [ ] **Step 1: 写失败测试**

`threadmap-core/src/test/java/com/threadmap/core/trace/TraceRecorderTest.java`:
```java
package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceRecorderTest {

    @Test
    void buildsNestedTreeWithTiming() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("A#a()", "A.java", 0);
        r.enter("B#b()", "B.java", 0);
        r.exit(30);
        r.exit(100);
        r.stop();

        TraceNode root = r.getRoot();
        assertNotNull(root);
        assertEquals("A#a()", root.getSignature());
        assertEquals(1, root.getChildren().size());
        assertEquals("B#b()", root.getChildren().get(0).getSignature());
        assertEquals(70, root.selfMs());
        assertEquals(30, root.getChildren().get(0).selfMs());
    }

    @Test
    void assignsIncrementingIdsInEnterOrder() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("A#a()", "A.java", 0);
        r.enter("B#b()", "B.java", 0);
        r.exit(1);
        r.exit(2);
        r.stop();

        assertEquals(0, r.getRoot().getId());
        assertEquals(1, r.getRoot().getChildren().get(0).getId());
    }

    @Test
    void recordsNothingWhenNotRecording() {
        TraceRecorder r = new TraceRecorder();
        r.enter("X#x()", "X.java", 0);
        r.exit(5);
        assertNull(r.getRoot());
    }

    @Test
    void startResetsPreviousRun() {
        TraceRecorder r = new TraceRecorder();
        r.start();
        r.enter("Old#old()", "Old.java", 0);
        r.exit(1);
        r.stop();

        r.start();
        r.enter("New#fresh()", "New.java", 0);
        r.exit(1);
        r.stop();

        assertEquals("New#fresh()", r.getRoot().getSignature());
        assertEquals(0, r.getRoot().getId());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*TraceRecorderTest'`
Expected: 编译失败(`TraceRecorder` 不存在)。

- [ ] **Step 3: 写最小实现**

`threadmap-core/src/main/java/com/threadmap/core/trace/TraceRecorder.java`:
```java
package com.threadmap.core.trace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程内栈式记录器:enter 压栈并挂到父节点,exit 出栈并记耗时。
 * M1 单线程请求路径;跨线程异步分支不在本次捕获范围内。
 */
public class TraceRecorder {
    private final ThreadLocal<Deque<TraceNode>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicInteger ids = new AtomicInteger(0);
    private volatile boolean recording = false;
    private volatile TraceNode root;

    public void start() {
        recording = true;
        root = null;
        ids.set(0);
        stack.get().clear();
    }

    public void stop() { recording = false; }

    public boolean isRecording() { return recording; }

    public TraceNode getRoot() { return root; }

    public void enter(String signature, String file, int line) {
        if (!recording) return;
        TraceNode node = new TraceNode(ids.getAndIncrement(), signature, file, line);
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) {
            root = node;
        } else {
            s.peek().addChild(node);
        }
        s.push(node);
    }

    public void exit(long elapsedMs) {
        if (!recording) return;
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) return;
        s.pop().setElapsedMs(elapsedMs);
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*TraceRecorderTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/trace/TraceRecorder.java \
        threadmap-core/src/test/java/com/threadmap/core/trace/TraceRecorderTest.java
git commit -m "feat(core): add thread-local trace recorder"
```

---

## Task 4: 签名格式化 + 源码路径推导

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/SignatureFormatter.java`
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/SourceLocation.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/trace/SignatureFormatterTest.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/trace/SourceLocationTest.java`

- [ ] **Step 1: 写失败测试**

`threadmap-core/src/test/java/com/threadmap/core/trace/SignatureFormatterTest.java`:
```java
package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class SignatureFormatterTest {

    static class Sample {
        public String greet(String name, int times) { return null; }
        public void noArgs() { }
    }

    @Test
    void formatsFqnHashMethodWithSimpleParamTypes() throws Exception {
        Method m = Sample.class.getMethod("greet", String.class, int.class);
        assertEquals(
            "com.threadmap.core.trace.SignatureFormatterTest$Sample#greet(String, int)",
            SignatureFormatter.format(m));
    }

    @Test
    void formatsNoArgMethodWithEmptyParens() throws Exception {
        Method m = Sample.class.getMethod("noArgs");
        assertEquals(
            "com.threadmap.core.trace.SignatureFormatterTest$Sample#noArgs()",
            SignatureFormatter.format(m));
    }
}
```

`threadmap-core/src/test/java/com/threadmap/core/trace/SourceLocationTest.java`:
```java
package com.threadmap.core.trace;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceLocationTest {

    static class Inner { }

    @Test
    void derivesSourceRelativePathFromClass() {
        assertEquals("com/threadmap/core/trace/SourceLocation.java",
                SourceLocation.fileFor(SourceLocation.class));
    }

    @Test
    void usesOuterClassForNestedTypes() {
        assertEquals("com/threadmap/core/trace/SourceLocationTest.java",
                SourceLocation.fileFor(Inner.class));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*SignatureFormatterTest' --tests '*SourceLocationTest'`
Expected: 编译失败(两个类不存在)。

- [ ] **Step 3: 写最小实现**

`threadmap-core/src/main/java/com/threadmap/core/trace/SignatureFormatter.java`:
```java
package com.threadmap.core.trace;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 把一个方法格式化为 "全限定类名#方法名(简单参数类型, ...)"。 */
public final class SignatureFormatter {
    private SignatureFormatter() { }

    public static String format(Method m) {
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return m.getDeclaringClass().getName() + "#" + m.getName() + "(" + params + ")";
    }
}
```

`threadmap-core/src/main/java/com/threadmap/core/trace/SourceLocation.java`:
```java
package com.threadmap.core.trace;

/** 由类推导源码相对路径(行号留待下游用 PSI 按签名反查)。 */
public final class SourceLocation {
    private SourceLocation() { }

    public static String fileFor(Class<?> type) {
        String name = type.getName();
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            name = name.substring(0, dollar); // 内部类归到外层类的源文件
        }
        return name.replace('.', '/') + ".java";
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*SignatureFormatterTest' --tests '*SourceLocationTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/trace/SignatureFormatter.java \
        threadmap-core/src/main/java/com/threadmap/core/trace/SourceLocation.java \
        threadmap-core/src/test/java/com/threadmap/core/trace/SignatureFormatterTest.java \
        threadmap-core/src/test/java/com/threadmap/core/trace/SourceLocationTest.java
git commit -m "feat(core): add signature and source-location helpers"
```

---

## Task 5: trace.json 序列化 TraceJsonWriter

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/trace/TraceJsonWriter.java`
- Test: `threadmap-core/src/test/java/com/threadmap/core/trace/TraceJsonWriterTest.java`

- [ ] **Step 1: 写失败测试**

`threadmap-core/src/test/java/com/threadmap/core/trace/TraceJsonWriterTest.java`:
```java
package com.threadmap.core.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceJsonWriterTest {

    @Test
    void serializesTreeWithSnakeCaseFields() throws Exception {
        TraceNode root = new TraceNode(0, "A#a()", "com/example/A.java", 0);
        root.setElapsedMs(100);
        TraceNode child = new TraceNode(1, "B#b()", "com/example/B.java", 12);
        child.setElapsedMs(30);
        root.addChild(child);
        Trace trace = new Trace("A#a()", "2026-06-16T00:00:00Z", root);

        String json = new TraceJsonWriter().toJson(trace);
        JsonNode parsed = new ObjectMapper().readTree(json);

        assertEquals("A#a()", parsed.get("entry_signature").asText());
        assertEquals("2026-06-16T00:00:00Z", parsed.get("captured_at").asText());

        JsonNode r = parsed.get("root");
        assertEquals(0, r.get("id").asInt());
        assertEquals("A#a()", r.get("signature").asText());
        assertEquals("com/example/A.java", r.get("file").asText());
        assertEquals(0, r.get("line").asInt());
        assertEquals(70, r.get("self_ms").asInt());

        JsonNode c = r.get("children").get(0);
        assertEquals(1, c.get("id").asInt());
        assertEquals("B#b()", c.get("signature").asText());
        assertEquals(12, c.get("line").asInt());
        assertEquals(30, c.get("self_ms").asInt());
        assertTrue(c.get("children").isArray());
        assertEquals(0, c.get("children").size());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `./gradlew :threadmap-core:test --tests '*TraceJsonWriterTest'`
Expected: 编译失败(`TraceJsonWriter` 不存在)。

- [ ] **Step 3: 写最小实现**

`threadmap-core/src/main/java/com/threadmap/core/trace/TraceJsonWriter.java`:
```java
package com.threadmap.core.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 把 Trace 序列化成约定 schema 的 trace.json(显式 snake_case 字段)。 */
public class TraceJsonWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(Trace trace) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();
        root.put("entry_signature", trace.getEntrySignature());
        root.put("captured_at", trace.getCapturedAt());
        root.set("root", node(trace.getRoot()));
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private ObjectNode node(TraceNode n) {
        ObjectNode o = mapper.createObjectNode();
        o.put("id", n.getId());
        o.put("signature", n.getSignature());
        o.put("file", n.getFile());
        o.put("line", n.getLine());
        o.put("self_ms", n.selfMs());
        ArrayNode children = o.putArray("children");
        for (TraceNode c : n.getChildren()) {
            children.add(node(c));
        }
        return o;
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `./gradlew :threadmap-core:test --tests '*TraceJsonWriterTest'`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/trace/TraceJsonWriter.java \
        threadmap-core/src/test/java/com/threadmap/core/trace/TraceJsonWriterTest.java
git commit -m "feat(core): add trace.json writer"
```

---

## Task 6: Spring fixture + 集成测试(RED)

本任务建立 test 作用域的小 Spring Boot 应用与端到端测试,并放一个**空操作**的切面 stub,使测试能编译并因"没有记录到调用树"而失败。

**Files:**
- Create: `threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java`(stub)
- Create: `threadmap-core/src/test/java/com/threadmap/core/integration/SampleComponents.java`
- Create: `threadmap-core/src/test/java/com/threadmap/core/integration/SampleApp.java`
- Create: `threadmap-core/src/test/java/com/threadmap/core/integration/BeanLevelTraceIntegrationTest.java`

- [ ] **Step 1: 写切面 stub(只透传,不记录)**

`threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java`:
```java
package com.threadmap.core.aspect;

import com.threadmap.core.trace.TraceRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/** Bean 级追踪切面。Task 6 为 stub(透传);Task 7 实现记录逻辑。 */
@Aspect
public class ThreadmapAspect {
    private final TraceRecorder recorder;

    public ThreadmapAspect(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@within(org.springframework.stereotype.Service) || "
          + "@within(org.springframework.stereotype.Repository) || "
          + "@within(org.springframework.stereotype.Component) || "
          + "@within(org.springframework.stereotype.Controller) || "
          + "@within(org.springframework.web.bind.annotation.RestController)")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        // Task 7 在此实现 enter/exit;当前透传。
        return pjp.proceed();
    }
}
```

- [ ] **Step 2: 写 fixture 组件**

`threadmap-core/src/test/java/com/threadmap/core/integration/SampleComponents.java`:
```java
package com.threadmap.core.integration;

import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Repository
class SampleRepository {
    String load() { return "row"; }
}

@Service
class SampleService {
    private final SampleRepository repository;
    SampleService(SampleRepository repository) { this.repository = repository; }
    String doWork() { return repository.load().toUpperCase(); }
}

@RestController
class SampleController {
    private final SampleService service;
    SampleController(SampleService service) { this.service = service; }

    @GetMapping("/sample")
    String handle() { return service.doWork(); }
}
```

- [ ] **Step 3: 写测试 Spring 应用(注册 recorder + 切面 + 开启 AOP)**

`threadmap-core/src/test/java/com/threadmap/core/integration/SampleApp.java`:
```java
package com.threadmap.core.integration;

import com.threadmap.core.aspect.ThreadmapAspect;
import com.threadmap.core.trace.TraceRecorder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
class SampleApp {

    @Bean
    TraceRecorder traceRecorder() { return new TraceRecorder(); }

    @Bean
    ThreadmapAspect threadmapAspect(TraceRecorder recorder) {
        return new ThreadmapAspect(recorder);
    }
}
```

- [ ] **Step 4: 写端到端测试**

`threadmap-core/src/test/java/com/threadmap/core/integration/BeanLevelTraceIntegrationTest.java`:
```java
package com.threadmap.core.integration;

import com.threadmap.core.trace.Trace;
import com.threadmap.core.trace.TraceJsonWriter;
import com.threadmap.core.trace.TraceNode;
import com.threadmap.core.trace.TraceRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BeanLevelTraceIntegrationTest {

    @Autowired
    SampleController controller;

    @Autowired
    TraceRecorder recorder;

    @Test
    void capturesBeanLevelCallTreeAndWritesTraceJson(@TempDir Path tmp) throws Exception {
        recorder.start();
        controller.handle();
        recorder.stop();

        TraceNode root = recorder.getRoot();
        assertNotNull(root, "切面应当记录到一个根节点");
        assertTrue(root.getSignature().endsWith("SampleController#handle()"),
                "根应为控制器入口,实际:" + root.getSignature());

        assertEquals(1, root.getChildren().size());
        TraceNode service = root.getChildren().get(0);
        assertTrue(service.getSignature().contains("SampleService#doWork"),
                "实际:" + service.getSignature());

        assertEquals(1, service.getChildren().size());
        TraceNode repo = service.getChildren().get(0);
        assertTrue(repo.getSignature().contains("SampleRepository#load"),
                "实际:" + repo.getSignature());

        Path out = tmp.resolve("trace.json");
        String json = new TraceJsonWriter()
                .toJson(new Trace(root.getSignature(), Instant.now().toString(), root));
        Files.writeString(out, json);
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 0);
    }
}
```

- [ ] **Step 5: 运行测试,确认失败(RED)**

Run: `./gradlew :threadmap-core:test --tests '*BeanLevelTraceIntegrationTest'`
Expected: FAIL —— 断言 `切面应当记录到一个根节点` 失败,`root` 为 `null`(stub 切面未记录)。

- [ ] **Step 6: 提交(红)**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java \
        threadmap-core/src/test/java/com/threadmap/core/integration/
git commit -m "test(core): add bean-level tracing integration test (red)"
```

---

## Task 7: 实现 Bean 级追踪切面(GREEN)

**Files:**
- Modify: `threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java`

- [ ] **Step 1: 在 advice 里实现 enter/exit 记录**

把 `ThreadmapAspect.java` 的 `trace` 方法体替换为完整实现(其余不变):

`threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java`:
```java
package com.threadmap.core.aspect;

import com.threadmap.core.trace.SignatureFormatter;
import com.threadmap.core.trace.SourceLocation;
import com.threadmap.core.trace.TraceRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/** Bean 级追踪切面:在 Bean 间方法调用上记录 enter/exit,组成调用树。 */
@Aspect
public class ThreadmapAspect {
    private final TraceRecorder recorder;

    public ThreadmapAspect(TraceRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@within(org.springframework.stereotype.Service) || "
          + "@within(org.springframework.stereotype.Repository) || "
          + "@within(org.springframework.stereotype.Component) || "
          + "@within(org.springframework.stereotype.Controller) || "
          + "@within(org.springframework.web.bind.annotation.RestController)")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        if (!recorder.isRecording()) {
            return pjp.proceed();
        }
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        String signature = SignatureFormatter.format(ms.getMethod());
        String file = SourceLocation.fileFor(ms.getDeclaringType());
        recorder.enter(signature, file, 0);
        long startNs = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            recorder.exit((System.nanoTime() - startNs) / 1_000_000);
        }
    }
}
```

- [ ] **Step 2: 运行集成测试,确认通过(GREEN)**

Run: `./gradlew :threadmap-core:test --tests '*BeanLevelTraceIntegrationTest'`
Expected: PASS —— 调用树为 `SampleController#handle()` → `SampleService#doWork()` → `SampleRepository#load()`,并写出 `trace.json`。

- [ ] **Step 3: 跑全部测试**

Run: `./gradlew :threadmap-core:test`
Expected: 所有测试 PASS。

- [ ] **Step 4: 提交(绿)**

```bash
git add threadmap-core/src/main/java/com/threadmap/core/aspect/ThreadmapAspect.java
git commit -m "feat(core): implement bean-level tracing aspect (green)"
```

---

## 完成标准(M1 Done)

- `./gradlew :threadmap-core:test` 全绿。
- 集成测试证明:测试触发一次 Bean 间调用,捕获到正确嵌套的调用树,并序列化为符合 schema 的 `trace.json`(字段:`entry_signature` / `captured_at` / `root{ id, signature, file, line, self_ms, children[] }`)。
- 输出的 `trace.json` 即 M2(折叠 + 绑证据标注)的输入。

## 后续(不在本计划)

- **M2:** 包名折叠 + 逐节点绑证据 LLM 标注 + 哈希缓存 → `annotated-tree.json`(单独计划)。
- **M3:** IntelliJ 插件渲染(单独计划)。
- 采集扩展:java-agent 兜底(方法级)、OTel 复用、header/入口过滤隔离——按需另开。
