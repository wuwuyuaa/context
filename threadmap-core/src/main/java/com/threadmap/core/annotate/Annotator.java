package com.threadmap.core.annotate;

/** 把一个节点标注成结构化 Annotation。实现可为离线 Fake 或真实 LLM(M2b)。 */
public interface Annotator {
    Annotation annotate(AnnotationRequest request);
}
