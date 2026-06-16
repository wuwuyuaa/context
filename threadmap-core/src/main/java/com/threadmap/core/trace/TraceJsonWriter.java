package com.threadmap.core.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 把 Trace 序列化成约定 schema 的 trace.json(显式 snake_case 字段)。 */
public class TraceJsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(Trace trace) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("entry_signature", trace.getEntrySignature());
        root.put("captured_at", trace.getCapturedAt());
        root.set("root", node(trace.getRoot()));
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unexpected trace serialization failure", e);
        }
    }

    private ObjectNode node(TraceNode n) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("id", n.getId());
        o.put("signature", n.getSignature());
        o.put("file", n.getFile());
        o.put("line", n.getLine());
        o.put("self_ms", n.selfMs());
        ArrayNode children = o.putArray("children");
        for (TraceNode c : n.getChildren()) {
            children.add(node(c));
        }
        return o;
    }
}
