package ai_plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages

abstract class BaseAIAction(
    private val actionLabel: String,
    private val successTitle: String,
) : AnAction() {

    abstract fun callGroqStreaming(code: String, language: String, context: String, onToken: (String) -> Unit)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val code = editor.selectionModel.selectedText
        val currentFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val language = ProjectScanner.languageDisplayName(currentFile?.extension)

        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project, "Select some code first.", actionLabel)
            return
        }

        val dialog = StreamingDialog(project, successTitle)

        ApplicationManager.getApplication().executeOnPooledThread {
            val context = ProjectScanner.collectContext(project, currentFile)
            try {
                callGroqStreaming(code, language, context) { token ->
                    dialog.bufferToken(token)
                }
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
