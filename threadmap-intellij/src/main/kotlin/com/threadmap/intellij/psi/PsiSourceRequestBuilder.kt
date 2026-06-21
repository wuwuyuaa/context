package com.threadmap.intellij.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotationRequest
import com.threadmap.core.annotate.AnnotationRequestBuilder

/**
 * 用 PSI 给节点填方法源码 + 被调名,让 LLM 基于真实代码标注(2b)。
 * 在 ReadAction 内解析(标注 Task 跑在后台线程);抽不到就退回仅签名(PromptBuilder 自会降级)。
 */
class PsiSourceRequestBuilder(private val project: Project) : AnnotationRequestBuilder {
    override fun build(node: AnnotatedNode): AnnotationRequest =
        ReadAction.compute<AnnotationRequest, RuntimeException> {
            val e = PsiMethodSource.extract(project, node.signature)
            if (e != null) AnnotationRequest(node.signature, e.source, e.calleeNames)
            else AnnotationRequest.ofSignature(node.signature)
        }
}
