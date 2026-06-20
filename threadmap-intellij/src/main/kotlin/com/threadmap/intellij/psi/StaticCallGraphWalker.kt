package com.threadmap.intellij.psi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.PsiTreeUtil
import com.threadmap.core.trace.TraceNode

private val STEREOTYPES = listOf(
    "org.springframework.stereotype.Service", "org.springframework.stereotype.Component",
    "org.springframework.stereotype.Controller", "org.springframework.web.bind.annotation.RestController",
)
private const val REPOSITORY = "org.springframework.stereotype.Repository"

/** 从入口 PsiMethod 静态走出与 trace 同构的调用树（应用 NodePolicy，穿透 helper/getter/库，深度+环路保护）。 */
class StaticCallGraphWalker(private val basePackage: String, private val maxDepth: Int = 12) {

    fun walk(entry: PsiMethod): TraceNode {
        val id = intArrayOf(0)
        val node = newNode(entry, id)
        collect(entry, node, 0, id, HashSet())
        return node
    }

    /** 把 method 体内的组件调用收成 parent 的子节点；FOLLOW_THROUGH 的目标就地内联走进去。 */
    private fun collect(method: PsiMethod, parent: TraceNode, depth: Int, id: IntArray, onPath: MutableSet<String>) {
        if (depth >= maxDepth) return
        val body = method.body ?: return
        for (call in PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)) {
            val callee = call.resolveMethod() ?: continue
            when (NodePolicy.decide(target(callee, method))) {
                NodeDecision.INCLUDE -> {
                    val impl = resolveImpl(callee) ?: callee
                    val sig = signatureOf(impl)
                    if (!onPath.add(sig)) { parent.addChild(newNode(impl, id)); continue }
                    val child = newNode(impl, id)
                    collect(impl, child, depth + 1, id, onPath)
                    onPath.remove(sig)
                    parent.addChild(child)
                }
                NodeDecision.LEAF -> parent.addChild(newNode(callee, id))
                NodeDecision.FOLLOW_THROUGH -> {
                    val sig = signatureOf(callee)
                    if (onPath.add(sig)) { collect(callee, parent, depth, id, onPath); onPath.remove(sig) }
                }
                NodeDecision.SKIP -> {}
            }
        }
    }

    private fun newNode(m: PsiMethod, id: IntArray): TraceNode =
        TraceNode(id[0]++, signatureOf(m), fileOf(m), lineOf(m))

    /** com.pkg.Cls#method(Param1, Param2) */
    private fun signatureOf(m: PsiMethod): String {
        val cls = m.containingClass?.qualifiedName ?: "?"
        val params = m.parameterList.parameters.joinToString(", ") { it.type.presentableText }
        return "$cls#${m.name}($params)"
    }

    private fun fileOf(m: PsiMethod): String =
        m.containingFile?.virtualFile?.let { vf ->
            m.project.basePath?.let { base -> vf.path.removePrefix("$base/") } ?: vf.name
        } ?: ""

    private fun lineOf(m: PsiMethod): Int {
        val file = m.containingFile ?: return 0
        val doc = PsiDocumentManager.getInstance(m.project).getDocument(file) ?: return 0
        return doc.getLineNumber(m.textOffset) + 1
    }

    /** 由 PsiMethod 组装 NodePolicy 的输入。 */
    private fun target(callee: PsiMethod, caller: PsiMethod): CallTarget {
        val cls = callee.containingClass
        val fqn = cls?.qualifiedName
        val inBase = fqn?.startsWith(basePackage) == true
        val iface = cls?.isInterface == true
        val ownPrivate = cls == caller.containingClass &&
            (callee.hasModifierProperty(PsiModifier.PRIVATE) || !callee.hasModifierProperty(PsiModifier.PUBLIC))
        val comp = !ownPrivate && cls != null && STEREOTYPES.any { AnnotationUtil.isAnnotated(cls, it, 0) }
        val repoPort = cls != null && (
            AnnotationUtil.isAnnotated(cls, REPOSITORY, 0) ||
            (iface && cls.name?.let { it.endsWith("Repository") || it.endsWith("Port") || it.endsWith("Client") || it.endsWith("Gateway") } == true)
        )
        val getter = PropertyUtilBase.isSimplePropertyGetter(callee) ||
            PropertyUtilBase.isSimplePropertySetter(callee) || callee.name == "builder" || callee.name == "build"
        return CallTarget(fqn, inBase, iface, comp, repoPort, getter, ownPrivate)
    }

    /** S2 占位：接口不解析（返回 null，调用方退回用接口本身）。S3 覆盖。 */
    private fun resolveImpl(callee: PsiMethod): PsiMethod? = null
}
