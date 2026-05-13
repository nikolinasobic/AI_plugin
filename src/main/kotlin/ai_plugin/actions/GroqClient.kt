package ai_plugin.actions

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Collections
import java.util.concurrent.TimeUnit

object GroqClient {

    private val apiKey: String by lazy {
        System.getenv(PluginConfig.GROQ_API_KEY_ENV)
            ?: error("${PluginConfig.GROQ_API_KEY_ENV} environment variable is not set.")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(PluginConfig.HTTP_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(PluginConfig.HTTP_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(PluginConfig.HTTP_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    // In-memory LRU cache keyed by hash of (systemPrompt + code)
    private val responseCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(PluginConfig.RESPONSE_CACHE_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, String>) = size > PluginConfig.RESPONSE_CACHE_SIZE
        }
    )

    fun explain(code: String, language: String, projectContext: String, onToken: (String) -> Unit) {
        val prompt = buildSystemPrompt("Explain the following $language code clearly and concisely.", projectContext)
        callStreaming(prompt, code, onToken)
    }

    fun suggestOptimizations(code: String, language: String, projectContext: String, onToken: (String) -> Unit) {
        val instructions = "Suggest exactly 3 optimizations for the following $language code. " +
                "Only report problems you can clearly see in the code itself. Do not speculate or say things like " +
                "'might be slow' or 'could be expensive' — if you cannot see the problem directly in the code, skip it. " +
                "Number each suggestion clearly (1., 2., 3.) and for each one provide: a short title, explanation of " +
                "the exact problem you see, and an improved code snippet."
        callStreaming(buildSystemPrompt(instructions, projectContext), code, onToken)
    }

    fun generateReadme(projectName: String, projectContext: String, onToken: (String) -> Unit) {
        val instructions = """Generate a README.md for this project in Markdown format.
Include only sections that are relevant based on what you can see in the project files:
- Title and short description
- Features
- Prerequisites (tools, runtimes, versions)
- Setup & Installation
- Configuration (environment variables, settings)
- Usage (how to run it, with examples)
Base everything strictly on what is visible in the code and config files. Do not invent features or steps not evident from the code. Return only the Markdown content, no preamble or commentary."""
        callStreaming(buildSystemPrompt(instructions, projectContext), "Project name: $projectName", onToken)
    }

    private fun buildSystemPrompt(instructions: String, context: String): String {
        val prefix = "You are an expert software engineer. "
        return if (context.isBlank()) "$prefix$instructions"
        else "${prefix}Here is the full project context:\n\n$context\n\n$instructions"
    }

    private fun callStreaming(systemPrompt: String, code: String, onToken: (String) -> Unit) {
        val cacheKey = (systemPrompt + code).hashCode()
        responseCache[cacheKey]?.let { cached ->
            onToken(cached)
            return
        }

        val body = buildRequestBody(systemPrompt, code)
        val request = Request.Builder()
            .url(PluginConfig.GROQ_API_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        val accumulated = StringBuilder()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                error("Groq API error ${response.code}: $errorBody")
            }
            val source = response.body?.source() ?: error("Empty response body from Groq API")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") break
                parseStreamChunk(data)?.let { token ->
                    onToken(token)
                    accumulated.append(token)
                }
            }
        }

        if (accumulated.isNotEmpty()) responseCache[cacheKey] = accumulated.toString()
    }

    private fun buildRequestBody(systemPrompt: String, code: String): String {
        val escapedSystem = systemPrompt.escape()
        val escapedCode = code.escape()
        return """{"model":"${PluginConfig.GROQ_MODEL}","stream":true,"messages":[{"role":"system","content":"$escapedSystem"},{"role":"user","content":"```\n$escapedCode\n```"}]}"""
    }

    private fun parseStreamChunk(json: String): String? = try {
        val delta = JsonParser.parseString(json)
            .asJsonObject
            .getAsJsonArray("choices")[0]
            .asJsonObject
            .getAsJsonObject("delta")
        if (delta.has("content") && !delta.get("content").isJsonNull) delta.get("content").asString
        else null
    } catch (_: Exception) { null }

    private fun String.escape() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
