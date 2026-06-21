package com.threadmap.intellij.psi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.trace.Trace
import com.threadmap.core.trace.TraceJsonWriter
import com.threadmap.core.trace.TraceNode
import java.time.Instant

/** 从一个入口 PsiMethod 静态走出链路,并转成可渲染 / 可落盘的结构。右键 action 与入口清单共用。 */
object StaticChain {
    /** 在 ReadAction 内调用:静态走链。 */
    fun walk(method: PsiMethod): TraceNode =
        StaticCallGraphWalker(guessBasePackage(method)).walk(method)

    fun toAnnotatedTree(root: TraceNode): AnnotatedTree =
        AnnotatedTree(root.signature, Instant.now().toString(), toAnnotated(root))

    /** 落 .threadmap/static-trace.json,供现有 runCli 标注管线消费;失败不阻塞渲染。 */
    fun writeStaticTrace(project: Project, root: TraceNode) {
        val base = project.basePath ?: return
        try {
            val out = java.nio.file.Path.of(base, ".threadmap", "static-trace.json")
            java.nio.file.Files.createDirectories(out.parent)
            java.nio.file.Files.writeString(
                out,
                TraceJsonWriter().toJson(Trace(root.signature, Instant.now().toString(), root))
            )
        } catch (e: Exception) {
            Logger.getInstance(StaticChain::class.java).warn("写入 static-trace.json 失败", e)
        }
    }

    private fun toAnnotated(n: TraceNode): AnnotatedNode {
        val a = AnnotatedNode(n.id, n.signature, n.file, n.line, 0)
        a.markers = n.markers
        a.confidence = n.confidence
        n.children.forEach { a.addChild(toAnnotated(it)) }
        return a
    }

    fun guessBasePackage(m: PsiMethod): String {
        val fqn = m.containingClass?.qualifiedName ?: return ""
        val parts = fqn.split('.')
        return if (parts.size >= 3) parts.take(3).joinToString(".") else fqn.substringBeforeLast('.')
    }
}
