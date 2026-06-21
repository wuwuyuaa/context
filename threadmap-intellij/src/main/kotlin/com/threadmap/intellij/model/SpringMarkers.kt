package com.threadmap.intellij.model

/**
 * 识别 Spring「非显式控制流」注解,映射成节点上的短标签(纯逻辑,可测)。
 * PSI 读取(方法 / 所在类带了哪些注解)在 walker 里做,这里只负责 FQN → 标签。
 */
object SpringMarkers {

    /** 注解全限定名 → 短标签。方法本身或其所在类带上即生效(类级 @Transactional 也算)。 */
    val KNOWN: Map<String, String> = linkedMapOf(
        "org.springframework.transaction.annotation.Transactional" to "事务",
        "jakarta.transaction.Transactional" to "事务",
        "javax.transaction.Transactional" to "事务",
        "org.springframework.scheduling.annotation.Async" to "异步",
        "org.springframework.retry.annotation.Retryable" to "重试",
        "org.springframework.cache.annotation.Cacheable" to "缓存",
        "org.springframework.cache.annotation.CachePut" to "缓存",
        "org.springframework.cache.annotation.CacheEvict" to "缓存",
        "org.springframework.scheduling.annotation.Scheduled" to "定时",
        "org.springframework.security.access.prepost.PreAuthorize" to "鉴权",
        "org.springframework.security.access.prepost.PostAuthorize" to "鉴权",
        "org.springframework.security.access.annotation.Secured" to "鉴权",
        "jakarta.annotation.security.RolesAllowed" to "鉴权",
        "javax.annotation.security.RolesAllowed" to "鉴权",
    )

    /** 所有可能的标签(供 UI 配色判断「这是不是 Spring marker」)。 */
    val LABELS: Set<String> = KNOWN.values.toSet()

    /** 给定一组注解 FQN,返回去重、稳定顺序的标签(未知注解忽略)。 */
    fun labelsFor(fqns: Collection<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (fqn in fqns) KNOWN[fqn]?.let { out.add(it) }
        return out.toList()
    }
}
