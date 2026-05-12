package ai_plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages

class SuggestOptimizationsAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor  = event.getData(CommonDataKeys.EDITOR) ?: return
        val code    = editor.selectionModel.selectedText

        if (code.isNullOrBlank()) {
            Messages.showWarningDialog(project, "Select some code first.", "Suggest Optimizations")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Suggesting optimizations...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Scanning project..."
                val context = ProjectScanner.collectContext(project)
                indicator.text = "Asking Groq..."
                val (title, message) = try {
                    "Optimization Suggestions" to GroqClient().suggestOptimizations(code, context)
                } catch (e: Exception) {
                    "Error" to (e.message ?: "Unknown error")
                }
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(project, message, title)
                }
            }
        })
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible =
            editor != null && editor.selectionModel.hasSelection()
    }
}
