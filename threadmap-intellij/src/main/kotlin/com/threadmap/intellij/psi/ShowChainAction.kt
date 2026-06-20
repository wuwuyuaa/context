package com.threadmap.intellij.psi

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.threadmap.core.annotate.AnnotatedNode
import com.threadmap.core.annotate.AnnotatedTree
import com.threadmap.core.trace.Trace
import com.threadmap.core.trace.TraceJsonWriter
import com.threadmap.core.trace.TraceNode
import com.threadmap.intellij.ui.ThreadmapPanel
import java.time.Instant

/** 编辑器右键「看这条链」:从光标所在方法静态走出调用链,渲染进脉络工具窗。 */
class ShowChainAction : AnAction("看这条链") {

    // 读 PSI 必须在后台线程:2024.2 禁止在 EDT 上请求 psi.File(否则 update 抛异常、菜单项被隐藏)。
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = methodAt(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val method = methodAt(e) ?: return
        val base = guessBasePackage(method)
        val traceRoot = ReadAction.compute<TraceNode, RuntimeException> {
            StaticCallGraphWalker(base).walk(method)
        }
        val tree = AnnotatedTree(traceRoot.signature, Instant.now().toString(), toAnnotated(traceRoot))
        writeStaticTrace(project, traceRoot)
        val tw = ToolWindowManager.getInstance(project).getToolWindow("脉络 (Threadmap)") ?: return
        tw.activate {
            val panel = tw.contentManager.contents.firstOrNull()?.component as? ThreadmapPanel
            panel?.renderStaticTree(tree)
        }
    }

    private fun methodAt(e: AnActionEvent): PsiMethod? {
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val caret = e.getData(CommonDataKeys.CARET) ?: return null
        val el = file.findElementAt(caret.offset) ?: return null
        return PsiTreeUtil.getParentOfType(el, PsiMethod::class.java)
    }

    private fun toAnnotated(n: TraceNode): AnnotatedNode {
        val a = AnnotatedNode(n.id, n.signature, n.file, n.line, 0)
        n.children.forEach { a.addChild(toAnnotated(it)) }
        return a
    }

    /** 落 .threadmap/static-trace.json(与运行时 trace 同构),供现有 runCli 标注管线消费。 */
    private fun writeStaticTrace(project: Project, traceRoot: TraceNode) {
        val base = project.basePath ?: return
        try {
            val out = java.nio.file.Path.of(base, ".threadmap", "static-trace.json")
            java.nio.file.Files.createDirectories(out.parent)
            java.nio.file.Files.writeString(
                out,
                TraceJsonWriter().toJson(Trace(traceRoot.signature, Instant.now().toString(), traceRoot))
            )
        } catch (e: Exception) {
            Logger.getInstance(ShowChainAction::class.java).warn("写入 static-trace.json 失败", e)
        }
    }

    private fun guessBasePackage(m: PsiMethod): String {
        val fqn = m.containingClass?.qualifiedName ?: return ""
        val parts = fqn.split('.')
        return if (parts.size >= 3) parts.take(3).joinToString(".") else fqn.substringBeforeLast('.')
    }
}
