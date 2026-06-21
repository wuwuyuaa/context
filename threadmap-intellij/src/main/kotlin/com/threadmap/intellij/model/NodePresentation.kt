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

    /** 结构标签:Spring「非显式控制流」注解(事务/异步/重试/缓存/定时/鉴权),走链时由 PSI 读出,无需 LLM。 */
    fun markers(node: AnnotatedNode): List<String> = node.markers

    fun statusStyle(node: AnnotatedNode): StatusStyle = when (
        node.understanding ?: Understanding.UNKNOWN
    ) {
        Understanding.UNKNOWN -> StatusStyle.UNKNOWN
        Understanding.HALF -> StatusStyle.HALF
        Understanding.MASTERED -> StatusStyle.MASTERED
        Understanding.RISKY -> StatusStyle.RISKY
    }

    fun isDigWorthy(node: AnnotatedNode): Boolean = node.annotation?.digWorthy() ?: false

    /** 仅凭被调类名后缀 + 方法名推断的"结构性数据/副作用边界"(无需 LLM):DB 读/写、外部、消息。 */
    fun structuralSideEffect(node: AnnotatedNode): String? {
        val sig = node.signature
        val hash = sig.indexOf('#')
        val cls = (if (hash < 0) sig else sig.substring(0, hash)).substringAfterLast('.')
        val method = if (hash < 0) "" else sig.substring(hash + 1).substringBefore('(')
        return when {
            cls.endsWith("Repository") || cls.endsWith("Dao") -> if (isDbWrite(method)) "DB写" else "DB读"
            cls.endsWith("Port") || cls.endsWith("Client") || cls.endsWith("Gateway") || cls.endsWith("Feign") -> "外部API"
            cls.endsWith("Producer") || cls.endsWith("Publisher") || cls.endsWith("Sender") -> "消息"
            else -> null
        }
    }

    private fun isDbWrite(method: String): Boolean {
        val n = method.lowercase()
        return listOf(
            "save", "insert", "update", "delete", "remove", "persist", "merge",
            "create", "store", "add", "put", "write", "batch", "upsert", "flush"
        ).any { n.startsWith(it) }
    }

    /** 主干里程碑 = 结构上的数据/副作用边界(落库 / 读库 / 调外部 / 发消息);读也是边界,仍算主干。 */
    fun isMilestone(node: AnnotatedNode): Boolean = structuralSideEffect(node) != null
}
