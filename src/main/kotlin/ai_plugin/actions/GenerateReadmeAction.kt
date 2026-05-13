package ai_plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class GenerateReadmeAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val projectName = project.name

        val dialog = StreamingDialog(
            project = project,
            title = "README Preview",
            okButtonText = "Write to README.md",
            editable = true,
            onOk = { content -> writeReadme(project, content) },
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            val context = ProjectScanner.collectContext(project, currentFile = null)
            try {
                GroqClient.generateReadme(projectName, context) { token ->
                    dialog.bufferToken(token)
                }
                ApplicationManager.getApplication().invokeLater({ dialog.markComplete() }, ModalityState.any())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "README Generation Failed")
                }, ModalityState.any())
            }
        }

        dialog.show()
    }

    private fun writeReadme(project: Project, content: String) {
        WriteCommandAction.runWriteCommandAction(project, "Generate README", null, {
            val basePath = project.basePath ?: return@runWriteCommandAction
            val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return@runWriteCommandAction
            val vf = baseDir.findChild("README.md") ?: baseDir.createChildData(this, "README.md")
            VfsUtil.saveText(vf, content)
            FileEditorManager.getInstance(project).openFile(vf, true)
        })
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }
}
