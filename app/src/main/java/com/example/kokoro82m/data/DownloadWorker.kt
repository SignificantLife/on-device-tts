package com.example.kokoro82m.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class DownloadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString("model_id")
        val downloadUrl = inputData.getString("download_url")
        val hfToken = inputData.getString("hf_token")

        if (modelId.isNullOrEmpty() || downloadUrl.isNullOrEmpty()) {
            return Result.failure()
        }

        return try {
            val modelDir = File(applicationContext.filesDir, "models")
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            val destinationFile = File(modelDir, "$modelId.task")

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpsURLConnection
            hfToken?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            connection.connect()

            val inputStream = connection.getInputStream()
            val outputStream = FileOutputStream(destinationFile)

            val buffer = ByteArray(1024)
            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                outputStream.write(buffer, 0, len)
            }

            outputStream.close()
            inputStream.close()

            Log.d("DownloadWorker", "Model downloaded to ${destinationFile.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error downloading model", e)
            Result.failure()
        }
    }
}
