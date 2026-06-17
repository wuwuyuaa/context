package com.threadmap.core.annotate;

import java.util.Objects;

/** 一棵标注树:入口签名 + 采集时间 + 根节点。 */
public class AnnotatedTree {
    private final String entrySignature;
    private final String capturedAt;
    private final AnnotatedNode root;

    public AnnotatedTree(String entrySignature, String capturedAt, AnnotatedNode root) {
        this.entrySignature = entrySignature;
        this.capturedAt = capturedAt;
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    public String getEntrySignature() { return entrySignature; }
    public String getCapturedAt() { return capturedAt; }
    public AnnotatedNode getRoot() { return root; }
}
