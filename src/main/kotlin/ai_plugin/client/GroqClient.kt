package ai_plugin.client

import ai_plugin.config.PluginConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

internal object GroqClient {

    private val apiKey: String by lazy {
        System.getenv(PluginConfig.GROQ_API_KEY_ENV)
            ?: error("${PluginConfig.GROQ_API_KEY_ENV} environment variable is not set.")
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(PluginConfig.HTTP_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(PluginConfig.HTTP_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(PluginConfig.HTTP_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // In-memory LRU cache keyed by hash of (systemPrompt + code)
    private val responseCache: MutableMap<Int, String> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(PluginConfig.RESPONSE_CACHE_SIZE + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, String>) = size > PluginConfig.RESPONSE_CACHE_SIZE
        }
    )

    @Volatile private var currentCall: okhttp3.Call? = null

    fun cancelCurrentCall() {
        currentCall?.cancel()
    }

    fun explain(code: String, language: String, projectContext: String, onToken: (String) -> Unit) {
        val prompt = buildSystemPrompt("Explain the following $language code clearly and concisely.", projectContext)
        callStreaming(prompt, code, onToken)
    }

    fun suggestOptimizations(code: String, language: String, projectContext: String, onToken: (String) -> Unit) {
        val instructions = "Suggest the most impactful optimizations for the following $language code. " +
                "Only report problems you can clearly see in the code itself. Do not speculate or say things like " +
                "'might be slow' or 'could be expensive' — if you cannot see the problem directly in the code, skip it. " +
                "For each optimization provide: a short title, explanation of the exact problem, " +
                "and an improved code snippet in a fenced code block."
        callStreaming(buildSystemPrompt(instructions, projectContext), code, onToken)
    }

    fun generateReadme(
        projectName: String,
        projectContext: String,
        existingContent: String? = null,
        onToken: (String) -> Unit,
    ) {
        val instructions = if (existingContent == null) {
            """Generate a README.md for this project in Markdown format.
Include only sections that are relevant based on what you can see in the project files:
- Title and short description
- Features
- Prerequisites (tools, runtimes, versions)
- Setup & Installation
- Configuration (environment variables, settings)
- Usage (how to run it, with examples)
Base everything strictly on what is visible in the code and config files. Do not invent features or steps not evident from the code. Return only the Markdown content, no preamble or commentary."""
        } else {
            """Update and improve the following existing README.md based on the current project state.
Keep what is accurate, update what has changed, and add any missing sections.
Do not remove sections unless they are clearly no longer relevant.
Return only the updated Markdown content, no preamble or commentary.

Existing README:
$existingContent"""
        }
        callStreaming(buildSystemPrompt(instructions, projectContext), "Project name: $projectName", onToken)
    }

    private fun buildSystemPrompt(instructions: String, context: String): String {
        val prefix = "You are an expert software engineer. "
        return if (context.isBlank()) "$prefix$instructions"
        else "${prefix}Here is the full project context:\n\n$context\n\n$instructions"
    }

    private data class Message(val role: String, val content: String)
    private data class ChatRequest(val model: String, val stream: Boolean, val messages: List<Message>)

    private fun callStreaming(systemPrompt: String, code: String, onToken: (String) -> Unit) {
        val cacheKey = (systemPrompt + code).hashCode()
        responseCache[cacheKey]?.let { cached ->
            onToken(cached)
            return
        }

        val body = gson.toJson(ChatRequest(
            model = PluginConfig.GROQ_MODEL,
            stream = true,
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", "```\n$code\n```"),
            )
        ))
        val request = Request.Builder()
            .url(PluginConfig.GROQ_API_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        val accumulated = StringBuilder()
        val call = http.newCall(request)
        currentCall = call
        var completed = false
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    error("Groq API error ${response.code}: $errorBody")
                }
                val source = response.body?.source() ?: error("Empty response body from Groq API")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") { completed = true; break }
                    parseStreamChunk(data)?.let { token ->
                        onToken(token)
                        accumulated.append(token)
                    }
                }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) throw e
        } finally {
            currentCall = null
        }

        if (completed && accumulated.isNotEmpty()) responseCache[cacheKey] = accumulated.toString()
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
}
