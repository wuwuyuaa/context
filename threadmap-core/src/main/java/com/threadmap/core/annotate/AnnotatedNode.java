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
