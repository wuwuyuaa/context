package com.threadmap.core.annotate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;

class TraceJsonParserTest {

    private static final String TRACE_JSON = """
        {
          "entry_signature" : "A#a()",
          "captured_at" : "2026-06-17T00:00:00Z",
          "root" : {
            "id" : 0,
            "signature" : "A#a()",
            "file" : "com/example/A.java",
            "line" : 42,
            "self_ms" : 70,
            "children" : [ {
              "id" : 1,
              "signature" : "B#b()",
              "file" : "com/example/B.java",
              "line" : 0,
              "self_ms" : 30,
              "children" : [ ]
            } ]
          }
        }
        """;

    @Test
    void parsesStructureAndSelfMs() throws Exception {
        AnnotatedTree tree = new TraceJsonParser().parse(TRACE_JSON);

        assertEquals("A#a()", tree.getEntrySignature());
        assertEquals("2026-06-17T00:00:00Z", tree.getCapturedAt());

        AnnotatedNode root = tree.getRoot();
        assertEquals(0, root.getId());
        assertEquals("A#a()", root.getSignature());
        assertEquals("com/example/A.java", root.getFile());
        assertEquals(42, root.getLine());
        assertEquals(70, root.getSelfMs());
        assertEquals(1, root.getChildren().size());

        AnnotatedNode child = root.getChildren().get(0);
        assertEquals("B#b()", child.getSignature());
        assertEquals(30, child.getSelfMs());
        assertTrue(child.getChildren().isEmpty());

        // 解析出的节点默认未折叠、未标注
        assertFalse(root.isCollapsed());
        assertNull(root.getAnnotation());
    }

    @Test
    void rejectsMissingRootWithClearError() {
        String bad = "{\"entry_signature\":\"A#a()\",\"captured_at\":\"t\"}";
        IOException e = assertThrows(IOException.class, () -> new TraceJsonParser().parse(bad));
        assertTrue(e.getMessage().contains("root"));
    }

    @Test
    void rejectsMissingEntrySignatureWithClearError() {
        String bad = """
            {"captured_at":"t","root":{"id":0,"signature":"A#a()","file":"A.java","line":0,"self_ms":1,"children":[]}}
            """;
        IOException e = assertThrows(IOException.class, () -> new TraceJsonParser().parse(bad));
        assertTrue(e.getMessage().contains("entry_signature"));
    }

    @Test
    void rejectsMissingCapturedAtWithClearError() {
        String bad = """
            {"entry_signature":"A#a()","root":{"id":0,"signature":"A#a()","file":"A.java","line":0,"self_ms":1,"children":[]}}
            """;
        IOException e = assertThrows(IOException.class, () -> new TraceJsonParser().parse(bad));
        assertTrue(e.getMessage().contains("captured_at"));
    }
}
