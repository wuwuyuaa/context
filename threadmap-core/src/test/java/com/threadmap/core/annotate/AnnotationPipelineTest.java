package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnnotationPipelineTest {

    private static final String TRACE_JSON = """
        {
          "entry_signature" : "com.example.Controller#handle()",
          "captured_at" : "2026-06-17T00:00:00Z",
          "root" : {
            "id" : 0, "signature" : "com.example.Controller#handle()",
            "file" : "com/example/Controller.java", "line" : 0, "self_ms" : 10,
            "children" : [ {
              "id" : 1, "signature" : "com.example.Service#work()",
              "file" : "com/example/Service.java", "line" : 0, "self_ms" : 5,
              "children" : [ {
                "id" : 2, "signature" : "org.springframework.Tx#run()",
                "file" : "org/springframework/Tx.java", "line" : 0, "self_ms" : 1,
                "children" : [ ]
              } ]
            } ]
          }
        }
        """;

    @Test
    void foldsOutOfPackageAndAnnotatesOnlyVisibleNodes() throws Exception {
        AnnotationPipeline pipeline = new AnnotationPipeline(
                new PackageFolder(List.of("com.example"), 10),
                new FakeAnnotator());

        AnnotatedTree tree = pipeline.run(TRACE_JSON);

        AnnotatedNode root = tree.getRoot();
        AnnotatedNode service = root.getChildren().get(0);
        AnnotatedNode lib = service.getChildren().get(0);

        // 折叠:库节点折叠,业务节点不折叠
        assertFalse(root.isCollapsed());
        assertFalse(service.isCollapsed());
        assertTrue(lib.isCollapsed());

        // 懒标:未折叠节点有标注,折叠节点无标注
        assertNotNull(root.getAnnotation());
        assertNotNull(service.getAnnotation());
        assertNull(lib.getAnnotation(), "折叠节点不标注");
        assertTrue(service.getAnnotation().summary().contains("work"));

        // 可序列化为 annotated-tree.json
        String json = new AnnotatedTreeJsonWriter().toJson(tree);
        JsonNode parsed = new ObjectMapper().readTree(json);
        assertEquals("com.example.Controller#handle()", parsed.get("entry_signature").asText());
        JsonNode libNode = parsed.get("root").get("children").get(0).get("children").get(0);
        assertTrue(libNode.get("collapsed").asBoolean());
        assertFalse(libNode.has("summary"));
    }
}
