package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

class CallTreeNodeBuilderTest {

    @Test
    fun buildsMirroringTreeWithAnnotatedNodeUserObjects() {
        val root = AnnotatedNode(0, "A#a()", "A.java", 0, 5)
        val child = AnnotatedNode(1, "B#b()", "B.java", 0, 1)
        root.addChild(child)
        val tree = AnnotatedTree("A#a()", "t", root)

        val node: DefaultMutableTreeNode = CallTreeNodeBuilder.build(tree)

        assertSame(root, node.userObject)
        assertEquals(1, node.childCount)
        val childNode = node.getChildAt(0) as DefaultMutableTreeNode
        assertSame(child, childNode.userObject)
    }
}
