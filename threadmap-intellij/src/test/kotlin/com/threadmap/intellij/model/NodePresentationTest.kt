package com.threadmap.intellij.model

import com.threadmap.core.annotate.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NodePresentationTest {

    private fun annotated(): AnnotatedNode {
        val n = AnnotatedNode(0, "com.example.svc.Order#place(Cart, int)", "x", 0, 5)
        n.understanding = Understanding.MASTERED
        n.setAnnotation(Annotation("下单并扣库存", "Cart", "Order",
                listOf("DB写", "外部API"),
                Evidence("x", "1-9", listOf("save")), true, "核心"))
        return n
    }

    @Test
    fun shortSignatureDropsPackage() {
        assertEquals("Order#place(Cart, int)", NodePresentation.shortSignature(annotated()))
    }

    @Test
    fun summaryFallsBackToDashWhenUnannotated() {
        val bare = AnnotatedNode(0, "A#a()", "x", 0, 1)
        assertEquals("—", NodePresentation.summary(bare))
        assertEquals("下单并扣库存", NodePresentation.summary(annotated()))
    }

    @Test
    fun sideEffectsJoinedAndStatusMapped() {
        assertEquals("DB写, 外部API", NodePresentation.sideEffects(annotated()))
        assertEquals("", NodePresentation.sideEffects(AnnotatedNode(0, "A#a()", "x", 0, 1)))
        assertEquals(StatusStyle.MASTERED, NodePresentation.statusStyle(annotated()))
        assertEquals(StatusStyle.UNKNOWN, NodePresentation.statusStyle(AnnotatedNode(0, "A#a()", "x", 0, 1)))
    }

    @Test
    fun digWorthyFlag() {
        assertTrue(NodePresentation.isDigWorthy(annotated()))
        assertFalse(NodePresentation.isDigWorthy(AnnotatedNode(0, "A#a()", "x", 0, 1)))
    }
}
