package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 解析 M1 产出的 trace.json → 未折叠未标注的 AnnotatedTree(只含结构 + self_ms)。 */
public class TraceJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnnotatedTree parse(String traceJson) throws IOException {
        JsonNode root = MAPPER.readTree(traceJson);
        AnnotatedNode rootNode = node(root.get("root"));
        return new AnnotatedTree(
                root.get("entry_signature").asText(),
                root.get("captured_at").asText(),
                rootNode);
    }

    private AnnotatedNode node(JsonNode n) {
        AnnotatedNode an = new AnnotatedNode(
                n.get("id").asInt(),
                n.get("signature").asText(),
                n.get("file").asText(),
                n.get("line").asInt(),
                n.get("self_ms").asLong());
        JsonNode children = n.get("children");
        if (children != null) {
            for (JsonNode c : children) {
                an.addChild(node(c));
            }
        }
        return an;
    }
}
