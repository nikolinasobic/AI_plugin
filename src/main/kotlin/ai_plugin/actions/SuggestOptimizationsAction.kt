package ai_plugin.actions

import ai_plugin.client.GroqClient
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class SuggestOptimizationsAction : BaseAIAction(
    actionLabel = "Suggest Optimizations",
    successTitle = "Optimization Suggestions",
) {
    override fun callGroqStreaming(code: String, language: String, context: String, onToken: (String) -> Unit) =
        GroqClient.suggestOptimizations(code, language, context, onToken)

    override fun extraButton(
        project: Project,
        editor: Editor,
        selectionStart: Int,
        selectionEnd: Int,
    ): Pair<String, (String) -> Unit> = "Apply to Editor" to { responseText ->
        val blocks = parseCodeBlocks(responseText)
        when {
            blocks.isEmpty() ->
                Messages.showInfoMessage(project, "No code blocks found in the response.", "Apply to Editor")
            blocks.size == 1 ->
                applyToEditor(project, editor, selectionStart, selectionEnd, blocks[0])
            else -> {
                val index = Messages.showChooseDialog(
                    project,
                    "Which optimization would you like to apply?",
                    "Apply to Editor",
                    null,
                    blocks.mapIndexed { i, _ -> "Optimization ${i + 1}" }.toTypedArray(),
                    "Optimization 1",
                )
                if (index >= 0) applyToEditor(project, editor, selectionStart, selectionEnd, blocks[index])
            }
        }
    }

    private fun parseCodeBlocks(text: String): List<String> =
        Regex("""```\w*\n([\s\S]*?)```""").findAll(text)
            .map { it.groupValues[1].trimEnd() }
            .toList()

    private fun applyToEditor(project: Project, editor: Editor, start: Int, end: Int, code: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.replaceString(start, end, code)
        }
    }
}
