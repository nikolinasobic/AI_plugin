package ai_plugin.actions

import ai_plugin.client.GroqClient
import ai_plugin.scanner.ProjectScanner
import ai_plugin.ui.StreamingDialog
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
        val basePath = project.basePath ?: return
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return

        val existingReadme = baseDir.findChild("README.md")
        val existingContent: String?

        if (existingReadme != null) {
            val choice = Messages.showYesNoCancelDialog(
                project,
                "README.md already exists. What would you like to do?",
                "README Already Exists",
                "Update Existing",
                "Replace",
                "Cancel",
                Messages.getQuestionIcon(),
            )
            when (choice) {
                Messages.YES -> existingContent = try {
                    existingReadme.contentsToByteArray().toString(Charsets.UTF_8)
                } catch (_: Exception) { null }
                Messages.NO -> existingContent = null
                else -> return
            }
        } else {
            existingContent = null
        }

        val dialog = StreamingDialog(
            project = project,
            title = "README Preview",
            okButtonText = "Write to README.md",
            editable = true,
            onOk = { content -> writeReadme(project, content) },
            onStop = { GroqClient.cancelCurrentCall() },
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            val context = ProjectScanner.collectContext(project, currentFile = null)
            try {
                GroqClient.generateReadme(projectName, context, existingContent) { token ->
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
