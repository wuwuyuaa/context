package com.threadmap.core.annotate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;

/** 把 AnnotatedTree 序列化成 annotated-tree.json(显式 snake_case 字段)。 */
public class AnnotatedTreeJsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(AnnotatedTree tree) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("entry_signature", tree.getEntrySignature());
        root.put("captured_at", tree.getCapturedAt());
        root.set("root", node(tree.getRoot()));
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unexpected annotated-tree serialization failure", e);
        }
    }

    private ObjectNode node(AnnotatedNode n) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("id", n.getId());
        o.put("signature", n.getSignature());
        o.put("file", n.getFile());
        o.put("line", n.getLine());
        o.put("self_ms", n.getSelfMs());
        o.put("collapsed", n.isCollapsed());
        o.put("understanding", n.getUnderstanding().name().toLowerCase(Locale.ROOT));
        if (!n.getMarkers().isEmpty()) {
            ArrayNode markers = o.putArray("markers");
            n.getMarkers().forEach(markers::add);
        }
        if (!n.getConfidence().isEmpty()) {
            o.put("confidence", n.getConfidence());
        }
        if (!n.getSourceHash().isEmpty()) {
            o.put("source_hash", n.getSourceHash());
        }

        Annotation a = n.getAnnotation();
        if (a != null) {
            o.put("summary", a.summary());
            o.put("inputs", a.inputs());
            o.put("outputs", a.outputs());
            ArrayNode sideEffects = o.putArray("side_effects");
            for (String s : a.sideEffects()) {
                sideEffects.add(s);
            }
            ObjectNode evidence = o.putObject("evidence");
            evidence.put("file", a.evidence().file());
            evidence.put("lines", a.evidence().lines());
            ArrayNode calls = evidence.putArray("calls");
            for (String c : a.evidence().calls()) {
                calls.add(c);
            }
            o.put("dig_worthy", a.digWorthy());
            if (a.digReason() != null) {
                o.put("dig_reason", a.digReason());
            }
        }

        ArrayNode children = o.putArray("children");
        for (AnnotatedNode c : n.getChildren()) {
            children.add(node(c));
        }
        return o;
    }
}
