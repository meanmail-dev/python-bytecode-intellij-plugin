package dev.meanmail.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory


class PythonBytecodeTool : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val pluginDevToolWindow = PythonBytecodeToolWindow(project)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(
            pluginDevToolWindow.content, null, false
        )
        toolWindow.contentManager.addContent(content)
    }
}
