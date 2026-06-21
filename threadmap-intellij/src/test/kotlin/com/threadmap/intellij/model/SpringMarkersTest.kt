package com.threadmap.intellij.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringMarkersTest {

    @Test
    fun mapsKnownAnnotation() {
        assertEquals(
            listOf("事务"),
            SpringMarkers.labelsFor(listOf("org.springframework.transaction.annotation.Transactional")),
        )
    }

    @Test
    fun dedupsAndKeepsStableOrder() {
        val labels = SpringMarkers.labelsFor(
            listOf(
                "org.springframework.scheduling.annotation.Async",
                "org.springframework.cache.annotation.Cacheable",
                "org.springframework.cache.annotation.CacheEvict", // 同为「缓存」,应去重
            ),
        )
        assertEquals(listOf("异步", "缓存"), labels)
    }

    @Test
    fun ignoresUnknownAnnotations() {
        assertTrue(
            SpringMarkers.labelsFor(listOf("com.example.Foo", "java.lang.Override")).isEmpty(),
        )
    }
}
