package com.mewmix.nabu.api

import android.content.Context
import com.mewmix.nabu.utils.SettingsManager

object ApiServerRuntime {
    fun syncWithSettings(context: Context) {
        val appContext = context.applicationContext
        val apiEnabled = SettingsManager.isApiEnabled(appContext)
        val keepInBackground = SettingsManager.isApiBackgroundEnabled(appContext)

        if (!apiEnabled) {
            ApiServerBackgroundService.stop(appContext)
            ApiServerManager.stop()
            return
        }

        if (keepInBackground) {
            ApiServerBackgroundService.start(appContext)
            return
        }

        ApiServerBackgroundService.stop(appContext)
        ApiServerManager.syncWithSettings(appContext)
    }
}
