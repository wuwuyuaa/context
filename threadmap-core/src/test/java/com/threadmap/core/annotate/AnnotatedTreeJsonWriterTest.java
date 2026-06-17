package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedTreeJsonWriterTest {

    @Test
    void serializesNodesWithSnakeCaseAndOptionalAnnotation() throws Exception {
        AnnotatedNode root = new AnnotatedNode(0, "A#a()", "com/example/A.java", 5, 70);
        root.setUnderstanding(Understanding.MASTERED);
        root.setAnnotation(new Annotation(
                "创建任务并置为 CREATED",
                "RecruitCommand",
                "void",
                List.of("DB写", "外部API"),
                new Evidence("com/example/A.java", "32-48", List.of("repo.save")),
                true,
                "含关键分支"));

        AnnotatedNode child = new AnnotatedNode(1, "B#b()", "com/example/B.java", 0, 30);
        child.setCollapsed(true);   // 折叠 → 无标注
        root.addChild(child);

        AnnotatedTree tree = new AnnotatedTree("A#a()", "2026-06-17T00:00:00Z", root);
        String json = new AnnotatedTreeJsonWriter().toJson(tree);
        JsonNode parsed = new ObjectMapper().readTree(json);

        assertEquals("A#a()", parsed.get("entry_signature").asText());
        assertEquals("2026-06-17T00:00:00Z", parsed.get("captured_at").asText());
        JsonNode r = parsed.get("root");
        assertEquals(5, r.get("line").asInt());
        assertEquals(70, r.get("self_ms").asInt());
        assertFalse(r.get("collapsed").asBoolean());
        assertEquals("mastered", r.get("understanding").asText());
        assertEquals("创建任务并置为 CREATED", r.get("summary").asText());
        assertTrue(r.get("dig_worthy").asBoolean());
        assertEquals("含关键分支", r.get("dig_reason").asText());
        assertEquals("DB写", r.get("side_effects").get(0).asText());
        assertEquals("32-48", r.get("evidence").get("lines").asText());
        assertEquals("repo.save", r.get("evidence").get("calls").get(0).asText());

        JsonNode c = r.get("children").get(0);
        assertTrue(c.get("collapsed").asBoolean());
        assertEquals("unknown", c.get("understanding").asText());
        assertFalse(c.has("summary"), "折叠/未标注节点不应有 summary");
        assertFalse(c.has("dig_worthy"));
    }

    @Test
    void omitsDigReasonWhenNull() throws Exception {
        AnnotatedNode root = new AnnotatedNode(0, "A#a()", "A.java", 0, 1);
        root.setAnnotation(new Annotation(
                "s", "i", "o", List.of(),
                new Evidence("A.java", "", List.of()),
                false, null)); // digReason null

        String json = new AnnotatedTreeJsonWriter().toJson(new AnnotatedTree("A#a()", "t", root));
        JsonNode r = new ObjectMapper().readTree(json).get("root");

        assertTrue(r.has("summary"));
        assertTrue(r.has("dig_worthy"));
        assertFalse(r.has("dig_reason"), "digReason null 时应省略 dig_reason");
    }
}
