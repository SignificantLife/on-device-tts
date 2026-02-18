package com.mewmix.nabu.auth

import android.content.Context

internal object OAuthTokenStore {
    private const val PREFS_NAME = "oauth_token_store"
    private const val EXPIRY_SKEW_MS = 60_000L

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAtMs: Long?,
        val accountId: String?
    ) {
        fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
            val expiry = expiresAtMs ?: return false
            return nowMs >= (expiry - EXPIRY_SKEW_MS)
        }
    }

    fun save(
        context: Context,
        providerId: String,
        accessToken: String,
        refreshToken: String?,
        idToken: String?,
        expiresInSeconds: Long?,
        accountId: String?
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = expiresInSeconds?.let { seconds ->
            if (seconds > 0L) now + (seconds * 1000L) else null
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(accessKey(providerId), accessToken)
            .putString(refreshKey(providerId), refreshToken)
            .putString(idKey(providerId), idToken)
            .putString(accountKey(providerId), accountId)
            .putLong(expiresAtKey(providerId), expiresAt ?: -1L)
            .putLong(updatedAtKey(providerId), now)
            .apply()
    }

    fun load(context: Context, providerId: String): Tokens? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(accessKey(providerId), null)?.trim().orEmpty()
        if (accessToken.isBlank()) return null

        val expiresRaw = prefs.getLong(expiresAtKey(providerId), -1L)
        val expiresAt = if (expiresRaw > 0L) expiresRaw else null
        return Tokens(
            accessToken = accessToken,
            refreshToken = prefs.getString(refreshKey(providerId), null)?.ifBlank { null },
            idToken = prefs.getString(idKey(providerId), null)?.ifBlank { null },
            expiresAtMs = expiresAt,
            accountId = prefs.getString(accountKey(providerId), null)?.ifBlank { null }
        )
    }

    fun clear(context: Context, providerId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(accessKey(providerId))
            .remove(refreshKey(providerId))
            .remove(idKey(providerId))
            .remove(accountKey(providerId))
            .remove(expiresAtKey(providerId))
            .remove(updatedAtKey(providerId))
            .apply()
    }

    private fun accessKey(providerId: String): String = "access_$providerId"
    private fun refreshKey(providerId: String): String = "refresh_$providerId"
    private fun idKey(providerId: String): String = "id_$providerId"
    private fun accountKey(providerId: String): String = "account_$providerId"
    private fun expiresAtKey(providerId: String): String = "expires_$providerId"
    private fun updatedAtKey(providerId: String): String = "updated_$providerId"
}
