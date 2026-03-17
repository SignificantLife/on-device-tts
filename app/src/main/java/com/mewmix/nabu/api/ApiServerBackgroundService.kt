package com.mewmix.nabu.api

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mewmix.nabu.EXTRA_START_SCREEN
import com.mewmix.nabu.MainActivity
import com.mewmix.nabu.R
import com.mewmix.nabu.utils.DebugLogger
import com.mewmix.nabu.utils.SettingsManager

class ApiServerBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enabled = SettingsManager.isApiEnabled(applicationContext)
        val keepInBackground = SettingsManager.isApiBackgroundEnabled(applicationContext)
        if (!enabled || !keepInBackground) {
            releaseLocks()
            ApiServerManager.syncWithSettings(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            acquireLocks()
            ApiServerManager.syncWithSettings(applicationContext)
        } catch (t: Throwable) {
            DebugLogger.logErr("Failed to start API background service", t)
            ApiServerManager.stop()
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        val shouldKeepServer = SettingsManager.isApiEnabled(applicationContext) &&
            !SettingsManager.isApiBackgroundEnabled(applicationContext)
        if (!shouldKeepServer) {
            ApiServerManager.stop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openSettingsIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_START_SCREEN, "Settings")
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val host = ApiServerManager.configuredHost(applicationContext)
        val port = ApiServerManager.currentPort()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings_24)
            .setContentTitle("Nabu API server is active")
            .setContentText("Listening on http://$host:$port")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "API Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock?.isHeld != true) {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFILOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.release() }
        wakeLock = null
        runCatching { wifiLock?.release() }
        wifiLock = null
    }

    companion object {
        private const val CHANNEL_ID = "api_background_channel"
        private const val NOTIFICATION_ID = 1042
        private const val WAKELOCK_TAG = "com.mewmix.nabu:api_background_wakelock"
        private const val WIFILOCK_TAG = "com.mewmix.nabu:api_background_wifilock"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ApiServerBackgroundService::class.java)
            runCatching { ContextCompat.startForegroundService(appContext, intent) }
                .onFailure { t ->
                    DebugLogger.logErr("Unable to start API background service", t)
                    ApiServerManager.syncWithSettings(appContext)
                }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, ApiServerBackgroundService::class.java))
        }
    }
}
