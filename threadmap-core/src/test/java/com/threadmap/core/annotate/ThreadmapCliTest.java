package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThreadmapCliTest {

    private static final String TRACE_JSON = """
            {"entry_signature":"com.example.A#a()","captured_at":"2026-06-17T00:00:00Z",
             "root":{"id":0,"signature":"com.example.A#a()","file":"com/example/A.java",
                     "line":0,"self_ms":5,"children":[]}}
            """;

    @Test
    void readsTraceRunsPipelineAndWritesAnnotatedTree(@TempDir Path dir) throws Exception {
        Path trace = dir.resolve("trace.json");
        Files.writeString(trace, TRACE_JSON);
        Path out = dir.resolve(".threadmap/annotated-tree.json");

        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of("com.example"), 50),
                new FakeAnnotator());

        ThreadmapCli.run(trace, out, pipeline);

        assertTrue(Files.exists(out));
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(out));
        assertEquals("com.example.A#a()", parsed.get("entry_signature").asText());
        assertTrue(parsed.get("root").has("summary"), "未折叠根节点应已标注");
    }
}
