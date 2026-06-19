package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import java.nio.file.Files
import java.nio.file.Path

data class TodoItem(
    val signature: String,
    val reason: String,
    val location: String
)

/** 从标注树收集待查节点，并生成可提交或继续加工的 Markdown 清单。 */
object TodoExporter {

    fun collect(tree: AnnotatedTree): List<TodoItem> {
        val bySignature = linkedMapOf<String, TodoItem>()
        visit(tree.root) { node ->
            val annotation = node.annotation ?: return@visit
            if (!annotation.digWorthy()) return@visit
            bySignature.putIfAbsent(
                node.signature,
                TodoItem(
                    signature = node.signature,
                    reason = annotation.digReason()?.takeIf { it.isNotBlank() } ?: "值得进一步核查",
                    location = location(node)
                )
            )
        }
        return bySignature.values.toList()
    }

    fun toMarkdown(tree: AnnotatedTree): String {
        val items = collect(tree)
        return buildString {
            appendLine("# 脉络待查清单")
            appendLine()
            appendLine("> 入口：`${tree.entrySignature}`")
            appendLine()
            if (items.isEmpty()) {
                appendLine("当前链路没有待查节点。")
            } else {
                for (item in items) {
                    append("- [ ] `").append(item.signature).append("`")
                    append(" — ").append(item.reason)
                    if (item.location.isNotBlank()) {
                        append("（`").append(item.location).append("`）")
                    }
                    appendLine()
                }
            }
        }
    }

    fun write(tree: AnnotatedTree, path: Path) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, toMarkdown(tree))
    }

    private fun location(node: AnnotatedNode): String {
        val evidence = node.annotation?.evidence()
        if (evidence != null && evidence.file().isNotBlank()) {
            return if (evidence.lines().isBlank()) evidence.file()
            else "${evidence.file()}:${evidence.lines()}"
        }
        return if (node.line > 0) "${node.file}:${node.line}" else node.file
    }

    private fun visit(node: AnnotatedNode, action: (AnnotatedNode) -> Unit) {
        action(node)
        node.children.forEach { visit(it, action) }
    }
}
