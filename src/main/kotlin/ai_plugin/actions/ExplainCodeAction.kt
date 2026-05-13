package ai_plugin.actions

class ExplainCodeAction : BaseAIAction(
    actionLabel = "Explain Code",
    successTitle = "Groq Explanation",
    progressText = "Asking Groq...",
) {
    override fun callGroq(code: String, language: String, context: String) =
        GroqClient.explain(code, language, context)
}
