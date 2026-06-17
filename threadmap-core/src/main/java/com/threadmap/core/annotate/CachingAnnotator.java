package com.threadmap.core.annotate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 缓存装饰器:按方法体(source)哈希缓存标注;无源码时退化为按 signature。
 * 同一方法体不重复调用底层标注器(省 LLM 调用 / 费用)。
 */
public class CachingAnnotator implements Annotator {
    private final Annotator delegate;
    private final Map<String, Annotation> cache = new HashMap<>();

    public CachingAnnotator(Annotator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Annotation annotate(AnnotationRequest request) {
        String key = cacheKey(request);
        Annotation cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Annotation fresh = Objects.requireNonNull(
                delegate.annotate(request), "delegate returned null annotation");
        cache.put(key, fresh);
        return fresh;
    }

    private static String cacheKey(AnnotationRequest request) {
        // calleeSignatures 不参与 key:它们由 source 解析得出(同 source ⇒ 同 callees),
        // 故方法体(source)哈希已能唯一确定 prompt;无 source 时退化为 signature。
        String basis = request.source() != null && !request.source().isBlank()
                ? request.source()
                : request.signature();
        return sha256(basis);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
