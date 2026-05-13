package ai_plugin.actions

class ExplainCodeAction : BaseAIAction(
    actionLabel = "Explain Code",
    successTitle = "Groq Explanation",
) {
    override fun callGroqStreaming(code: String, language: String, context: String, onToken: (String) -> Unit) =
        GroqClient.explain(code, language, context, onToken)
}
