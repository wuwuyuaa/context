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
