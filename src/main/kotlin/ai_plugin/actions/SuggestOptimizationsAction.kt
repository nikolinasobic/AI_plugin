package ai_plugin.actions

import ai_plugin.client.GroqClient

class SuggestOptimizationsAction : BaseAIAction(
    actionLabel = "Suggest Optimizations",
    successTitle = "Optimization Suggestions",
) {
    override fun callGroqStreaming(code: String, language: String, context: String, onToken: (String) -> Unit) =
        GroqClient.suggestOptimizations(code, language, context, onToken)
}
