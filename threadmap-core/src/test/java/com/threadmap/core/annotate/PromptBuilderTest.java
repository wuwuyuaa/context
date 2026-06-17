package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    @Test
    void includesSignatureSourceCalleesAndJsonFields() {
        AnnotationRequest req = new AnnotationRequest(
                "com.example.MerchantService#recruit(RecruitCommand)",
                "void recruit(RecruitCommand c) { repo.save(c); }",
                List.of("com.example.MerchantRepository#save(RecruitCommand)"));

        String prompt = new PromptBuilder().build(req);

        assertTrue(prompt.contains("com.example.MerchantService#recruit(RecruitCommand)"));
        assertTrue(prompt.contains("repo.save(c)"), "应含方法源码");
        assertTrue(prompt.contains("com.example.MerchantRepository#save(RecruitCommand)"), "应含被调签名");
        assertTrue(prompt.contains("summary"));
        assertTrue(prompt.contains("side_effects"));
        assertTrue(prompt.contains("evidence"));
        assertTrue(prompt.contains("dig_worthy"));
        assertTrue(prompt.contains("inputs"));
        assertTrue(prompt.contains("outputs"));
    }

    @Test
    void degradesGracefullyWhenNoSource() {
        AnnotationRequest req = AnnotationRequest.ofSignature("A#a()");
        String prompt = new PromptBuilder().build(req);

        assertTrue(prompt.contains("A#a()"));
        assertTrue(prompt.contains("源码不可用"), "无源码时应说明");
        assertTrue(prompt.contains("(无)"), "无被调方法时应渲染 (无)");
    }
}
