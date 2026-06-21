package com.threadmap.core.annotate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private List<String> markers = List.of();            // 结构标签(Spring 注解:事务/异步/…),无需 LLM
    private String confidence = "";                       // 静态边可信度:""=确定,single_impl/multi_impl/unresolved
    private String sourceHash = "";                       // 标注时方法源码的 hash(持久化,过期检测用)
    private boolean stale;                                // 当前源码与 sourceHash 不符:标注可能过期(瞬态,不入 JSON)
    private final List<AnnotatedNode> children = new ArrayList<>();

    public AnnotatedNode(int id, String signature, String file, int line, long selfMs) {
        this.id = id;
        this.signature = signature;
        this.file = file;
        this.line = line;
        this.selfMs = selfMs;
    }

    public void addChild(AnnotatedNode child) { children.add(Objects.requireNonNull(child, "child must not be null")); }
    public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
    public void setAnnotation(Annotation annotation) { this.annotation = annotation; }
    public void setUnderstanding(Understanding understanding) { this.understanding = Objects.requireNonNull(understanding, "understanding must not be null"); }
    public void setMarkers(List<String> markers) { this.markers = markers == null ? List.of() : List.copyOf(markers); }
    public void setConfidence(String confidence) { this.confidence = confidence == null ? "" : confidence; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash == null ? "" : sourceHash; }
    public void setStale(boolean stale) { this.stale = stale; }

    public int getId() { return id; }
    public String getSignature() { return signature; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    public long getSelfMs() { return selfMs; }
    public boolean isCollapsed() { return collapsed; }
    public Annotation getAnnotation() { return annotation; }
    public Understanding getUnderstanding() { return understanding; }
    public List<String> getMarkers() { return markers; }
    public String getConfidence() { return confidence; }
    public String getSourceHash() { return sourceHash; }
    public boolean isStale() { return stale; }
    public List<AnnotatedNode> getChildren() { return Collections.unmodifiableList(children); }
}
