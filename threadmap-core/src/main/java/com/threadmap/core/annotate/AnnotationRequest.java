package com.threadmap.core.annotate;

import java.util.List;

/**
 * 标注请求:方法签名 + (可选)方法源码 + 直接被调方法签名。
 * M2a 只用 signature;M2b 填充 source / calleeSignatures 供真实 LLM 标注。
 */
public record AnnotationRequest(String signature, String source, List<String> calleeSignatures) {
    public static AnnotationRequest ofSignature(String signature) {
        return new AnnotationRequest(signature, null, List.of());
    }
}
