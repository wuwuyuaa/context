package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CachingAnnotatorTest {

    private static Annotation dummy() {
        return new Annotation("s", "i", "o", List.of(),
                new Evidence("f", "", List.of()), false, null);
    }

    @Test
    void rejectsNullDelegate() {
        assertThrows(NullPointerException.class, () -> new CachingAnnotator(null));
    }

    @Test
    void cachesByMethodBodyAndAvoidsRedundantCalls() {
        AtomicInteger calls = new AtomicInteger();
        Annotator counting = req -> { calls.incrementAndGet(); return dummy(); };
        CachingAnnotator caching = new CachingAnnotator(counting);

        AnnotationRequest r = new AnnotationRequest("A#a()", "void a(){}", List.of());

        caching.annotate(r);
        caching.annotate(r);                      // 同方法体 → 命中缓存
        assertEquals(1, calls.get(), "同方法体应只调一次底层");

        // 方法体不同 → 重新调用
        caching.annotate(new AnnotationRequest("A#a()", "void a(){ x(); }", List.of()));
        assertEquals(2, calls.get());
    }

    @Test
    void fallsBackToSignatureKeyWhenNoSource() {
        AtomicInteger calls = new AtomicInteger();
        Annotator counting = req -> { calls.incrementAndGet(); return dummy(); };
        CachingAnnotator caching = new CachingAnnotator(counting);

        caching.annotate(AnnotationRequest.ofSignature("A#a()"));
        caching.annotate(AnnotationRequest.ofSignature("A#a()"));  // 同签名 → 命中
        assertEquals(1, calls.get());

        caching.annotate(AnnotationRequest.ofSignature("B#b()"));
        assertEquals(2, calls.get());
    }
}
