package com.example.kokoro82m.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ModelDownloader(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    fun downloadModel(model: Model) {
        scope.launch {
            val token = userPreferencesRepository.hfToken.first()
            try {
                val url = URL(model.downloadUrl)
                val connection = url.openConnection() as HttpsURLConnection
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                connection.connect()

                val total = connection.contentLength
                val input = connection.inputStream

                val modelDir = File(context.filesDir, "models")
                if (!modelDir.exists()) modelDir.mkdirs()
                val destFile = File(modelDir, "${model.id}.task")
                val output = FileOutputStream(destFile)

                val buffer = ByteArray(1024)
                var bytesRead: Int
                var downloaded = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (total > 0) {
                        val p = downloaded.toFloat() / total
                        _progress.value = _progress.value.toMutableMap().apply { put(model.id, p) }
                    }
                }

                output.close()
                input.close()
                model.isDownloaded = true
            } catch (_: Exception) {
            } finally {
                _progress.value = _progress.value.toMutableMap().apply { remove(model.id) }
            }
        }
    }
}
