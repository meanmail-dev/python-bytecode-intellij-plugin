package dev.meanmail.tools

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Listener for file editor events to automatically update the Python bytecode tool window
 * when a Python file is selected or modified.
 */
class PythonBytecodeFileListener : FileEditorManagerListener {
    // Map to store document listeners for each project
    private val documentListeners = ConcurrentHashMap<Project, MutableMap<Document, DocumentListener>>()

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val project = event.manager.project

        // Unregister previous document listeners
        unregisterDocumentListeners(project)

        // Check if the new file is a Python file
        val file = event.newFile ?: return
        if (file.extension?.lowercase() != "py") {
            return
        }

        // Update the bytecode tool window
        updateBytecodeToolWindow(project)

        // Register document listener for the new file
        registerDocumentListener(project, file)
    }

    private fun registerDocumentListener(project: Project, file: VirtualFile) {
        // Get the document for the file
        val fileDocumentManager = FileDocumentManager.getInstance()
        val document = fileDocumentManager.getDocument(file) ?: return

        // Create a new document listener
        val listener = PythonBytecodeDocumentListener(project)

        // Add the listener to the document
        document.addDocumentListener(listener)

        // Store the listener for later removal
        documentListeners.computeIfAbsent(project) { ConcurrentHashMap() }[document] = listener
    }

    private fun unregisterDocumentListeners(project: Project) {
        // Get the listeners for this project
        val listeners = documentListeners[project] ?: return

        // Remove all listeners
        for ((document, listener) in listeners) {
            document.removeDocumentListener(listener)
        }

        // Clear the map
        listeners.clear()
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