package com.mewmix.nabu.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mewmix.nabu.utils.SettingsManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class ApiServerManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SettingsManager.setApiEnabled(context, false)
        ApiServerManager.stop()
    }

    @After
    fun tearDown() {
        ApiServerManager.stop()
        SettingsManager.setApiEnabled(context, false)
    }

    @Test
    fun syncWithSettings_startsAndStopsServer() {
        SettingsManager.setApiEnabled(context, true)
        ApiServerManager.syncWithSettings(context)

        assertTrue(ApiServerManager.isRunning())

        val conn = URL("http://${ApiServerManager.HOST}:${ApiServerManager.PORT}/health")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2_000
        conn.readTimeout = 2_000

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        assertTrue(json.optBoolean("ok"))

        SettingsManager.setApiEnabled(context, false)
        ApiServerManager.syncWithSettings(context)
        assertFalse(ApiServerManager.isRunning())
    }
}

