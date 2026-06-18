package com.threadmap.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel

class ThreadmapToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val placeholder = JLabel("脉络:加载 annotated-tree.json 后显示调用树")
        val content = ContentFactory.getInstance().createContent(placeholder, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
