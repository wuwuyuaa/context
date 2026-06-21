package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 解析 M1 产出的 trace.json → 未折叠未标注的 AnnotatedTree(只含结构 + self_ms)。 */
public class TraceJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnnotatedTree parse(String traceJson) throws IOException {
        JsonNode root = MAPPER.readTree(traceJson);
        JsonNode rootField = requireField(root, "root", traceJson);
        return new AnnotatedTree(
                requireField(root, "entry_signature", traceJson).asText(),
                requireField(root, "captured_at", traceJson).asText(),
                node(rootField));
    }

    // 参数 json 暂未插值,刻意保留以备将来在错误消息里附带 JSON 上下文(见 M2b-2 计划)。
    private static JsonNode requireField(JsonNode obj, String name, String json) throws IOException {
        JsonNode f = obj.get(name);
        if (f == null || f.isNull()) {
            throw new IOException("trace.json missing required field: " + name);
        }
        return f;
    }

    private AnnotatedNode node(JsonNode n) {
        AnnotatedNode an = new AnnotatedNode(
                n.get("id").asInt(),
                n.get("signature").asText(),
                n.get("file").asText(),
                n.get("line").asInt(),
                n.get("self_ms").asLong());
        JsonNode markers = n.get("markers");
        if (markers != null && markers.isArray()) {
            List<String> ms = new ArrayList<>();
            markers.forEach(m -> ms.add(m.asText()));
            an.setMarkers(ms);
        }
        JsonNode confidence = n.get("confidence");
        if (confidence != null && !confidence.isNull()) {
            an.setConfidence(confidence.asText());
        }
        JsonNode children = n.get("children");
        if (children != null) {
            for (JsonNode c : children) {
                an.addChild(node(c));
            }
        }
        return an;
    }
}
