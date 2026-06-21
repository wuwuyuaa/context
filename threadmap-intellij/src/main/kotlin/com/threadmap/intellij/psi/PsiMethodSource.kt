package com.threadmap.intellij.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.threadmap.intellij.model.SignatureParser

/**
 * 按签名 resolve 出 PsiMethod,抽方法源码(含方法级注解)+ 直接被调方法名,供真实 LLM 标注(2b)。
 * 必须在 ReadAction 内调用。源码过长会截断以控 token。
 */
object PsiMethodSource {

    data class Extracted(val source: String, val calleeNames: List<String>)

    /** 方法体超过此行数即截断(只标主干 + 截断,双重控 token)。 */
    private const val MAX_LINES = 160

    fun extract(project: Project, signature: String): Extracted? {
        val sig = SignatureParser.parse(signature) ?: return null
        val psiClass = JavaPsiFacade.getInstance(project)
            .findClass(sig.fqcn.replace('$', '.'), GlobalSearchScope.allScope(project)) ?: return null
        val candidates = psiClass.findMethodsByName(sig.methodName, false)
        if (candidates.isEmpty()) return null
        // 多重载时按参数个数挑;挑不到退回第一个
        val method = candidates.firstOrNull { it.parameterList.parametersCount == sig.paramCount }
            ?: candidates.first()
        val text = method.text ?: return null
        val callees = PsiTreeUtil.findChildrenOfType(method.body, PsiMethodCallExpression::class.java)
            .mapNotNull { it.methodExpression.referenceName }
            .distinct()
        return Extracted(truncate(text), callees)
    }

    private fun truncate(text: String): String {
        val lines = text.lines()
        if (lines.size <= MAX_LINES) return text
        return lines.take(MAX_LINES).joinToString("\n") +
            "\n// …(方法体过长,已截断 ${lines.size - MAX_LINES} 行)…"
    }
}
