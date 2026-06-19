package com.threadmap.intellij.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.threadmap.intellij.model.CallGraph
import com.threadmap.intellij.model.GraphEdge
import com.threadmap.intellij.model.GraphNode
import com.threadmap.intellij.model.StatusStyle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallGraphJsonTest {

    @Test
    fun `serializes nodes edges and rootId`() {
        val graph = CallGraph(
            nodes = listOf(
                GraphNode("R#r()", "R#r", "入口", StatusStyle.RISKY, listOf("DB写"), true, "R.java", 3),
                GraphNode("A#a()", "A#a", "—", StatusStyle.UNKNOWN, emptyList(), false, "A.java", 0),
            ),
            edges = listOf(GraphEdge("R#r()", "A#a()", 2)),
            rootId = "R#r()",
        )

        val json = CallGraphJson.toJson(graph, ObjectMapper())
        val parsed = ObjectMapper().readTree(json)

        assertEquals("R#r()", parsed.get("rootId").asText())
        assertEquals(2, parsed.get("nodes").size())
        val first = parsed.get("nodes").get(0)
        assertEquals("R#r()", first.get("id").asText())
        assertEquals("RISKY", first.get("status").asText())
        assertEquals("DB写", first.get("sideEffects").get(0).asText())
        assertTrue(first.get("digWorthy").asBoolean())
        val edge = parsed.get("edges").get(0)
        assertEquals("R#r()", edge.get("from").asText())
        assertEquals("A#a()", edge.get("to").asText())
        assertEquals(2, edge.get("count").asInt())
    }
}
