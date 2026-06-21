package com.threadmap.intellij.model

import java.security.MessageDigest

/**
 * 方法源码的稳定短哈希,用于标注过期检测:
 * 标注时存下当时源码的 hash,之后源码变了 → 重算 hash 不一致 → 标注「可能过期 / 需复查」。
 */
object SourceHash {
    /** SHA-256 取前 8 字节 → 16 位十六进制(稳定、跨会话一致、碰撞概率可忽略)。 */
    fun of(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
