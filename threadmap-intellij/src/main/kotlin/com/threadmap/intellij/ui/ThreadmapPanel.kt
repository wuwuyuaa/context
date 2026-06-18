package com.threadmap.intellij.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.threadmap.core.annotate.AnnotatedTreeJsonReader
import com.threadmap.intellij.model.CallTreeNodeBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JLabel
import javax.swing.JScrollPane

class ThreadmapPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    init {
        setContent(JScrollPane(JLabel("点击「加载 Trace」选择 annotated-tree.json,或把文件放到 项目根/.threadmap/annotated-tree.json")))

        val group = DefaultActionGroup()
        group.add(object : AnAction("加载 Trace", "选择 annotated-tree.json 加载", null) {
            override fun actionPerformed(e: AnActionEvent) {
                chooseAndLoad()
            }
        })
        group.add(object : AnAction("重新加载", "从 项目根/.threadmap/annotated-tree.json 重新加载", null) {
            override fun actionPerformed(e: AnActionEvent) {
                loadDefault()
            }
        })
        val actionToolbar = ActionManager.getInstance().createActionToolbar("ThreadmapToolbar", group, true)
        actionToolbar.targetComponent = this
        setToolbar(actionToolbar.component)

        loadDefault()
    }

    private fun chooseAndLoad() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        val file: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        render(Path.of(file.path))
    }

    private fun loadDefault() {
        val base = project.basePath ?: return
        val path = Path.of(base, ".threadmap", "annotated-tree.json")
        if (Files.isRegularFile(path)) render(path)
    }

    private fun render(path: Path) {
        val json = Files.readString(path)
        val tree = AnnotatedTreeJsonReader().read(json)
        val rootNode = CallTreeNodeBuilder.build(tree)
        val table = buildTreeTable(rootNode)
        setContent(JScrollPane(table))
    }
}
