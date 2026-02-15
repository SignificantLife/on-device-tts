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
import java.net.ServerSocket
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class ApiServerManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val freePort = ServerSocket(0).use { it.localPort }
        ApiServerManager.setPortOverrideForTesting(freePort)
        SettingsManager.setApiEnabled(context, false)
        SettingsManager.setApiLanEnabled(context, false)
        ApiServerManager.stop()
    }

    @After
    fun tearDown() {
        ApiServerManager.stop()
        ApiServerManager.setPortOverrideForTesting(null)
        SettingsManager.setApiEnabled(context, false)
        SettingsManager.setApiLanEnabled(context, false)
    }

    @Test
    fun syncWithSettings_startsAndStopsServer() {
        SettingsManager.setApiEnabled(context, true)
        ApiServerManager.syncWithSettings(context)

        assertTrue(ApiServerManager.isRunning())

        val conn = URL("http://${ApiServerManager.LOCAL_HOST}:${ApiServerManager.currentPort()}/health")
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

    @Test
    fun syncWithSettings_rebindsWhenLanModeChanges() {
        SettingsManager.setApiEnabled(context, true)
        SettingsManager.setApiLanEnabled(context, false)
        ApiServerManager.syncWithSettings(context)
        assertTrue(ApiServerManager.isRunning())
        assertTrue(ApiServerManager.currentHost() == ApiServerManager.LOCAL_HOST)

        SettingsManager.setApiLanEnabled(context, true)
        ApiServerManager.syncWithSettings(context)

        assertTrue(ApiServerManager.isRunning())
        assertTrue(ApiServerManager.currentHost() == ApiServerManager.LAN_HOST)

        val conn = URL("http://${ApiServerManager.LOCAL_HOST}:${ApiServerManager.currentPort()}/health")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2_000
        conn.readTimeout = 2_000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        assertTrue(json.optBoolean("ok"))
    }
}
