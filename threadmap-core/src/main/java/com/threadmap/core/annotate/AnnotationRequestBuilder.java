package com.threadmap.core.annotate;

/** 把一个节点变成标注请求。默认仅签名;源码版填充 source + 被调名(M2b-2)。 */
@FunctionalInterface
public interface AnnotationRequestBuilder {
    AnnotationRequest build(AnnotatedNode node);
}
