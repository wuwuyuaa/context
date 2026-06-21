package com.threadmap.intellij.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.threadmap.core.annotate.Annotation
import com.threadmap.core.annotate.AnnotationJsonParser
import com.threadmap.core.annotate.AnnotationRequest
import com.threadmap.core.annotate.Annotator
import java.nio.file.Files
import java.nio.file.Path

/**
 * 磁盘级标注缓存(Annotator 装饰器):按方法源码 hash 缓存标注,命中则跳过 LLM。
 * 跨链路 / 跨次 / 跨 IDE 重启复用——同一未改动方法永不重复花 token。源码变了 → hash 变 → 自然失效重标。
 * 标注一轮结束后调 [flush] 落盘。无源码的请求(仅签名)不缓存(key 不可靠)。
 */
class DiskAnnotationCache(
    private val delegate: Annotator,
    private val cacheFile: Path,
) : Annotator {

    private val mapper = ObjectMapper()
    private val parser = AnnotationJsonParser()
    private val cache: MutableMap<String, Annotation> = load()
    private var dirty = false

    override fun annotate(request: AnnotationRequest): Annotation {
        val source = request.source()
        if (source.isNullOrBlank()) return delegate.annotate(request) // 无源码,不做 key
        val key = SourceHash.of(source)
        cache[key]?.let { return it } // 命中:跳过 LLM
        val result = delegate.annotate(request)
        cache[key] = result
        dirty = true
        return result
    }

    /** 把新增缓存落盘(仅在有变更时写)。 */
    fun flush() {
        if (!dirty) return
        val root = mapper.createObjectNode()
        cache.forEach { (hash, a) -> root.set<ObjectNode>(hash, toNode(a)) }
        cacheFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(cacheFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))
        dirty = false
    }

    private fun load(): MutableMap<String, Annotation> {
        val map = HashMap<String, Annotation>()
        if (!Files.isRegularFile(cacheFile)) return map
        try {
            mapper.readTree(Files.readString(cacheFile)).fields().forEach { (hash, node) ->
                map[hash] = parser.parse(node.toString()) // 存的就是 LLM-JSON 格式,直接复用解析器
            }
        } catch (e: Exception) {
            // 缓存损坏不致命:当空缓存,后续重标重建
        }
        return map
    }

    /** Annotation → LLM-JSON 节点(与 AnnotationJsonParser 读取格式对称)。 */
    private fun toNode(a: Annotation): ObjectNode {
        val o = mapper.createObjectNode()
        o.put("summary", a.summary())
        o.put("inputs", a.inputs())
        o.put("outputs", a.outputs())
        val se = o.putArray("side_effects"); a.sideEffects().forEach { se.add(it) }
        val ev = o.putObject("evidence")
        ev.put("file", a.evidence().file())
        ev.put("lines", a.evidence().lines())
        val calls = ev.putArray("calls"); a.evidence().calls().forEach { calls.add(it) }
        o.put("dig_worthy", a.digWorthy())
        if (a.digReason() != null) o.put("dig_reason", a.digReason()) else o.putNull("dig_reason")
        return o
    }
}
