package dev.meanmail.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.sdk.PythonSdkType
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JComponent


class PythonBytecodeToolWindow(private val project: Project) : Disposable, PythonBytecodeToolWindowComponent {
    private val editor: EditorEx = createEditor()

    private fun createEditor(): EditorEx {
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("Click Update button")
        val editor: EditorEx = editorFactory.createViewer(
            document, project, EditorKind.MAIN_EDITOR
        ) as EditorEx
        editor.permanentHeaderComponent = panel {
            row {
                button("Update") {
                    updateBytecode()
                }
            }
        }
        editor.headerComponent = editor.permanentHeaderComponent

        return editor
    }

    private fun updateBytecodeInternal(editor: EditorEx) {
        val text = getBytecode(project)
        ApplicationManager.getApplication().runWriteAction {
            editor.document.setReadOnly(false)
            editor.document.setText(text)
            editor.document.setReadOnly(true)
        }
    }

    override fun updateBytecode() {
        updateBytecodeInternal(editor)
    }

    override val component: JComponent
        get() {
            return editor.component
        }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}


fun getSdk(project: Project): Sdk? {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectSdk = projectRootManager.projectSdk ?: return null
    if (projectSdk.sdkType is PythonSdkType) {
        return projectSdk
    }
    return null
}

fun execPython(sdk: Sdk, path: String, filename: String): String? {
    try {
        val parts = listOf(sdk.homePath, path, filename)
        val proc = ProcessBuilder(*parts.toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        proc.waitFor(5, TimeUnit.SECONDS)
        val errors = proc.errorStream.bufferedReader().readText()
        if (errors.isNotEmpty()) {
            return null
        }
        return proc.inputStream.bufferedReader().readText()
    } catch (_: IOException) {
        return null
    }
}


fun getBytecode(project: Project): String {
    val sdk = getSdk(project) ?: return "No Python SDK"
    val manager = FileEditorManager.getInstance(project)
    val file = manager.selectedEditor?.file ?: return "No file"
    if (file.extension?.lowercase() != "py") {
        return "No Python file"
    }

    // Get the current document content
    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = fileDocumentManager.getDocument(file) ?: return "Cannot get document"
    val currentContent = document.text

    // Create a temporary file with the current document content
    val tempFile = File.createTempFile("python_bytecode_", ".py")
    tempFile.writeText(currentContent)
    tempFile.deleteOnExit() // Ensure the file is deleted when the JVM exits

    // Create a temporary script file
    val scriptResource = object {}.javaClass.getResource("/get_bytecode.py")
    val script = File.createTempFile("tmp", null)
    script.writeText(scriptResource!!.readText())
    script.deleteOnExit() // Ensure the file is deleted when the JVM exits

    return execPython(sdk, script.path, tempFile.path) ?: "Compilation error"
}
