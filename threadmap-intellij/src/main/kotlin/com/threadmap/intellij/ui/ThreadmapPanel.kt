package com.threadmap.intellij.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.threadmap.core.annotate.AnnotatedTreeJsonReader
import com.threadmap.intellij.model.CallTreeNodeBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JToolBar

class ThreadmapPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    init {
        setContent(JScrollPane(JLabel("点击「加载 Trace」选择 .threadmap/annotated-tree.json")))
        val load = JButton("加载 Trace")
        load.addActionListener { chooseAndLoad() }
        val top = JToolBar()
        top.isFloatable = false
        top.add(load)
        setToolbar(top)
        project.basePath?.let { autoLoad(Path.of(it, ".threadmap", "annotated-tree.json")) }
    }

    private fun chooseAndLoad() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        val file: VirtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        render(Path.of(file.path))
    }

    private fun autoLoad(path: Path) {
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
