package com.mewmix.nabu.data

import android.content.Context
import com.mewmix.nabu.auth.CodexAuthenticator
import com.mewmix.nabu.auth.GeminiAuthenticator

object OAuthRemoteModels {
    const val CODEX_MODEL_ID = "codex-byos-oauth"
    const val GEMINI_MODEL_ID = "gemini-byos-oauth"

    fun connectedModels(context: Context): List<Model> {
        val appContext = context.applicationContext
        val models = mutableListOf<Model>()

        if (CodexAuthenticator().hasStoredSession(appContext)) {
            models += Model(
                id = CODEX_MODEL_ID,
                name = "Codex (BYOS OAuth)",
                description = "OpenAI Codex via OAuth session",
                repo = "",
                downloadUrl = "",
                gated = false,
                type = ModelType.LLM,
                initialIsDownloaded = true,
                initialBackend = "codex_oauth"
            )
        }

        if (GeminiAuthenticator().hasStoredSession(appContext)) {
            models += Model(
                id = GEMINI_MODEL_ID,
                name = "Gemini (BYOS OAuth)",
                description = "Google Gemini via OAuth session",
                repo = "",
                downloadUrl = "",
                gated = false,
                type = ModelType.LLM,
                initialIsDownloaded = true,
                initialBackend = "gemini_oauth"
            )
        }

        return models
    }
}
