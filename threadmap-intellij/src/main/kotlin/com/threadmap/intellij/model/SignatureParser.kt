package com.threadmap.intellij.model

/** 解析后的签名:全限定类名(内部类保留 $ 二进制名)、方法名、参数个数。 */
data class ParsedSignature(val fqcn: String, val methodName: String, val paramCount: Int)

/** 把 trace 签名 FQCN#method(简单参数类型, ...) 解析成结构(纯逻辑,供 PSI 查找)。 */
object SignatureParser {
    fun parse(signature: String): ParsedSignature? {
        val hash = signature.indexOf('#')
        if (hash < 0) return null
        val open = signature.indexOf('(', hash + 1)
        val close = signature.lastIndexOf(')')
        if (open < hash || close < open) return null
        val fqcn = signature.substring(0, hash)
        val method = signature.substring(hash + 1, open)
        val params = signature.substring(open + 1, close).trim()
        val paramCount = if (params.isEmpty()) 0 else params.split(",").size
        if (fqcn.isEmpty() || method.isEmpty()) return null
        return ParsedSignature(fqcn, method, paramCount)
    }
}
