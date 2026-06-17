package com.threadmap.core.annotate;

import java.util.List;

/**
 * 离线占位标注器:不调用任何外部服务,从签名派生确定性标注。
 * 用途:测试、离线模式、以及 LLM 不可用时的降级路径。
 */
public class FakeAnnotator implements Annotator {

    @Override
    public Annotation annotate(AnnotationRequest request) {
        String signature = request.signature();
        return new Annotation(
                "调用 " + methodName(signature),
                "—",
                "—",
                List.of(),
                new Evidence(fileOf(signature), "", List.of()),
                false,
                null);
    }

    private static String methodName(String signature) {
        int hash = signature.indexOf('#');
        int paren = signature.indexOf('(', hash + 1);
        if (hash >= 0 && paren > hash) {
            return signature.substring(hash + 1, paren);
        }
        return signature;
    }

    private static String fileOf(String signature) {
        int hash = signature.indexOf('#');
        String fqcn = hash >= 0 ? signature.substring(0, hash) : signature;
        int dollar = fqcn.indexOf('$');
        if (dollar >= 0) {
            fqcn = fqcn.substring(0, dollar);
        }
        return fqcn.replace('.', '/') + ".java";
    }
}
