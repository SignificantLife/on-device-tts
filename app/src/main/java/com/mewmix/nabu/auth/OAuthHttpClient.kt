package com.mewmix.nabu.auth

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object OAuthHttpClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun postForm(url: String, fields: Map<String, String>): Result<JSONObject> = runCatching {
        val bodyBuilder = FormBody.Builder()
        fields.forEach { (key, value) ->
            bodyBuilder.add(key, value)
        }
        val request = Request.Builder()
            .url(url)
            .post(bodyBuilder.build())
            .header("Accept", "application/json")
            .build()
        executeJson(request)
    }

    fun postJson(
        url: String,
        payload: JSONObject,
        headers: Map<String, String> = emptyMap()
    ): Result<JSONObject> = runCatching {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        val request = requestBuilder.build()
        executeJson(request)
    }

    fun getJson(url: String, headers: Map<String, String>): Result<JSONObject> = runCatching {
        val requestBuilder = Request.Builder().url(url).get()
        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        executeJson(requestBuilder.build())
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $text")
            }
            if (text.isBlank()) {
                return JSONObject()
            }
            return JSONObject(text)
        }
    }
}
