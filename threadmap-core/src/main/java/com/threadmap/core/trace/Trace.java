package com.threadmap.core.trace;

/** 一次追踪:入口签名 + 采集时间 + 调用树根节点。 */
public class Trace {
    private final String entrySignature;
    private final String capturedAt;   // ISO-8601
    private final TraceNode root;

    /**
     * @param entrySignature signature of the entry method
     * @param capturedAt     ISO-8601 instant string, e.g. {@code 2026-06-16T00:00:00Z}
     * @param root           root node of the captured call tree
     */
    public Trace(String entrySignature, String capturedAt, TraceNode root) {
        this.entrySignature = entrySignature;
        this.capturedAt = capturedAt;
        this.root = root;
    }

    public String getEntrySignature() { return entrySignature; }
    public String getCapturedAt() { return capturedAt; }
    public TraceNode getRoot() { return root; }
}
