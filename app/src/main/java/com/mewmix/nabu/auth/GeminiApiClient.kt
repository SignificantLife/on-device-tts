package com.mewmix.nabu.auth

import android.content.Context
import com.mewmix.nabu.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiApiClient(
    private val authenticator: GeminiAuthenticator = GeminiAuthenticator()
) {
    suspend fun sendPrompt(
        context: Context,
        prompt: String,
        model: String = "gemini-2.5-flash"
    ): Result<String> {
        val accessToken = authenticator.getValidAccessToken(context)
            ?: return Result.failure(IllegalStateException("Gemini account is not authenticated."))

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put("text", prompt)
                        )
                    )
                )
            )

        val response = withContext(Dispatchers.IO) {
            OAuthHttpClient.postJson(
                url = endpoint,
                payload = payload,
                headers = mapOf("Authorization" to "Bearer $accessToken")
            )
        }
        return response.map { json ->
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("Gemini returned no candidates: $json")
            }
            val first = candidates.optJSONObject(0)
                ?: throw IllegalStateException("Gemini candidate payload is invalid")
            val parts = first.optJSONObject("content")?.optJSONArray("parts")
            val text = buildString {
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val segment = parts.optJSONObject(i)?.optString("text").orEmpty()
                        if (segment.isNotBlank()) append(segment)
                    }
                }
            }.trim()
            if (text.isBlank()) {
                DebugLogger.log("GeminiApiClient: Empty text in response payload: $json")
                throw IllegalStateException("Gemini returned an empty response.")
            }
            text
        }
    }
}
