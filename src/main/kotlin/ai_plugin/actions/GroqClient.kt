package ai_plugin.actions

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GroqClient {

    private val apiKey = System.getenv("GROQ_API_KEY")
        ?: error("GROQ_API_KEY environment variable is not set.")

    private val http = OkHttpClient()

    fun explain(code: String): String {
        val escapedCode = code.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val body = """
            {
              "model": "llama-3.3-70b-versatile",
              "messages": [
                {"role": "system", "content": "You are an expert software engineer. Explain the following code clearly and concisely."},
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
            // Parse: {"choices":[{"message":{"content":"..."}}]}
            val contentStart = bodyString.indexOf("\"content\":\"") + 11
            check(contentStart > 11) { "Unexpected Groq response format: $bodyString" }
            val contentEnd = bodyString.indexOf("\"}", contentStart)
            return bodyString.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }
}