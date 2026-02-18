package com.mewmix.nabu.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CodexApiClient(
    private val authenticator: CodexAuthenticator = CodexAuthenticator()
) {
    suspend fun sendPrompt(
        context: Context,
        prompt: String,
        model: String = "gpt-5.3-codex"
    ): Result<String> {
        val accessToken = authenticator.getValidAccessToken(context)
            ?: return Result.failure(IllegalStateException("Codex account is not authenticated."))
        val accountId = authenticator.getAccountId(context)

        val payload = JSONObject()
            .put("model", model)
            .put("store", false)
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "input_text")
                                    .put("text", prompt)
                            )
                        )
                )
            )

        val headers = mutableMapOf(
            "Authorization" to "Bearer $accessToken",
            "User-Agent" to "Nabu-Android/1.0"
        )
        if (!accountId.isNullOrBlank()) {
            headers["ChatGPT-Account-Id"] = accountId
        }

        val response = withContext(Dispatchers.IO) {
            OAuthHttpClient.postJson(
                url = "https://chatgpt.com/backend-api/codex/responses",
                payload = payload,
                headers = headers
            )
        }

        return response.map { json ->
            extractOutputText(json) ?: throw IllegalStateException("Codex returned no text output: $json")
        }
    }

    suspend fun fetchUsageSummary(context: Context): Result<String> {
        val accessToken = authenticator.getValidAccessToken(context)
            ?: return Result.failure(IllegalStateException("Codex account is not authenticated."))
        val accountId = authenticator.getAccountId(context)
        val headers = mutableMapOf(
            "Authorization" to "Bearer $accessToken",
            "User-Agent" to "Nabu-Android/1.0",
            "Accept" to "application/json"
        )
        if (!accountId.isNullOrBlank()) {
            headers["ChatGPT-Account-Id"] = accountId
        }

        val response = withContext(Dispatchers.IO) {
            OAuthHttpClient.getJson(
                url = "https://chatgpt.com/backend-api/wham/usage",
                headers = headers
            )
        }

        return response.map { json ->
            val plan = json.optString("plan_type").ifBlank { "unknown plan" }
            val primary = json.optJSONObject("rate_limit")
                ?.optJSONObject("primary_window")
                ?.optDouble("used_percent", Double.NaN)
            if (primary != null && !primary.isNaN()) {
                "$plan (${primary.toInt()}% primary window used)"
            } else {
                plan
            }
        }
    }

    private fun extractOutputText(json: JSONObject): String? {
        json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }

        val output = json.optJSONArray("output") ?: return null
        val chunks = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val contentItem = content.optJSONObject(j) ?: continue
                val text = contentItem.optString("text").ifBlank { null } ?: continue
                chunks += text
            }
        }
        return chunks.joinToString("").trim().ifBlank { null }
    }
}
