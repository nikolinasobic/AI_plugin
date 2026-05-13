package ai_plugin.actions

class SuggestOptimizationsAction : BaseAIAction(
    actionLabel = "Suggest Optimizations",
    successTitle = "Optimization Suggestions",
    progressText = "Suggesting optimizations...",
) {
    override fun callGroq(code: String, language: String, context: String) =
        GroqClient.suggestOptimizations(code, language, context)
}
