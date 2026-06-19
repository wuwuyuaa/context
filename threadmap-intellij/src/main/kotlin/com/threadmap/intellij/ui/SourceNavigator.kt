package com.threadmap.intellij.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.intellij.model.SignatureParser

/** 按 trace 签名经 PSI 跳到方法;找不到则退回记录的 file:line。 */
object SourceNavigator {

    fun navigate(project: Project, node: AnnotatedNode) {
        val sig = SignatureParser.parse(node.signature)
        if (sig == null || DumbService.isDumb(project)) {
            navigateByFileLine(project, node)
            return
        }
        ReadAction.nonBlocking<SmartPsiElementPointer<PsiMethod>?> {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(sig.fqcn.replace('$', '.'), GlobalSearchScope.allScope(project))
                ?: return@nonBlocking null
            val method = psiClass.findMethodsByName(sig.methodName, false)
                .firstOrNull { it.parameterList.parametersCount == sig.paramCount }
                ?: psiClass.findMethodsByName(sig.methodName, false).firstOrNull()
                ?: return@nonBlocking null
            SmartPointerManager.createPointer(method)
        }
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { pointer ->
                val method = pointer?.element
                if (method != null && method.canNavigate()) {
                    method.navigate(true)
                } else {
                    navigateByFileLine(project, node)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
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
