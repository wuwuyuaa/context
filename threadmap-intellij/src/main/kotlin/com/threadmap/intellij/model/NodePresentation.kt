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

    fun summary(node: AnnotatedNode): String = node.annotation?.summary() ?: "—"

    fun sideEffects(node: AnnotatedNode): String =
        node.annotation?.sideEffects()?.joinToString(", ") ?: ""

    fun statusStyle(node: AnnotatedNode): StatusStyle = when (node.understanding) {
        Understanding.UNKNOWN -> StatusStyle.UNKNOWN
        Understanding.HALF -> StatusStyle.HALF
        Understanding.MASTERED -> StatusStyle.MASTERED
        Understanding.RISKY -> StatusStyle.RISKY
    }

    fun isDigWorthy(node: AnnotatedNode): Boolean = node.annotation?.digWorthy() ?: false
}
