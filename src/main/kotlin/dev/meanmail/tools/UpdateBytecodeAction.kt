package dev.meanmail.tools

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Action to update the Python bytecode display.
 * This action is available in the Python bytecode tool window.
 */
class UpdateBytecodeAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindowComponent = PythonBytecodeTool.getToolWindowComponent(project) ?: return
        toolWindowComponent.updateBytecode()
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if a project is available
        e.presentation.isEnabled = e.project != null
    }
}