package com.threadmap.intellij.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.intellij.model.SignatureParser

/** 按 trace 签名经 PSI 跳到方法;找不到则退回记录的 file:line。 */
object SourceNavigator {

    fun navigate(project: Project, node: AnnotatedNode) {
        if (navigateByPsi(project, node)) return
        navigateByFileLine(project, node)
    }

    private fun navigateByPsi(project: Project, node: AnnotatedNode): Boolean {
        val sig = SignatureParser.parse(node.signature) ?: return false
        val method = ReadAction.compute<com.intellij.psi.PsiMethod?, RuntimeException> {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(sig.fqcn.replace('$', '.'), GlobalSearchScope.allScope(project)) ?: return@compute null
            psiClass.findMethodsByName(sig.methodName, false)
                .firstOrNull { it.parameterList.parametersCount == sig.paramCount }
                ?: psiClass.findMethodsByName(sig.methodName, false).firstOrNull()
        } ?: return false
        if (!method.canNavigate()) return false
        method.navigate(true)
        return true
    }

    private fun navigateByFileLine(project: Project, node: AnnotatedNode) {
        val base = project.basePath ?: return
        val candidates = listOf("$base/${node.file}",
            "$base/app/src/main/java/${node.file}",
            "$base/src/main/java/${node.file}")
        val vFile = candidates.asSequence()
            .mapNotNull { LocalFileSystem.getInstance().findFileByPath(it) }
            .firstOrNull() ?: return
        val line = if (node.line > 0) node.line - 1 else 0
        OpenFileDescriptor(project, vFile, line, 0).navigate(true)
    }
}
