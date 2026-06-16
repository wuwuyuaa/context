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
