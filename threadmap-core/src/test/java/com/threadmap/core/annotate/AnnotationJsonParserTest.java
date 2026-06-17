package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationJsonParserTest {

    private static final String CLEAN = """
            {"summary":"创建任务并置为 CREATED","inputs":"RecruitCommand","outputs":"void",
             "side_effects":["DB写","外部API"],
             "evidence":{"file":"com/example/A.java","lines":"32-48","calls":["repo.save"]},
             "dig_worthy":true,"dig_reason":"含关键分支"}
            """;

    @Test
    void parsesCleanJson() {
        Annotation a = new AnnotationJsonParser().parse(CLEAN);
        assertEquals("创建任务并置为 CREATED", a.summary());
        assertEquals(2, a.sideEffects().size());
        assertEquals("DB写", a.sideEffects().get(0));
        assertEquals("com/example/A.java", a.evidence().file());
        assertEquals("32-48", a.evidence().lines());
        assertEquals("repo.save", a.evidence().calls().get(0));
        assertTrue(a.digWorthy());
        assertEquals("含关键分支", a.digReason());
    }

    @Test
    void toleratesCodeFenceAndSurroundingText() {
        String reply = "好的,分析如下:\n```json\n" + CLEAN + "\n```\n以上。";
        Annotation a = new AnnotationJsonParser().parse(reply);
        assertEquals("创建任务并置为 CREATED", a.summary());
        assertTrue(a.digWorthy());
    }

    @Test
    void defaultsMissingFields() {
        Annotation a = new AnnotationJsonParser().parse("{\"summary\":\"只有摘要\"}");
        assertEquals("只有摘要", a.summary());
        assertEquals("", a.inputs());
        assertTrue(a.sideEffects().isEmpty());
        assertNotNull(a.evidence());
        assertEquals("", a.evidence().file());
        assertFalse(a.digWorthy());
        assertNull(a.digReason());
    }

    @Test
    void throwsOnGarbage() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnnotationJsonParser().parse("not json at all"));
    }

    @Test
    void toleratesLeadingAndTrailingProseWithoutFence() {
        String reply = "Sure! Here is the result:\n{\"summary\":\"only summary\"}\nLet me know if you need more.";
        Annotation a = new AnnotationJsonParser().parse(reply);
        assertEquals("only summary", a.summary());
    }
}
