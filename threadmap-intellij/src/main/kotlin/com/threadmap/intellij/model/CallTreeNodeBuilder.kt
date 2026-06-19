package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import javax.swing.tree.DefaultMutableTreeNode

/** 把 AnnotatedTree 镜像成 Swing 树:每个 DefaultMutableTreeNode 的 userObject 是对应 AnnotatedNode。 */
object CallTreeNodeBuilder {
    fun build(tree: AnnotatedTree): DefaultMutableTreeNode = node(tree.root)

    /**
     * 过滤后保留匹配节点及其祖先，避免结果失去调用链上下文。
     * 没有结果时返回 null，由 UI 显示空状态。
     */
    fun build(tree: AnnotatedTree, filter: TreeFilter): DefaultMutableTreeNode? {
        if (filter.isEmpty()) return build(tree)
        return filteredNode(tree.root, filter)
    }

    private fun node(an: AnnotatedNode): DefaultMutableTreeNode {
        val dmtn = DefaultMutableTreeNode(an)
        for (child in an.children) {
            dmtn.add(node(child))
        }
        return dmtn
    }

    private fun filteredNode(an: AnnotatedNode, filter: TreeFilter): DefaultMutableTreeNode? {
        val children = an.children.mapNotNull { filteredNode(it, filter) }
        if (!filter.matches(an) && children.isEmpty()) return null
        return DefaultMutableTreeNode(an).apply {
            children.forEach(::add)
        }
    }
}
