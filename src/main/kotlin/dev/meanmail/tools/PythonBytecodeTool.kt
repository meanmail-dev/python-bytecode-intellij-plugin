package dev.meanmail.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.util.concurrent.ConcurrentHashMap


class PythonBytecodeTool : ToolWindowFactory {
    companion object {
        // Map to store tool window components by project
        private val toolWindowComponents = ConcurrentHashMap<Project, PythonBytecodeToolWindowComponent>()

        // Get the tool window component for a project
        fun getToolWindowComponent(project: Project): PythonBytecodeToolWindowComponent? {
            return toolWindowComponents[project]
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val pluginDevToolWindow = PythonBytecodeToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(
            pluginDevToolWindow.component, null, false
        )
        toolWindow.contentManager.addContent(content)

        // Store the tool window component for later use
        toolWindowComponents[project] = pluginDevToolWindow

        // Register the tool window with the disposable to ensure proper cleanup
        Disposer.register(toolWindow.disposable) {
            toolWindowComponents.remove(project)
            pluginDevToolWindow.dispose()
        }
    }
}
