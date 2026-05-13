package ai_plugin.config

internal object PluginConfig {
    const val GROQ_API_KEY_ENV = "GROQ_API_KEY"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODEL = "llama-3.3-70b-versatile"
    const val MAX_CONTEXT_CHARS = 50_000
    val EXCLUDED_DIRS = setOf("build", ".gradle", ".idea", ".git", "node_modules", "out", "tmp")

    const val HTTP_CONNECT_TIMEOUT_SEC = 10L
    const val HTTP_WRITE_TIMEOUT_SEC = 10L
    const val HTTP_READ_TIMEOUT_SEC = 60L  // Groq can be slow on large prompts
    const val RESPONSE_CACHE_SIZE = 20
}
