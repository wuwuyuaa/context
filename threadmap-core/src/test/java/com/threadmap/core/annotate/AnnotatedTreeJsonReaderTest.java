package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotatedTreeJsonReaderTest {

    private AnnotatedTree sampleTree() {
        AnnotatedNode root = new AnnotatedNode(0, "com.example.A#a()", "com/example/A.java", 0, 5);
        root.setUnderstanding(Understanding.MASTERED);
        root.setAnnotation(new Annotation("做 A", "无", "B 结果",
                List.of("DB写"), new Evidence("com/example/A.java", "10-20", List.of("b")),
                true, "核心逻辑"));
        AnnotatedNode lib = new AnnotatedNode(1, "org.lib.X#y()", "org/lib/X.java", 0, 1);
        lib.setCollapsed(true); // 折叠节点:无标注
        root.addChild(lib);
        return new AnnotatedTree("com.example.A#a()", "2026-06-18T00:00:00Z", root);
    }

    @Test
    void roundTripsWriterOutput() throws IOException {
        String json = new AnnotatedTreeJsonWriter().toJson(sampleTree());

        AnnotatedTree tree = new AnnotatedTreeJsonReader().read(json);

        assertEquals("com.example.A#a()", tree.getEntrySignature());
        assertEquals("2026-06-18T00:00:00Z", tree.getCapturedAt());

        AnnotatedNode root = tree.getRoot();
        assertEquals(0, root.getId());
        assertEquals("com.example.A#a()", root.getSignature());
        assertEquals(5, root.getSelfMs());
        assertFalse(root.isCollapsed());
        assertEquals(Understanding.MASTERED, root.getUnderstanding());
        assertNotNull(root.getAnnotation());
        assertEquals("做 A", root.getAnnotation().summary());
        assertTrue(root.getAnnotation().sideEffects().contains("DB写"));
        assertEquals("10-20", root.getAnnotation().evidence().lines());
        assertTrue(root.getAnnotation().digWorthy());
        assertEquals("核心逻辑", root.getAnnotation().digReason());

        assertEquals(1, root.getChildren().size());
        AnnotatedNode lib = root.getChildren().get(0);
        assertTrue(lib.isCollapsed());
        assertNull(lib.getAnnotation(), "折叠节点无标注");
        assertEquals(Understanding.UNKNOWN, lib.getUnderstanding());
    }

    @Test
    void rejectsMissingRoot() {
        String bad = "{\"entry_signature\":\"A#a()\",\"captured_at\":\"t\"}";
        assertThrows(IOException.class, () -> new AnnotatedTreeJsonReader().read(bad));
    }
}
