package com.threadmap.core.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TraceJsonWriterTest {

    @Test
    void serializesTreeWithSnakeCaseFields() throws Exception {
        TraceNode root = new TraceNode(0, "A#a()", "com/example/A.java", 0);
        root.setElapsedMs(100);
        TraceNode child = new TraceNode(1, "B#b()", "com/example/B.java", 12);
        child.setElapsedMs(30);
        root.addChild(child);
        Trace trace = new Trace("A#a()", "2026-06-16T00:00:00Z", root);

        String json = new TraceJsonWriter().toJson(trace);
        JsonNode parsed = new ObjectMapper().readTree(json);

        assertEquals("A#a()", parsed.get("entry_signature").asText());
        assertEquals("2026-06-16T00:00:00Z", parsed.get("captured_at").asText());

        JsonNode r = parsed.get("root");
        assertEquals(0, r.get("id").asInt());
        assertEquals("A#a()", r.get("signature").asText());
        assertEquals("com/example/A.java", r.get("file").asText());
        assertEquals(0, r.get("line").asInt());
        assertEquals(70, r.get("self_ms").asInt());

        JsonNode c = r.get("children").get(0);
        assertEquals(1, c.get("id").asInt());
        assertEquals("B#b()", c.get("signature").asText());
        assertEquals(12, c.get("line").asInt());
        assertEquals(30, c.get("self_ms").asInt());
        assertTrue(c.get("children").isArray());
        assertEquals(0, c.get("children").size());
    }
}
