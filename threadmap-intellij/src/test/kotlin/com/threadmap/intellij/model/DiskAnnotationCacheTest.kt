package com.threadmap.intellij.model

import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.AnnotationRequest
import com.threadmap.core.annotate.Annotator
import com.threadmap.core.annotate.Evidence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DiskAnnotationCacheTest {

    private fun ann(summary: String) =
        Annotation(summary, "in", "out", listOf("DB写"), Evidence("f", "1-9", listOf("save")), false, null)

    private class CountingAnnotator(private val result: Annotation) : Annotator {
        var calls = 0
        override fun annotate(r: AnnotationRequest): Annotation { calls++; return result }
    }

    private fun req(source: String?) =
        if (source == null) AnnotationRequest.ofSignature("S#m()")
        else AnnotationRequest("S#m()", source, listOf("save"))

    @Test
    fun hitsBySourceHashWithinRun() {
        val file = Files.createTempFile("cache", ".json")
        val delegate = CountingAnnotator(ann("做X"))
        val cache = DiskAnnotationCache(delegate, file)
        val r = req("void m(){ save(); }")
        assertEquals("做X", cache.annotate(r).summary())
        assertEquals("做X", cache.annotate(r).summary()) // 同源码第二次:命中
        assertEquals(1, delegate.calls) // delegate 只被调一次
    }

    @Test
    fun persistsAndReloadsAcrossInstances() {
        val file = Files.createTempFile("cache", ".json")
        val r = req("void m(){ save(); }")
        val d1 = CountingAnnotator(ann("做X"))
        DiskAnnotationCache(d1, file).apply { annotate(r); flush() }
        assertEquals(1, d1.calls)

        // 新实例(模拟另一条链 / 下次会话)读盘命中,不再调 delegate
        val d2 = CountingAnnotator(ann("不该被调到"))
        val reloaded = DiskAnnotationCache(d2, file)
        assertEquals("做X", reloaded.annotate(r).summary())
        assertEquals(0, d2.calls)
    }

    @Test
    fun differentSourceMisses() {
        val file = Files.createTempFile("cache", ".json")
        val delegate = CountingAnnotator(ann("X"))
        val cache = DiskAnnotationCache(delegate, file)
        cache.annotate(req("void m(){ a(); }"))
        cache.annotate(req("void m(){ b(); }")) // 源码不同 → 未命中
        assertEquals(2, delegate.calls)
    }

    @Test
    fun skipsCacheWhenNoSource() {
        val file = Files.createTempFile("cache", ".json")
        val delegate = CountingAnnotator(ann("sig"))
        val cache = DiskAnnotationCache(delegate, file)
        cache.annotate(req(null)); cache.annotate(req(null)) // 仅签名:不缓存
        assertEquals(2, delegate.calls)
    }
}
