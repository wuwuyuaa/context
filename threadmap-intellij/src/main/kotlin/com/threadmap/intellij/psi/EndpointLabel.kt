package com.threadmap.intellij.psi

/** 把 mapping 注解短名 + 路径属性推成 "VERB" 和 "/path"(纯逻辑,便于不依赖 PSI 单测)。 */
object EndpointLabel {
    fun verb(annotationShortName: String): String = when (annotationShortName) {
        "GetMapping" -> "GET"
        "PostMapping" -> "POST"
        "PutMapping" -> "PUT"
        "DeleteMapping" -> "DELETE"
        "PatchMapping" -> "PATCH"
        else -> "ANY" // RequestMapping(未指定 method) 等
    }

    /** 类级基路径 + 方法级路径 → 归一化的 "/a/b/c"。 */
    fun path(classPath: String?, methodPath: String?): String {
        val c = (classPath ?: "").trim().trim('/')
        val m = (methodPath ?: "").trim().trim('/')
        val joined = listOf(c, m).filter { it.isNotEmpty() }.joinToString("/")
        return "/" + joined
    }
}
