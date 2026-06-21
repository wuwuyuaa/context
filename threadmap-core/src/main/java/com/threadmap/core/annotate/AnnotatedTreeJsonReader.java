package com.threadmap.core.annotate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 把 annotated-tree.json 反序列化回 AnnotatedTree(对称于 AnnotatedTreeJsonWriter)。 */
public class AnnotatedTreeJsonReader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnnotatedTree read(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        JsonNode rootNode = requireField(root, "root");
        return new AnnotatedTree(
                requireField(root, "entry_signature").asText(),
                requireField(root, "captured_at").asText(),
                node(rootNode));
    }

    private static JsonNode requireField(JsonNode obj, String name) throws IOException {
        JsonNode f = obj.get(name);
        if (f == null || f.isNull()) {
            throw new IOException("annotated-tree.json missing required field: " + name);
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

        JsonNode collapsed = n.get("collapsed");
        if (collapsed != null) {
            an.setCollapsed(collapsed.asBoolean());
        }
        JsonNode understanding = n.get("understanding");
        if (understanding != null && !understanding.isNull()) {
            an.setUnderstanding(Understanding.valueOf(understanding.asText().toUpperCase(Locale.ROOT)));
        }
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
        JsonNode sourceHash = n.get("source_hash");
        if (sourceHash != null && !sourceHash.isNull()) {
            an.setSourceHash(sourceHash.asText());
        }
        if (n.has("summary")) {
            an.setAnnotation(annotation(n));
        }

        JsonNode children = n.get("children");
        if (children != null) {
            for (JsonNode c : children) {
                an.addChild(node(c));
            }
        }
        return an;
    }

    private Annotation annotation(JsonNode n) {
        JsonNode ev = n.get("evidence");
        Evidence evidence = new Evidence(
                textOrEmpty(ev, "file"),
                textOrEmpty(ev, "lines"),
                stringList(ev == null ? null : ev.get("calls")));
        return new Annotation(
                textOrEmpty(n, "summary"),
                textOrEmpty(n, "inputs"),
                textOrEmpty(n, "outputs"),
                stringList(n.get("side_effects")),
                evidence,
                n.path("dig_worthy").asBoolean(false),
                n.has("dig_reason") ? n.get("dig_reason").asText() : null);
    }

    private static String textOrEmpty(JsonNode obj, String name) {
        if (obj == null) return "";
        JsonNode f = obj.get(name);
        return f == null || f.isNull() ? "" : f.asText();
    }

    private static List<String> stringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode e : arr) {
                out.add(e.asText());
            }
        }
        return out;
    }
}
