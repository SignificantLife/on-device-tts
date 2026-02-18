package com.mewmix.nabu.auth

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal object OAuthSessionStore {
    private const val PREFS_NAME = "oauth_session_store"
    private const val SESSION_TTL_MS = 10 * 60 * 1000L
    private val secureRandom = SecureRandom()

    data class Session(
        val state: String,
        val codeVerifier: String
    )

    fun createSession(context: Context, providerId: String): Session {
        val session = Session(
            state = randomUrlSafe(24),
            codeVerifier = randomUrlSafe(64)
        )
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(stateKey(providerId), session.state)
            .putString(verifierKey(providerId), session.codeVerifier)
            .putLong(createdAtKey(providerId), System.currentTimeMillis())
            .apply()
        return session
    }

    fun consumeIfValid(context: Context, providerId: String, state: String?): Session? {
        if (state.isNullOrBlank()) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedState = prefs.getString(stateKey(providerId), null)
        val storedVerifier = prefs.getString(verifierKey(providerId), null)
        val createdAt = prefs.getLong(createdAtKey(providerId), 0L)

        clearSession(context, providerId)

        if (storedState.isNullOrBlank() || storedVerifier.isNullOrBlank()) return null
        if (storedState != state) return null
        if (createdAt <= 0L || (System.currentTimeMillis() - createdAt) > SESSION_TTL_MS) return null

        return Session(state = storedState, codeVerifier = storedVerifier)
    }

    fun clearSession(context: Context, providerId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(stateKey(providerId))
            .remove(verifierKey(providerId))
            .remove(createdAtKey(providerId))
            .apply()
    }

    fun buildCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        secureRandom.nextBytes(buffer)
        return Base64.encodeToString(buffer, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun stateKey(providerId: String): String = "state_$providerId"
    private fun verifierKey(providerId: String): String = "verifier_$providerId"
    private fun createdAtKey(providerId: String): String = "created_$providerId"
}
