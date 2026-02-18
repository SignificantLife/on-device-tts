package com.mewmix.nabu.auth

import android.util.Base64
import org.json.JSONObject

internal object JwtUtils {
    fun decodePayload(jwt: String): JSONObject? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        val decoded = runCatching {
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }.getOrNull() ?: return null
        return runCatching { JSONObject(String(decoded, Charsets.UTF_8)) }.getOrNull()
    }

    fun codexAccountId(accessToken: String, idToken: String?): String? {
        val fromId = idToken
            ?.let(::decodePayload)
            ?.optJSONObject("https://api.openai.com/auth")
            ?.optString("chatgpt_account_id")
            ?.ifBlank { null }
        if (!fromId.isNullOrBlank()) return fromId
        return decodePayload(accessToken)
            ?.optJSONObject("https://api.openai.com/auth")
            ?.optString("chatgpt_account_id")
            ?.ifBlank { null }
    }
}
