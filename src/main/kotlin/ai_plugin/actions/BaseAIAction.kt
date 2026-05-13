package ai_plugin.actions

import ai_plugin.client.GroqClient
import ai_plugin.scanner.ProjectScanner
import ai_plugin.ui.StreamingDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages

abstract class BaseAIAction(
    private val actionLabel: String,
    private val successTitle: String,
) : AnAction() {

    abstract fun callGroqStreaming(code: String, language: String, context: String, onToken: (String) -> Unit)

    // Override to add a left-side action button (e.g. "Apply to Editor")
    open fun extraButton(
        project: Project,
        editor: Editor,
        selectionStart: Int,
        selectionEnd: Int,
    ): Pair<String, (String) -> Unit>? = null

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val code = editor.selectionModel.selectedText
        val currentFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val language = ProjectScanner.languageDisplayName(currentFile?.extension)
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd

        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project, "Select some code first.", actionLabel)
            return
        }

        val dialog = StreamingDialog(
            project = project,
            title = successTitle,
            onStop = { GroqClient.cancelCurrentCall() },
            extraButton = extraButton(project, editor, selectionStart, selectionEnd),
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            val context = ProjectScanner.collectContext(project, currentFile)
            try {
                callGroqStreaming(code, language, context) { token -> dialog.bufferToken(token) }
                ApplicationManager.getApplication().invokeLater({ dialog.markComplete() }, ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "Error")
                }, ModalityState.any())
            }
        }

        dialog.show()
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible =
            editor != null && editor.selectionModel.hasSelection()
    }
}
