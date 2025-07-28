package dev.meanmail.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.Color
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.swing.JComponent


/**
 * Tool window component that displays Python bytecode for the current file.
 * Implements selection synchronization between the source editor and bytecode display.
 */
class PythonBytecodeToolWindow(private val project: Project) : Disposable, PythonBytecodeToolWindowComponent {
    // Editor for displaying bytecode
    private val editor: EditorEx = createEditor()

    // Selection listener for the source editor
    private var selectionListener: SelectionListener? = null

    // Reference to the current source editor
    private var currentSourceEditor: Editor? = null

    // List of active highlights in the bytecode editor
    private var highlightRanges = mutableListOf<com.intellij.openapi.editor.markup.RangeHighlighter>()

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

    /**
     * Updates the bytecode display with the current file's bytecode and sets up the selection listener.
     *
     * @param editor The editor to update
     */
    private fun updateBytecodeInternal(editor: EditorEx) {
        val text = getBytecode(project)
        ApplicationManager.getApplication().runWriteAction {
            editor.document.setReadOnly(false)
            editor.document.setText(text)
            editor.document.setReadOnly(true)
        }

        // After updating bytecode, setup selection listener
        setupSelectionListener()
    }

    /**
     * Sets up a selection listener on the current source editor.
     * This listener will highlight the corresponding bytecode when text is selected in the source editor.
     */
    private fun setupSelectionListener() {
        // Remove previous listener if exists
        removeSelectionListener()

        // Get current editor
        val fileEditorManager = FileEditorManager.getInstance(project)
        val currentEditor = fileEditorManager.selectedTextEditor ?: return
        currentSourceEditor = currentEditor

        // Create and add selection listener
        selectionListener = object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                highlightBytecodeForSelection(e)
            }
        }

        currentEditor.selectionModel.addSelectionListener(selectionListener!!)
    }

    /**
     * Removes the selection listener from the source editor and clears any highlights.
     * This is called when the bytecode is updated or when the tool window is disposed.
     */
    private fun removeSelectionListener() {
        currentSourceEditor?.let { editor ->
            selectionListener?.let { listener ->
                editor.selectionModel.removeSelectionListener(listener)
            }
        }
        selectionListener = null
        currentSourceEditor = null

        // Clear existing highlights
        clearHighlights()
    }

    /**
     * Clears all highlights from the bytecode editor.
     */
    private fun clearHighlights() {
        for (highlighter in highlightRanges) {
            editor.markupModel.removeHighlighter(highlighter)
        }
        highlightRanges.clear()
    }

    /**
     * Highlights the bytecode that corresponds to the selected text in the source editor.
     *
     * @param e The selection event containing information about the selection
     */
    private fun highlightBytecodeForSelection(e: SelectionEvent) {
        // Clear previous highlights
        clearHighlights()

        // If no selection, return
        if (e.newRange.startOffset == e.newRange.endOffset) return

        // Get selected lines (1-based to match Python line numbers)
        val document = e.editor.document
        val startLine = document.getLineNumber(e.newRange.startOffset) + 1
        val endLine = document.getLineNumber(e.newRange.endOffset) + 1
        val selectedLines = (startLine..endLine).toSet()

        // Parse bytecode to find corresponding lines
        val bytecodeText = editor.document.text
        if (bytecodeText.isBlank() || bytecodeText.startsWith("No Python")) {
            return // No valid bytecode to highlight
        }

        val bytecodeLines = bytecodeText.lines()

        // Highlight corresponding bytecode lines
        var i = 0
        while (i < bytecodeLines.size) {
            val line = bytecodeLines[i]
            // Check if line starts with a line number (e.g. "  1           LOAD_CONST               0 (1)")
            val lineNumberMatch = Regex("^\\s*(\\d+)\\s+").find(line)
            if (lineNumberMatch != null) {
                try {
                    val sourceLineNumber = lineNumberMatch.groupValues[1].toInt()
                    if (sourceLineNumber in selectedLines) {
                        // Find the end of this bytecode block
                        var endIndex = i + 1
                        while (endIndex < bytecodeLines.size) {
                            val nextLine = bytecodeLines[endIndex]
                            if (Regex("^\\s*\\d+\\s+").find(nextLine) != null) {
                                break
                            }
                            endIndex++
                        }

                        // Highlight this block
                        highlightBytecodeLines(i, endIndex - 1)
                    }
                } catch (ex: NumberFormatException) {
                    // Skip lines with invalid line numbers
                    continue
                }
            }
            i++
        }
    }

    /**
     * Highlights a range of lines in the bytecode editor.
     *
     * @param startLine The index of the first line to highlight
     * @param endLine The index of the last line to highlight
     */
    private fun highlightBytecodeLines(startLine: Int, endLine: Int) {
        val document = editor.document
        val startOffset = document.getLineStartOffset(startLine)
        val endOffset = document.getLineEndOffset(endLine)

        val attributes = TextAttributes().apply {
            backgroundColor = Color(255, 255, 0, 50) // Light yellow highlight
        }

        val highlighter = editor.markupModel.addRangeHighlighter(
            startOffset, endOffset,
            HighlighterLayer.SELECTION,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )

        highlightRanges.add(highlighter)
    }

    override fun updateBytecode() {
        updateBytecodeInternal(editor)
    }

    override val component: JComponent
        get() {
            return editor.component
        }

    override fun dispose() {
        removeSelectionListener()
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
