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

    fun explain(code: String, language: String, projectContext: String): String {
        val prompt = buildSystemPrompt("Explain the following $language code clearly and concisely.", projectContext)
        return call(prompt, code)
    }

    fun suggestOptimizations(code: String, language: String, projectContext: String): String {
        val instructions = "Suggest exactly 3 optimizations for the following $language code. " +
                "Only report problems you can clearly see in the code itself. Do not speculate or say things like " +
                "'might be slow' or 'could be expensive' — if you cannot see the problem directly in the code, skip it. " +
                "Number each suggestion clearly (1., 2., 3.) and for each one provide: a short title, explanation of " +
                "the exact problem you see, and an improved code snippet."
        val prompt = buildSystemPrompt(instructions, projectContext)
        return call(prompt, code)
    }

    private fun buildSystemPrompt(instructions: String, context: String): String {
        val prefix = "You are an expert software engineer. "
        return if (context.isBlank()) {
            "$prefix$instructions"
        } else {
            "${prefix}Here is the full project context:\n\n$context\n\n$instructions"
        }
    }

    private fun call(systemPrompt: String, code: String): String {
        val cacheKey = (systemPrompt + code).hashCode()
        responseCache[cacheKey]?.let { return it }

        val body = buildRequestBody(systemPrompt, code)

        val request = Request.Builder()
            .url(PluginConfig.GROQ_API_URL)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        http.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
                ?: error("Empty response body from Groq API")
            if (!response.isSuccessful) error("Groq API error ${response.code}: $bodyString")
            return parseContent(bodyString).also { responseCache[cacheKey] = it }
        }
    }

    private fun buildRequestBody(systemPrompt: String, code: String): String {
        val escapedSystem = systemPrompt.escape()
        val escapedCode = code.escape()
        return """{"model":"${PluginConfig.GROQ_MODEL}","messages":[{"role":"system","content":"$escapedSystem"},{"role":"user","content":"```\n$escapedCode\n```"}]}"""
    }

    private fun parseContent(json: String): String {
        return try {
            JsonParser.parseString(json)
                .asJsonObject
                .getAsJsonArray("choices")[0]
                .asJsonObject
                .getAsJsonObject("message")
                .get("content")
                .asString
        } catch (e: Exception) {
            error("Unexpected Groq response format: $json")
        }
    }

    private fun String.escape() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
