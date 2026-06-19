package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree

/** 调用图的节点(按签名唯一)。 */
data class GraphNode(
    val id: String,
    val label: String,
    val summary: String,
    val status: StatusStyle,
    val sideEffects: List<String>,
    val digWorthy: Boolean,
    val file: String,
    val line: Int,
)

/** 调用图的有向边;count = 该 caller→callee 调用出现次数。 */
data class GraphEdge(val from: String, val to: String, val count: Int)

data class CallGraph(val nodes: List<GraphNode>, val edges: List<GraphEdge>, val rootId: String)

/** AnnotatedTree → 去重节点 + 计数边(纯逻辑,可测)。 */
object CallGraphBuilder {

    fun build(tree: AnnotatedTree): CallGraph {
        val nodes = LinkedHashMap<String, GraphNode>()
        val edgeCounts = LinkedHashMap<Pair<String, String>, Int>()

        fun visit(n: AnnotatedNode) {
            nodes.getOrPut(n.signature) { toGraphNode(n) }
            for (child in n.children) {
                val key = n.signature to child.signature
                edgeCounts[key] = (edgeCounts[key] ?: 0) + 1
                visit(child)
            }
        }
        visit(tree.root)

        val edges = edgeCounts.map { (k, c) -> GraphEdge(k.first, k.second, c) }
        return CallGraph(nodes.values.toList(), edges, tree.root.signature)
    }

    private fun toGraphNode(n: AnnotatedNode): GraphNode = GraphNode(
        id = n.signature,
        label = NodePresentation.compactSignature(n),
        summary = NodePresentation.summary(n),
        status = NodePresentation.statusStyle(n),
        sideEffects = n.annotation?.sideEffects() ?: emptyList(),
        digWorthy = NodePresentation.isDigWorthy(n),
        file = n.file,
        line = n.line,
    )
}
