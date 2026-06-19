package com.threadmap.intellij.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Understanding
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * 项目级接管进度存储。
 *
 * 格式按 signature 关联，并为每个节点保留对象结构，方便后续把风险、人工备注等
 * 独立字段加入而不破坏现有文件。
 */
class ProgressStore(private val file: Path) {
    private val mapper = ObjectMapper()

    /** 把 progress.json 中的掌握状态覆盖到树上；同一 signature 的所有节点同步更新。 */
    @Throws(IOException::class)
    fun merge(tree: AnnotatedTree) {
        if (!Files.isRegularFile(file)) return
        val states = readStates()
        visit(tree.root) { node ->
            states[node.signature]?.let { node.understanding = it }
        }
    }

    /** 更新当前树和 progress.json；UNKNOWN 会移除显式状态，回到默认值。 */
    @Throws(IOException::class)
    fun update(tree: AnnotatedTree, signature: String, state: Understanding) {
        val root = readDocument()
        val nodes = objectField(root, "nodes")
        val entry = (nodes.get(signature) as? ObjectNode) ?: mapper.createObjectNode()
        if (state == Understanding.UNKNOWN) {
            entry.remove("understanding")
        } else {
            entry.put("understanding", state.name.lowercase(Locale.ROOT))
        }
        if (entry.isEmpty) {
            nodes.remove(signature)
        } else {
            nodes.set<ObjectNode>(signature, entry)
        }
        writeAtomically(root)

        visit(tree.root) { node ->
            if (node.signature == signature) node.understanding = state
        }
    }

    @Throws(IOException::class)
    internal fun readStates(): Map<String, Understanding> {
        val nodes = readDocument().get("nodes") as ObjectNode
        val result = linkedMapOf<String, Understanding>()
        nodes.fields().forEachRemaining { (signature, value) ->
            parseUnderstanding(value.get("understanding"))?.let { result[signature] = it }
        }
        return result
    }

    private fun readDocument(): ObjectNode {
        if (!Files.isRegularFile(file)) return emptyDocument()
        val parsed = mapper.readTree(Files.readString(file))
        if (parsed !is ObjectNode) {
            throw IOException("progress.json 顶层必须是 JSON 对象")
        }
        val nodes = parsed.get("nodes")
        if (nodes == null) {
            parsed.set<ObjectNode>("nodes", mapper.createObjectNode())
        } else if (!nodes.isObject) {
            throw IOException("progress.json 的 nodes 必须是 JSON 对象")
        }
        if (!parsed.has("version")) parsed.put("version", 1)
        return parsed
    }

    private fun emptyDocument(): ObjectNode = mapper.createObjectNode().apply {
        put("version", 1)
        set<ObjectNode>("nodes", mapper.createObjectNode())
    }

    private fun objectField(root: ObjectNode, name: String): ObjectNode =
        (root.get(name) as? ObjectNode) ?: mapper.createObjectNode().also {
            root.set<ObjectNode>(name, it)
        }

    private fun parseUnderstanding(node: JsonNode?): Understanding? {
        val value = node?.takeUnless { it.isNull }?.asText()?.uppercase(Locale.ROOT) ?: return null
        return runCatching { Understanding.valueOf(value) }.getOrNull()
    }

    private fun writeAtomically(root: ObjectNode) {
        val parent = file.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temp = Files.createTempFile(parent, "progress-", ".json.tmp")
        try {
            Files.writeString(temp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun visit(node: AnnotatedNode, action: (AnnotatedNode) -> Unit) {
        action(node)
        node.children.forEach { visit(it, action) }
    }
}
