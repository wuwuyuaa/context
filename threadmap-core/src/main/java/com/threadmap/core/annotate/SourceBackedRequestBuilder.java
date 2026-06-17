package com.threadmap.core.annotate;

import java.util.Objects;

/** 用 MethodSourceExtractor 按签名抽取源码 + 被调名;抽不到时退化为仅签名。 */
public class SourceBackedRequestBuilder implements AnnotationRequestBuilder {
    private final MethodSourceExtractor extractor;

    public SourceBackedRequestBuilder(MethodSourceExtractor extractor) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
    }

    @Override
    public AnnotationRequest build(AnnotatedNode node) {
        String signature = node.getSignature();
        // 注:e.calleeNames() 是裸方法名(如 "save"),并非完整签名;当前填入 AnnotationRequest
        // 的 calleeSignatures 字段仅供 prompt 提示用。M4 前考虑升级为 symbol-resolved 签名。
        return extractor.extract(signature)
                .map(e -> new AnnotationRequest(signature, e.source(), e.calleeNames()))
                .orElseGet(() -> AnnotationRequest.ofSignature(signature));
    }
}
