package dev.meanmail.tools

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Listener for document changes to automatically update the Python bytecode tool window
 * when a Python file is modified.
 */
class PythonBytecodeDocumentListener(private val project: Project) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        // Get the virtual file associated with the document
        val document = event.document
        val fileDocumentManager = FileDocumentManager.getInstance()
        val file = fileDocumentManager.getFile(document) ?: return

        // Check if it's a Python file
        if (file.extension?.lowercase() != "py") {
            return
        }

        // Update the bytecode tool window
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