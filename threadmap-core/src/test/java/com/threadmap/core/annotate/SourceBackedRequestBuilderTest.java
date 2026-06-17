package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceBackedRequestBuilderTest {

    @Test
    void fillsSourceAndCalleesWhenFound(@TempDir Path root) throws Exception {
        Path dir = root.resolve("com/example");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("S.java"), """
                package com.example;
                class S { int run() { return helper(); } int helper() { return 1; } }
                """);
        SourceBackedRequestBuilder builder =
                new SourceBackedRequestBuilder(new MethodSourceExtractor(List.of(root)));

        AnnotatedNode node = new AnnotatedNode(0, "com.example.S#run()", "com/example/S.java", 0, 1);
        AnnotationRequest req = builder.build(node);

        assertEquals("com.example.S#run()", req.signature());
        assertNotNull(req.source());
        assertTrue(req.source().contains("helper"));
        assertTrue(req.calleeSignatures().contains("helper"));
    }

    @Test
    void fallsBackToSignatureWhenNotFound(@TempDir Path root) {
        SourceBackedRequestBuilder builder =
                new SourceBackedRequestBuilder(new MethodSourceExtractor(List.of(root)));
        AnnotatedNode node = new AnnotatedNode(0, "com.example.Missing#x()", "x", 0, 1);

        AnnotationRequest req = builder.build(node);

        assertEquals("com.example.Missing#x()", req.signature());
        assertNull(req.source());
        assertTrue(req.calleeSignatures().isEmpty());
    }
}
