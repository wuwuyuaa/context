package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Understanding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MasteryProgressTest {

    private fun node(id: Int, sig: String, u: Understanding, collapsed: Boolean = false): AnnotatedNode {
        val n = AnnotatedNode(id, sig, "f", 0, 0)
        n.setUnderstanding(u)
        if (collapsed) n.setCollapsed(true)
        return n
    }

    private fun tree(root: AnnotatedNode) = AnnotatedTree(root.signature, "t", root)

    @Test
    fun countsMasteredOverNonCollapsed() {
        val root = node(0, "A#a()", Understanding.MASTERED)
        root.addChild(node(1, "B#b()", Understanding.UNKNOWN))
        root.addChild(node(2, "L#l()", Understanding.UNKNOWN, collapsed = true)) // 折叠不计
        val c = MasteryProgress.counts(tree(root))
        assertEquals(2, c.total)    // A、B(L 折叠)
        assertEquals(1, c.mastered) // A
    }

    @Test
    fun nextPendingSkipsMasteredAndCycles() {
        val root = node(0, "A#a()", Understanding.MASTERED)
        root.addChild(node(1, "B#b()", Understanding.UNKNOWN))
        root.addChild(node(2, "C#c()", Understanding.RISKY))
        val t = tree(root)
        assertEquals("B#b()", MasteryProgress.nextPending(t, null))    // 从头:跳过已掌握的 A
        assertEquals("C#c()", MasteryProgress.nextPending(t, "B#b()")) // B 之后
        assertEquals("B#b()", MasteryProgress.nextPending(t, "C#c()")) // 循环回 B
    }

    @Test
    fun nextPendingNullWhenAllMastered() {
        val root = node(0, "A#a()", Understanding.MASTERED)
        root.addChild(node(1, "B#b()", Understanding.MASTERED))
        assertNull(MasteryProgress.nextPending(tree(root), null))
    }
}
