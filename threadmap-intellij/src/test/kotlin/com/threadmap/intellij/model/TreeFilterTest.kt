package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.Evidence
import com.threadmap.core.annotate.Understanding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

class TreeFilterTest {

    @Test
    fun keepsMatchingNodeAndItsAncestors() {
        val tree = sampleTree()

        val root = CallTreeNodeBuilder.build(tree, TreeFilter(query = "repository"))!!

        assertSame(tree.root, root.userObject)
        assertEquals(1, root.childCount)
        val service = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("Service#work()", (service.userObject as AnnotatedNode).signature)
        assertEquals(1, service.childCount)
    }

    @Test
    fun filtersByUnderstandingAndTodo() {
        val tree = sampleTree()

        val risky = CallTreeNodeBuilder.build(
            tree,
            TreeFilter(UnderstandingFilter.RISKY)
        )!!
        assertEquals(1, risky.childCount)

        val todo = CallTreeNodeBuilder.build(tree, TreeFilter(todoOnly = true))!!
        val service = todo.getChildAt(0) as DefaultMutableTreeNode
        assertEquals(1, service.childCount)
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        assertNull(CallTreeNodeBuilder.build(sampleTree(), TreeFilter(query = "missing")))
    }

    private fun sampleTree(): AnnotatedTree {
        val root = AnnotatedNode(0, "Controller#entry()", "Controller.java", 0, 1)
        val service = AnnotatedNode(1, "Service#work()", "Service.java", 0, 1)
        val repository = AnnotatedNode(2, "OrderRepository#save()", "OrderRepository.java", 0, 1)
        repository.understanding = Understanding.RISKY
        repository.setAnnotation(Annotation(
            "保存订单",
            "Order",
            "void",
            listOf("DB写"),
            Evidence("OrderRepository.java", "10-20", listOf("save")),
            true,
            "写入核心数据"
        ))
        service.addChild(repository)
        root.addChild(service)
        return AnnotatedTree(root.signature, "t", root)
    }
}
