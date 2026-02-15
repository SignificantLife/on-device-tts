package com.mewmix.nabu.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mewmix.nabu.utils.SettingsManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class ApiServerInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ApiServerManager.stop()
        SettingsManager.setApiEnabled(context, true)
        SettingsManager.setApiLanEnabled(context, true)
        ApiServerManager.syncWithSettings(context)
    }

    @After
    fun tearDown() {
        ApiServerManager.stop()
        SettingsManager.setApiEnabled(context, false)
        SettingsManager.setApiLanEnabled(context, false)
    }

    @Test
    fun healthAndModelsEndpointsRespond() {
        val health = rawHttpGet("/health")
        assertEquals(200, health.first)
        val healthJson = JSONObject(health.second)
        assertTrue(healthJson.optBoolean("ok"))

        val models = rawHttpGet("/v1/models")
        assertEquals(200, models.first)
        val modelsJson = JSONObject(models.second)
        assertEquals("list", modelsJson.optString("object"))
        assertTrue(modelsJson.has("data"))
    }

    private fun rawHttpGet(path: String): Pair<Int, String> {
        Socket("127.0.0.1", ApiServerManager.PORT).use { socket ->
            socket.soTimeout = 5_000
            val request = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val statusLine = reader.readLine() ?: error("Missing HTTP status line")
            val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
                ?: error("Unable to parse HTTP status from: $statusLine")

            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            val body = buildString {
                while (true) {
                    val bodyLine = reader.readLine() ?: break
                    append(bodyLine)
                }
            }
            return statusCode to body
        }
    }
}
