package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.threadmap.intellij.model.CallGraph

/** 把 CallGraph 序列化成 JS 端 renderGraph 需要的 JSON。 */
object CallGraphJson {

    fun toJson(graph: CallGraph, mapper: ObjectMapper): String {
        val root = mapper.createObjectNode()
        root.put("rootId", graph.rootId)
        val nodes = root.putArray("nodes")
        graph.nodes.forEach { n ->
            val o = nodes.addObject()
            o.put("id", n.id)
            o.put("label", n.label)
            o.put("summary", n.summary)
            o.put("status", n.status.name)
            o.put("digWorthy", n.digWorthy)
            val se = o.putArray("sideEffects")
            n.sideEffects.forEach { se.add(it) }
        }
        val edges = root.putArray("edges")
        graph.edges.forEach { e ->
            val o = edges.addObject()
            o.put("from", e.from)
            o.put("to", e.to)
            o.put("count", e.count)
        }
        return mapper.writeValueAsString(root)
    }
}
