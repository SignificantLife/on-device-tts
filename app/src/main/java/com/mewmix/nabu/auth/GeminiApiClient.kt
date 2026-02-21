package com.mewmix.nabu.auth

import android.content.Context
import com.mewmix.nabu.chat.LlmMessage
import com.mewmix.nabu.utils.DebugLogger
import com.mewmix.nabu.utils.SettingsManager
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
        return sendConversation(
            context = context,
            conversation = listOf(LlmMessage(role = "user", content = prompt)),
            model = model
        )
    }

    suspend fun sendConversation(
        context: Context,
        conversation: List<LlmMessage>,
        model: String = "gemini-2.5-flash"
    ): Result<String> {
        val accessToken = authenticator.getValidAccessToken(context)
            ?: return Result.failure(IllegalStateException("Gemini account is not authenticated."))

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val payload = JSONObject()
        val contents = JSONArray()

        val systemText = conversation
            .filter { it.role.equals("system", ignoreCase = true) && it.content.isNotBlank() }
            .joinToString("\n\n") { it.content.trim() }
        if (systemText.isNotBlank()) {
            payload.put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemText))
                )
            )
        }

        conversation
            .filterNot { it.role.equals("system", ignoreCase = true) }
            .filter { it.content.isNotBlank() }
            .forEach { message ->
                contents.put(
                    JSONObject()
                        .put("role", mapRole(message.role))
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", message.content))
                        )
                )
            }
        if (contents.length() == 0) {
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "Hello"))
                    )
            )
        }
        payload.put("contents", contents)

        val headers = mutableMapOf("Authorization" to "Bearer $accessToken")
        val projectId = SettingsManager.getGeminiOAuthProjectId(context.applicationContext)
        if (projectId.isNotBlank()) {
            headers["x-goog-user-project"] = projectId
        }

        val response = withContext(Dispatchers.IO) {
            OAuthHttpClient.postJson(
                url = endpoint,
                payload = payload,
                headers = headers
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

    private fun mapRole(role: String): String {
        return if (role.equals("model", ignoreCase = true) || role.equals("assistant", ignoreCase = true)) {
            "model"
        } else {
            "user"
        }
    }
}
