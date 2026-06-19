package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.Evidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodoExporterTest {

    @Test
    fun collectsDigWorthyNodesAndDeduplicatesSignatures() {
        val tree = sampleTree()

        val items = TodoExporter.collect(tree)

        assertEquals(1, items.size)
        assertEquals("B#work()", items[0].signature)
        assertEquals("含关键分支", items[0].reason)
        assertEquals("B.java:10-20", items[0].location)
    }

    @Test
    fun rendersMarkdownChecklist() {
        val markdown = TodoExporter.toMarkdown(sampleTree())

        assertTrue(markdown.contains("# 脉络待查清单"))
        assertTrue(markdown.contains("> 入口：`A#entry()`"))
        assertTrue(markdown.contains("- [ ] `B#work()` — 含关键分支"))
    }

    private fun sampleTree(): AnnotatedTree {
        val root = AnnotatedNode(0, "A#entry()", "A.java", 0, 1)
        val first = AnnotatedNode(1, "B#work()", "B.java", 0, 1)
        first.setAnnotation(annotation(true))
        val repeated = AnnotatedNode(2, "B#work()", "B.java", 0, 1)
        repeated.setAnnotation(annotation(true))
        val ordinary = AnnotatedNode(3, "C#helper()", "C.java", 0, 1)
        ordinary.setAnnotation(annotation(false))
        root.addChild(first)
        root.addChild(repeated)
        root.addChild(ordinary)
        return AnnotatedTree("A#entry()", "t", root)
    }

    private fun annotation(digWorthy: Boolean) = Annotation(
        "摘要",
        "输入",
        "输出",
        emptyList(),
        Evidence("B.java", "10-20", listOf("save")),
        digWorthy,
        if (digWorthy) "含关键分支" else null
    )
}
