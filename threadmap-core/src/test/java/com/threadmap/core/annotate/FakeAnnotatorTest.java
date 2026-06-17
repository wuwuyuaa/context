package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FakeAnnotatorTest {

    @Test
    void derivesDeterministicAnnotationFromSignature() {
        Annotator annotator = new FakeAnnotator();
        AnnotationRequest req = AnnotationRequest.ofSignature(
                "com.example.MerchantService#recruit(RecruitCommand)");

        Annotation a = annotator.annotate(req);

        assertTrue(a.summary().contains("recruit"), "summary 应含方法名,实际:" + a.summary());
        assertNotNull(a.evidence());
        assertEquals("com/example/MerchantService.java", a.evidence().file());
        assertFalse(a.digWorthy());
        assertTrue(a.sideEffects().isEmpty());

        // 确定性:同输入同输出
        Annotation b = annotator.annotate(req);
        assertEquals(a.summary(), b.summary());
    }

    @Test
    void ofSignatureLeavesSourceAndCalleesEmpty() {
        AnnotationRequest req = AnnotationRequest.ofSignature("A#a()");
        assertEquals("A#a()", req.signature());
        assertNull(req.source());
        assertTrue(req.calleeSignatures().isEmpty());
    }
}
