package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QwenAnnotatorTest {

    @Test
    void buildsPromptCallsChatAndParsesReply() {
        ChatFn stub = prompt -> """
                {"summary":"创建并保存商户","inputs":"cmd","outputs":"void",
                 "side_effects":["DB写"],
                 "evidence":{"file":"f","lines":"1-9","calls":["repo.save"]},
                 "dig_worthy":false,"dig_reason":null}
                """;
        QwenAnnotator annotator = new QwenAnnotator(stub, new FakeAnnotator());

        Annotation a = annotator.annotate(AnnotationRequest.ofSignature("com.example.S#m()"));

        assertEquals("创建并保存商户", a.summary());
        assertEquals("DB写", a.sideEffects().get(0));
        assertFalse(a.digWorthy());
    }

    @Test
    void fallsBackWhenChatThrows() {
        ChatFn boom = prompt -> { throw new RuntimeException("network down"); };
        QwenAnnotator annotator = new QwenAnnotator(boom, new FakeAnnotator());

        Annotation a = annotator.annotate(
                AnnotationRequest.ofSignature("com.example.S#recruit()"));

        // 降级到 FakeAnnotator:summary 含方法名
        assertTrue(a.summary().contains("recruit"), "应降级到 fake,实际:" + a.summary());
    }

    @Test
    void fallsBackWhenReplyUnparseable() {
        ChatFn garbage = prompt -> "这不是 JSON";
        QwenAnnotator annotator = new QwenAnnotator(garbage, new FakeAnnotator());

        Annotation a = annotator.annotate(AnnotationRequest.ofSignature("X#y()"));
        assertTrue(a.summary().contains("y"));
    }
}
