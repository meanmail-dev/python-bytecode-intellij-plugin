package dev.meanmail.tools

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
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

    // Caret listener for the source editor
    private var caretListener: CaretListener? = null

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
     * Updates the bytecode display with the current file's bytecode and sets up the listeners.
     * Also triggers initial highlighting based on current selection or caret position.
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

        // After updating bytecode, setup listeners
        setupSelectionListener()
        setupCaretListener()

        // Trigger initial highlighting based on current selection or caret position
        triggerInitialHighlighting()
    }

    /**
     * Triggers initial highlighting based on the current selection or caret position.
     * This is called after the bytecode is first displayed to ensure highlighting is updated.
     */
    private fun triggerInitialHighlighting() {
        val currentEditor = currentSourceEditor ?: return
        val document = currentEditor.document

        // Check if there's a selection
        val selectionModel = currentEditor.selectionModel
        val selectedLines: Set<Int>

        if (selectionModel.hasSelection()) {
            // Get selected lines (1-based to match Python line numbers)
            val startLine = document.getLineNumber(selectionModel.selectionStart) + 1
            val endLine = document.getLineNumber(selectionModel.selectionEnd) + 1
            selectedLines = (startLine..endLine).toSet()
        } else {
            // No selection, use the line where cursor is positioned
            val caretModel = currentEditor.caretModel
            val caretOffset = caretModel.offset
            val caretLine = document.getLineNumber(caretOffset) + 1
            selectedLines = setOf(caretLine)
        }

        // Highlight the bytecode for the selected lines
        highlightBytecodeForLines(selectedLines)
    }

    /**
     * Sets up a selection listener on the current source editor.
     * This listener will highlight the corresponding bytecode when text is selected in the source editor.
     */
    private fun setupSelectionListener() {
        // Remove all previous listeners
        removeAllListeners()

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
     * Removes the selection listener from the source editor.
     * This is called when cleaning up individual listeners.
     */
    private fun removeSelectionListener() {
        currentSourceEditor?.let { editor ->
            selectionListener?.let { listener ->
                editor.selectionModel.removeSelectionListener(listener)
            }
        }
        selectionListener = null
    }

    /**
     * Sets up a caret listener on the current source editor.
     * This listener will highlight the corresponding bytecode when the caret position changes.
     */
    private fun setupCaretListener() {
        // Get current editor (should already be set by setupSelectionListener)
        val currentEditor = currentSourceEditor ?: return

        // Create and add caret listener
        caretListener = object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                highlightBytecodeForCaret(e)
            }
        }

        currentEditor.caretModel.addCaretListener(caretListener!!)
    }

    /**
     * Removes the caret listener from the source editor.
     * This is called when the bytecode is updated or when the tool window is disposed.
     */
    private fun removeCaretListener() {
        currentSourceEditor?.let { editor ->
            caretListener?.let { listener ->
                editor.caretModel.removeCaretListener(listener)
            }
        }
        caretListener = null
    }

    /**
     * Removes all listeners from the source editor and clears any highlights.
     * This is called when the bytecode is updated or when the tool window is disposed.
     */
    private fun removeAllListeners() {
        removeSelectionListener()
        removeCaretListener()
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
     * If no text is selected, highlights the bytecode for the line where the cursor is positioned.
     *
     * @param e The selection event containing information about the selection
     */
    private fun highlightBytecodeForSelection(e: SelectionEvent) {
        // Clear previous highlights
        clearHighlights()

        // Get document
        val document = e.editor.document

        // Check if there's a selection
        val selectedLines: Set<Int>
        if (e.newRange.startOffset == e.newRange.endOffset) {
            // No selection, use the line where cursor is positioned
            val cursorLine = document.getLineNumber(e.newRange.startOffset) + 1
            selectedLines = setOf(cursorLine)
        } else {
            // Get selected lines (1-based to match Python line numbers)
            val startLine = document.getLineNumber(e.newRange.startOffset) + 1
            val endLine = document.getLineNumber(e.newRange.endOffset) + 1
            selectedLines = (startLine..endLine).toSet()
        }

        // Highlight the bytecode for the selected lines
        highlightBytecodeForLines(selectedLines)
    }

    /**
     * Highlights the bytecode that corresponds to the line where the caret is positioned.
     *
     * @param e The caret event containing information about the caret position
     */
    private fun highlightBytecodeForCaret(e: CaretEvent) {
        // Clear previous highlights
        clearHighlights()

        // Get document
        val document = e.editor.document

        // Get the caret offset, return if caret is null
        val caretOffset = e.caret?.offset ?: return

        // Get the line where the caret is positioned (1-based to match Python line numbers)
        val caretLine = document.getLineNumber(caretOffset) + 1
        val selectedLines = setOf(caretLine)

        // Highlight the bytecode for the caret line
        highlightBytecodeForLines(selectedLines)
    }

    /**
     * Highlights the bytecode that corresponds to the specified source lines.
     *
     * @param selectedLines The set of source line numbers (1-based) to highlight
     */
    private fun highlightBytecodeForLines(selectedLines: Set<Int>) {
        // Parse bytecode to find corresponding lines
        val bytecodeText = editor.document.text
        if (bytecodeText.isBlank() || bytecodeText.startsWith("No Python")) {
            return // No valid bytecode to highlight
        }

        val bytecodeLines = bytecodeText.lines()

        // First, collect all bytecode blocks and their source line numbers
        val bytecodeBlocks = mutableListOf<Pair<Int, Pair<Int, Int>>>() // (sourceLineNumber, (startIndex, endIndex))

        var i = 0
        while (i < bytecodeLines.size) {
            val line = bytecodeLines[i]
            // Check if line starts with a line number (e.g. "  1           LOAD_CONST               0 (1)")
            val lineNumberMatch = Regex("^\\s*(\\d+)\\s+").find(line)
            if (lineNumberMatch != null) {
                try {
                    val sourceLineNumber = lineNumberMatch.groupValues[1].toInt()

                    // Find the end of this bytecode block
                    var endIndex = i + 1
                    while (endIndex < bytecodeLines.size) {
                        val nextLine = bytecodeLines[endIndex]
                        if (Regex("^\\s*\\d+\\s+").find(nextLine) != null) {
                            break
                        }
                        endIndex++
                    }

                    // Add this block to our list
                    bytecodeBlocks.add(Pair(sourceLineNumber, Pair(i, endIndex - 1)))
                } catch (ex: NumberFormatException) {
                    // Skip lines with invalid line numbers
                }
            }
            i++
        }

        // Sort blocks by source line number
        bytecodeBlocks.sortBy { it.first }

        // Now determine the range of source lines covered by each block
        for (j in bytecodeBlocks.indices) {
            val (sourceLineNumber, indexPair) = bytecodeBlocks[j]
            val (startIndex, endIndex) = indexPair

            // Determine the end source line for this block
            val endSourceLine = if (j < bytecodeBlocks.size - 1) {
                bytecodeBlocks[j + 1].first - 1
            } else {
                Int.MAX_VALUE // Last block covers all remaining lines
            }

            // Check if any of the selected lines fall within this block's range
            if (selectedLines.any { it in sourceLineNumber..endSourceLine }) {
                // Highlight this block
                highlightBytecodeLines(startIndex, endIndex)
            }
        }
    }

    /**
     * Highlights a range of lines in the bytecode editor and scrolls to make them visible.
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

        // Scroll to make the highlighted area visible
        ApplicationManager.getApplication().invokeLater {
            // Get the visible area height in lines
            val visibleAreaHeight = editor.scrollingModel.visibleArea.height / editor.lineHeight

            // Calculate the number of lines in the highlighted block
            val startLine = editor.document.getLineNumber(startOffset)
            val endLine = editor.document.getLineNumber(endOffset)
            val blockHeight = endLine - startLine + 1

            // Determine the appropriate scroll type based on block size
            if (blockHeight > visibleAreaHeight) {
                // If the block doesn't fit in the visible area, show the first line at the top
                editor.scrollingModel.scrollTo(
                    editor.offsetToLogicalPosition(startOffset),
                    ScrollType.MAKE_VISIBLE
                )
            } else {
                // If the block fits, center it as before
                // Calculate the middle offset
                val rawMiddleOffset = (startOffset + endOffset) / 2
                // Get the line number at the middle offset
                val middleLine = editor.document.getLineNumber(rawMiddleOffset)
                // Get the start offset of that line to ensure middleOffset is at the beginning of a line
                val middleOffset = editor.document.getLineStartOffset(middleLine)
                editor.scrollingModel.scrollTo(
                    editor.offsetToLogicalPosition(middleOffset),
                    ScrollType.MAKE_VISIBLE
                )
            }
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
        removeAllListeners()
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
