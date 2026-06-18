package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import javax.swing.tree.DefaultMutableTreeNode

/** 把 AnnotatedTree 镜像成 Swing 树:每个 DefaultMutableTreeNode 的 userObject 是对应 AnnotatedNode。 */
object CallTreeNodeBuilder {
    fun build(tree: AnnotatedTree): DefaultMutableTreeNode = node(tree.root)

    private fun node(an: AnnotatedNode): DefaultMutableTreeNode {
        val dmtn = DefaultMutableTreeNode(an)
        for (child in an.children) {
            dmtn.add(node(child))
        }
        return dmtn
    }
}
