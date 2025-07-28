package dev.meanmail.tools

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Listener for file editor events to automatically update the Python bytecode tool window
 * when a Python file is selected.
 */
class PythonBytecodeFileListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.extension?.lowercase() != "py") {
            return
        }

        val project = event.manager.project
        updateBytecodeToolWindow(project)
    }

    private fun updateBytecodeToolWindow(project: Project) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Python bytecode") ?: return

        // Only update if the tool window is visible
        if (toolWindow.isVisible) {
            // Get the tool window component and update it
            val component = PythonBytecodeTool.getToolWindowComponent(project) ?: return
            component.updateBytecode()
        }
    }
}