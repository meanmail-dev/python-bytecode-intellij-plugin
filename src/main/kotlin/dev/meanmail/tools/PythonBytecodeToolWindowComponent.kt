package dev.meanmail.tools

import javax.swing.JComponent

/**
 * Interface for the Python bytecode tool window component.
 * This interface defines the methods that must be implemented by any component
 * that displays Python bytecode in a tool window.
 */
interface PythonBytecodeToolWindowComponent {
    /**
     * Returns the Swing component that displays the bytecode.
     */
    val component: JComponent

    /**
     * Updates the bytecode display with the current file's bytecode.
     */
    fun updateBytecode()
}