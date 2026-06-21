package com.threadmap.intellij.model

import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.annotate.Understanding

/**
 * 接管阅读进度(纯逻辑,可测):已掌握 N/M + 找「下一个待读」节点。
 * 把被动的掌握状态列表变成主动引导——用户一路点「下一个待读」走完整条链。
 */
object MasteryProgress {

    data class Counts(val mastered: Int, val total: Int)

    /** 该理解的节点 = 未折叠节点(折叠的库/helper 不算);已掌握 = understanding==MASTERED。 */
    fun counts(tree: AnnotatedTree): Counts {
        var mastered = 0
        var total = 0
        fun walk(n: AnnotatedNode) {
            if (!n.isCollapsed) {
                total++
                if (n.understanding == Understanding.MASTERED) mastered++
            }
            n.children.forEach { walk(it) }
        }
        walk(tree.root)
        return Counts(mastered, total)
    }

    /**
     * 下一个待读节点的签名:DFS 顺序里、`afterSignature` 之后、首个「未折叠且非已掌握」的节点,循环。
     * afterSignature 为 null(或找不到)从头找;全部已掌握返回 null。
     */
    fun nextPending(tree: AnnotatedTree, afterSignature: String?): String? {
        val sigs = ArrayList<String>()
        val pending = ArrayList<Boolean>()
        fun walk(n: AnnotatedNode) {
            if (!n.isCollapsed) {
                sigs.add(n.signature)
                pending.add(n.understanding != Understanding.MASTERED)
            }
            n.children.forEach { walk(it) }
        }
        walk(tree.root)
        if (sigs.isEmpty()) return null
        val start = afterSignature?.let { sigs.indexOf(it) } ?: -1
        val n = sigs.size
        for (k in 1..n) {
            val i = ((start + k) % n + n) % n
            if (pending[i]) return sigs[i]
        }
        return null
    }
}
