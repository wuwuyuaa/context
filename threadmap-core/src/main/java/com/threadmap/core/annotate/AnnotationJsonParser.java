package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 把 LLM 文本回复解析成 Annotation;容忍 ```json 围栏与前后多余文字,缺字段给默认值。 */
public class AnnotationJsonParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Annotation parse(String reply) {
        String json = extractJsonObject(reply);
        JsonNode n;
        try {
            n = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析标注 JSON: " + reply, e);
        }
        return new Annotation(
                text(n, "summary"),
                text(n, "inputs"),
                text(n, "outputs"),
                stringList(n.get("side_effects")),
                evidence(n.get("evidence")),
                n.path("dig_worthy").asBoolean(false),
                n.hasNonNull("dig_reason") ? n.get("dig_reason").asText() : null);
    }

    /** 剥掉 ``` 围栏,截取首个 '{' 到末个 '}'。 */
    private static String extractJsonObject(String reply) {
        String s = reply.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int closingFence = s.lastIndexOf("```");
            if (closingFence >= 0) {
                s = s.substring(0, closingFence);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : "";
    }

    private static List<String> stringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode e : array) {
                out.add(e.asText());
            }
        }
        return out;
    }

    private static Evidence evidence(JsonNode ev) {
        if (ev == null || ev.isNull()) {
            return new Evidence("", "", List.of());
        }
        return new Evidence(
                ev.hasNonNull("file") ? ev.get("file").asText() : "",
                ev.hasNonNull("lines") ? ev.get("lines").asText() : "",
                stringList(ev.get("calls")));
    }
}
