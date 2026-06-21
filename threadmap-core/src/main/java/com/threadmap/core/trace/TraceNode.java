package com.threadmap.core.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 调用树的一个节点;一个 Bean 方法调用对应一个节点。 */
public class TraceNode {
    private final int id;
    private final String signature;
    private final String file;
    private final int line;            // 0 = 未解析(下游用 PSI 按签名反查)
    private long elapsedMs;             // 含子节点的总耗时
    private List<String> markers = List.of();  // 结构标签(静态走链时由 PSI 读 Spring 注解填:事务/异步/…)
    private final List<TraceNode> children = new ArrayList<>();

    public TraceNode(int id, String signature, String file, int line) {
        this.id = id;
        this.signature = signature;
        this.file = file;
        this.line = line;
    }

    public void addChild(TraceNode child) {
        children.add(Objects.requireNonNull(child, "child must not be null"));
    }
    public void setElapsedMs(long ms) { this.elapsedMs = ms; }
    public void setMarkers(List<String> markers) { this.markers = markers == null ? List.of() : List.copyOf(markers); }

    /** 自身耗时 = 总耗时 - 直接子节点总耗时,负数归零。 */
    public long selfMs() {
        long childSum = 0;
        for (TraceNode c : children) childSum += c.getElapsedMs();
        return Math.max(0, elapsedMs - childSum);
    }

    public int getId() { return id; }
    public String getSignature() { return signature; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    public long getElapsedMs() { return elapsedMs; }
    public List<String> getMarkers() { return markers; }
    public List<TraceNode> getChildren() {
        return Collections.unmodifiableList(children);
    }
}
