package ai_plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

class StreamingDialog(
    project: Project,
    title: String,
    okButtonText: String = "Close",
    editable: Boolean = false,
    private val onOk: ((String) -> Unit)? = null,
) : DialogWrapper(project) {

    private val textArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = editable
    }

    private val statusLabel = JLabel("Generating...").apply {
        foreground = java.awt.Color.GRAY
    }

    private val tokenBuffer = StringBuilder()

    // Batches token appends every 50ms on the EDT instead of one dispatch per token
    private val flushTimer = Timer(50) { flushBuffer() }.apply { isRepeats = true }

    val text: String get() = textArea.text

    init {
        this.title = title
        setOKButtonText(okButtonText)
        init()
        flushTimer.start()
    }

    override fun createCenterPanel(): JPanel = JPanel(BorderLayout(0, 6)).apply {
        add(JBScrollPane(textArea).apply { preferredSize = Dimension(820, 500) }, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    // Safe to call from any thread
    fun bufferToken(token: String) {
        synchronized(tokenBuffer) { tokenBuffer.append(token) }
    }

    // Called on EDT by the timer
    private fun flushBuffer() {
        val batch = synchronized(tokenBuffer) {
            val s = tokenBuffer.toString()
            tokenBuffer.clear()
            s
        }
        if (batch.isEmpty()) return
        textArea.append(batch)
        textArea.caretPosition = textArea.document.length
    }

    // Must be called on EDT (via invokeLater with ModalityState.any())
    fun markComplete() {
        flushTimer.stop()
        flushBuffer()
        statusLabel.text = "Done"
    }

    override fun dispose() {
        flushTimer.stop()
        super.dispose()
    }

    override fun doOKAction() {
        super.doOKAction()
        onOk?.invoke(text)
    }
}
