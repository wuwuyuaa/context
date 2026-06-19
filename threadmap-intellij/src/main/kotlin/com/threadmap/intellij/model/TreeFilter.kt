package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.Understanding
import java.util.Locale

enum class UnderstandingFilter(val label: String, private val state: Understanding?) {
    ALL("全部状态", null),
    UNKNOWN("未知", Understanding.UNKNOWN),
    HALF("半懂", Understanding.HALF),
    MASTERED("已掌握", Understanding.MASTERED),
    RISKY("高风险", Understanding.RISKY);

    fun matches(node: AnnotatedNode): Boolean = state == null || node.understanding == state
    override fun toString(): String = label
}

data class TreeFilter(
    val understanding: UnderstandingFilter = UnderstandingFilter.ALL,
    val todoOnly: Boolean = false,
    val query: String = ""
) {
    fun isEmpty(): Boolean =
        understanding == UnderstandingFilter.ALL && !todoOnly && query.isBlank()

    fun matches(node: AnnotatedNode): Boolean {
        if (!understanding.matches(node)) return false
        if (todoOnly && node.annotation?.digWorthy() != true) return false
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return true
        return node.signature.lowercase(Locale.ROOT).contains(normalized) ||
            node.annotation?.summary()?.lowercase(Locale.ROOT)?.contains(normalized) == true
    }
}
