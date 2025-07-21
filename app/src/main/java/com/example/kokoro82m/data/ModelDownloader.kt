package com.example.kokoro82m.data

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ModelDownloader(private val context: Context) {

    fun downloadModel(model: Model, token: String? = null) {
        val dataBuilder = Data.Builder()
            .putString("model_id", model.id)
            .putString("download_url", model.downloadUrl)

        token?.let {
            dataBuilder.putString("hf_token", it)
        }

        val downloadWorkRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(dataBuilder.build())
            .build()

        WorkManager.getInstance(context).enqueue(downloadWorkRequest)
    }
}
