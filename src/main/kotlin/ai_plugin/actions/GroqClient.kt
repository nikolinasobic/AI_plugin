package ai_plugin.actions

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GroqClient {

    private val apiKey = System.getenv("GROQ_API_KEY")
        ?: error("GROQ_API_KEY environment variable is not set.")

    private val http = OkHttpClient()

    fun explain(code: String, projectContext: String): String {
        val systemPrompt = if (projectContext.isBlank()) {
            "You are an expert software engineer. Explain the following code clearly and concisely."
        } else {
            "You are an expert software engineer. Here is the full project context:\n\n$projectContext\n\nUse this context to give a more accurate explanation."
        }
        return call(systemPrompt, code)
    }

    fun suggestOptimizations(code: String, projectContext: String): String {
        val systemPrompt = if (projectContext.isBlank()) {
            "You are an expert software engineer. Suggest exactly 3 optimizations for the following code. " +
                    "Only report problems you can clearly see in the code itself. Do not speculate or say things like 'might be slow' or 'could be expensive' — if you cannot see the problem directly in the code, skip it. " +
                    "Number each suggestion clearly (1., 2., 3.) and for each one provide: a short title, explanation of the exact problem you see, and an improved code snippet."
        } else {
            "You are an expert software engineer. Here is the full project context:\n\n$projectContext\n\n" +
                    "Using this context, suggest exactly 3 optimizations for the selected code. " +
                    "Only report problems you can clearly see in the code. Do not speculate or say things like 'might be slow' or 'could be expensive' — if you cannot see the problem directly, skip it. " +
                    "Number each suggestion clearly (1., 2., 3.) and for each one provide: a short title, explanation of the exact problem you see, and an improved code snippet."
        }
        return call(systemPrompt, code)
    }

    private fun call(systemPrompt: String, code: String): String {
        val escapedCode = code.escape()
        val escapedSystem = systemPrompt.escape()

        val body = """
            {
              "model": "llama-3.3-70b-versatile",
              "messages": [
                {"role": "system", "content": "$escapedSystem"},
                {"role": "user", "content": "```\n$escapedCode\n```"}
              ]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val bodyString = response.body!!.string()
            if (!response.isSuccessful) error("Groq API error: ${response.code} $bodyString")
            val contentStart = bodyString.indexOf("\"content\":\"") + 11
            check(contentStart > 11) { "Unexpected Groq response format: $bodyString" }
            val contentEnd = bodyString.indexOf("\"}", contentStart)
            return bodyString.substring(contentStart, contentEnd).unescape()
        }
    }

    private fun String.escape() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun String.unescape() = replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }
}

