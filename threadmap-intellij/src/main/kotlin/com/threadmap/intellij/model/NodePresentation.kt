package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.Understanding

/** 掌握状态的 UI 语义(UI 层映射到具体 JBColor)。 */
enum class StatusStyle { UNKNOWN, HALF, MASTERED, RISKY }

/** 从 AnnotatedNode 提取列展示文本与状态(纯逻辑,可测)。 */
object NodePresentation {

    /** 去包前缀的短签名:com.example.svc.Order#place(Cart, int) → Order#place(Cart, int)。 */
    fun shortSignature(node: AnnotatedNode): String {
        val sig = node.signature
        val hash = sig.indexOf('#')
        if (hash < 0) return sig
        val fqcn = sig.substring(0, hash)
        val simple = fqcn.substringAfterLast('.')
        return simple + sig.substring(hash)
    }

    /** 树中使用的紧凑签名，参数详情留到右侧面板与 tooltip。 */
    fun compactSignature(node: AnnotatedNode): String =
        shortSignature(node).substringBefore('(')

    fun summary(node: AnnotatedNode): String = node.annotation?.summary() ?: "—"

    fun sideEffects(node: AnnotatedNode): String =
        node.annotation?.sideEffects()?.joinToString(", ") ?: ""

    fun statusStyle(node: AnnotatedNode): StatusStyle = when (
        node.understanding ?: Understanding.UNKNOWN
    ) {
        Understanding.UNKNOWN -> StatusStyle.UNKNOWN
        Understanding.HALF -> StatusStyle.HALF
        Understanding.MASTERED -> StatusStyle.MASTERED
        Understanding.RISKY -> StatusStyle.RISKY
    }

    fun isDigWorthy(node: AnnotatedNode): Boolean = node.annotation?.digWorthy() ?: false

    /** 仅凭被调类名后缀推断的"结构性副作用边界"(无需 LLM):仓储 / 外部 / 消息生产者。 */
    fun structuralSideEffect(node: AnnotatedNode): String? {
        val sig = node.signature
        val hash = sig.indexOf('#')
        val cls = (if (hash < 0) sig else sig.substring(0, hash)).substringAfterLast('.')
        return when {
            cls.endsWith("Repository") || cls.endsWith("Dao") -> "DB写"
            cls.endsWith("Port") || cls.endsWith("Client") || cls.endsWith("Gateway") || cls.endsWith("Feign") -> "外部API"
            cls.endsWith("Producer") || cls.endsWith("Publisher") || cls.endsWith("Sender") -> "消息"
            else -> null
        }
    }

    /** 主干里程碑 = 结构上产生副作用的边界节点(落库 / 调外部 / 发消息)。 */
    fun isMilestone(node: AnnotatedNode): Boolean = structuralSideEffect(node) != null
}
