package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.Evidence
import com.threadmap.core.annotate.Understanding
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CallGraphBuilderTest {

    private fun node(id: Int, sig: String) = AnnotatedNode(id, sig, "$sig.java", 0, 0)

    @Test
    fun `dedupes repeated callee under same caller into one edge with count`() {
        val root = node(0, "R#r()")
        root.addChild(node(1, "A#a()"))
        root.addChild(node(2, "A#a()"))
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(setOf("R#r()", "A#a()"), g.nodes.map { it.id }.toSet())
        assertEquals(1, g.edges.size)
        assertEquals("R#r()", g.edges[0].from)
        assertEquals("A#a()", g.edges[0].to)
        assertEquals(2, g.edges[0].count)
        assertEquals("R#r()", g.rootId)
    }

    @Test
    fun `same signature under different callers is a single node with two incoming edges`() {
        val root = node(0, "R#r()")
        val a = node(1, "A#a()")
        val b = node(2, "B#b()")
        a.addChild(node(3, "C#c()"))
        b.addChild(node(4, "C#c()"))
        root.addChild(a)
        root.addChild(b)
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(4, g.nodes.size)
        assertEquals(1, g.nodes.count { it.id == "C#c()" })
        assertEquals(2, g.edges.count { it.to == "C#c()" })
    }

    @Test
    fun `self recursion becomes a self loop edge`() {
        val root = node(0, "R#r()")
        root.addChild(node(1, "R#r()"))
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)

        assertEquals(1, g.nodes.size)
        assertEquals(1, g.edges.size)
        assertEquals("R#r()", g.edges[0].from)
        assertEquals("R#r()", g.edges[0].to)
    }

    @Test
    fun `maps understanding and side effects onto the node`() {
        val root = node(0, "R#r()")
        root.understanding = Understanding.RISKY
        root.annotation = Annotation("做了X", "in", "out", listOf("DB写", "消息"),
            Evidence("R.java", "1-9", listOf("save")), true, "值得查")
        val tree = AnnotatedTree("R#r()", "t", root)

        val g = CallGraphBuilder.build(tree)
        val n = g.nodes.single()

        assertEquals(StatusStyle.RISKY, n.status)
        assertEquals(listOf("DB写", "消息"), n.sideEffects)
        assertTrue(n.digWorthy)
        assertEquals("做了X", n.summary)
    }
}
