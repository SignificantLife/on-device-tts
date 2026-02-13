package com.mewmix.nabu.data

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
import com.mewmix.nabu.utils.DebugLogger

class ModelDownloader(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress

    fun downloadModel(model: Model) {
        scope.launch {
            if (model.type == ModelType.TTS) {
                downloadTTSModel(model)
            } else {
                downloadLLMModel(model)
            }
        }
    }

    private suspend fun downloadTTSModel(model: Model) {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()

        val targetDir = File(modelDir, model.id)
        val tempDir = File(modelDir, "${model.id}_partial")
        // We'll decide destination dir after computing required files, so we can heal missing files

        val filesToDownload: List<String>
        val baseUrl: String

        if (model.id == "soprano-80m-onnx") {
            baseUrl = model.downloadUrl
            // Soprano decoder uses external data file; download both .onnx and .onnx.data
            filesToDownload = listOf(
                "onnx/soprano_backbone_kv.onnx",
                "onnx/soprano_decoder.onnx",
                "onnx/soprano_decoder.onnx.data",
                "tokenizer.json"
            )
        } else {
            val onnxFiles = listOf(
                "duration_predictor.onnx",
                "text_encoder.onnx",
                "vector_estimator.onnx",
                "vocoder.onnx",
                "tts.json",
                "unicode_indexer.json"
            )

            // Add voice styles
            // The base URL for onnx is: https://huggingface.co/Supertone/supertonic/resolve/main/onnx/
            // The voices are in: https://huggingface.co/Supertone/supertonic/resolve/main/voice_styles/
            // We need to handle this URL difference.
            // I will hardcode the logic for now based on the file list, assuming model.downloadUrl points to the onnx folder.
            // A cleaner way would be to put the root url in the model and append paths.
            // Current downloadUrl: https://huggingface.co/Supertone/supertonic/resolve/main/onnx/
            // Root: https://huggingface.co/Supertone/supertonic/resolve/main/

            baseUrl = model.downloadUrl.removeSuffix("onnx/")
            val voiceStyles = when (model.id) {
                "supertonic-2-onnx" -> listOf(
                    "F1.json", "F2.json", "F3.json", "F4.json", "F5.json",
                    "M1.json", "M2.json", "M3.json", "M4.json", "M5.json"
                )
                else -> listOf("F1.json", "F2.json", "M1.json", "M2.json")
            }

            filesToDownload = onnxFiles.map { "onnx/$it" } + voiceStyles.map { "voice_styles/$it" }
        }

        // Determine destination: if target exists and all required files are present, we're done.
        // If target exists but required files are missing (e.g., new files added in update), download only missing into target.
        if (targetDir.exists()) {
            if (model.id == "soprano-80m-onnx") {
                val requiredLocal = listOf(
                    "soprano_backbone_kv.onnx",
                    "soprano_decoder.onnx",
                    "soprano_decoder.onnx.data",
                    "tokenizer.json"
                )
                val missing = requiredLocal.filter { !File(targetDir, it).exists() }
                if (missing.isEmpty()) {
                    model.isDownloaded = true
                    model.hasPartial = false
                    DebugLogger.log("ModelDownloader: ${model.name} already downloaded")
                    return
                } else {
                    DebugLogger.log("ModelDownloader: Healing ${model.name}; missing files: ${missing.joinToString()}")
                    model.hasPartial = true
                    // Redefine filesToDownload to only missing
                    val mapLocalToRemote = mapOf(
                        "soprano_backbone_kv.onnx" to "onnx/soprano_backbone_kv.onnx",
                        "soprano_decoder.onnx" to "onnx/soprano_decoder.onnx",
                        "soprano_decoder.onnx.data" to "onnx/soprano_decoder.onnx.data",
                        "tokenizer.json" to "tokenizer.json"
                    )
                    // Download only missing directly into targetDir
                    try {
                        val totalFiles = missing.size
                        missing.forEachIndexed { index, localName ->
                            val relativePath = mapLocalToRemote[localName] ?: localName
                            val fileUrl = "${baseUrl}${relativePath}?download=true"
                            val destFile = File(targetDir, localName)
                            DebugLogger.log("Downloading $localName...")
                            val url = URL(fileUrl)
                            val connection = url.openConnection() as HttpsURLConnection
                            val token = userPreferencesRepository.hfToken.first()
                            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                            connection.connect()
                            connection.inputStream.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    val buffer = ByteArray(4096)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                            val progress = (index + 1).toFloat() / totalFiles
                            _progress.value = _progress.value.toMutableMap().apply { put(model.id, progress) }
                        }
                        model.isDownloaded = true
                        model.hasPartial = false
                        DebugLogger.log("ModelDownloader: Healing of ${model.name} completed")
                        return
                    } catch (e: Exception) {
                        DebugLogger.log("ModelDownloader: Error healing ${model.name}: ${e.message}")
                        // If healing fails, fall through to normal download into tempDir as a fallback
                    } finally {
                        _progress.value = _progress.value.toMutableMap().apply { remove(model.id) }
                    }
                }
            } else {
                model.isDownloaded = true
                model.hasPartial = false
                DebugLogger.log("ModelDownloader: ${model.name} already downloaded")
                return
            }
        }

        if (!tempDir.exists()) tempDir.mkdirs()
        model.hasPartial = true

        DebugLogger.log("ModelDownloader: Starting download of ${model.name} (TTS)")

        try {
            val totalFiles = filesToDownload.size

            filesToDownload.forEachIndexed { index, relativePath ->
                val fileUrl = "${baseUrl}${relativePath}?download=true"
                val fileName = relativePath.substringAfterLast("/")
                val subDir = if (relativePath.startsWith("voice_styles")) "voice_styles" else "."

                val destDir = if (subDir == ".") tempDir else File(tempDir, subDir)
                if (!destDir.exists()) destDir.mkdirs()

                val destFile = File(destDir, fileName)

                if (destFile.exists()) {
                     // Check if complete? Hard to say without checksum. For now assume if it exists we might re-download or skip.
                     // Let's overwrite to be safe, or resume if we implemented resume logic.
                     // Simple implementation: overwrite.
                }

                DebugLogger.log("Downloading $fileName...")

                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpsURLConnection
                // TTS models might not need auth if public, but pass it if we have it and it's from HF?
                // Supertonic is public, but let's check user pref.
                val token = userPreferencesRepository.hfToken.first()
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

                connection.connect()
                val input = connection.inputStream
                val output = FileOutputStream(destFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.close()
                input.close()

                val progress = (index + 1).toFloat() / totalFiles
                _progress.value = _progress.value.toMutableMap().apply { put(model.id, progress) }
            }

            // Move tempDir to targetDir
            tempDir.renameTo(targetDir)
            model.isDownloaded = true
            model.hasPartial = false
            DebugLogger.log("ModelDownloader: Download of ${model.name} completed")

        } catch (e: Exception) {
            DebugLogger.log("ModelDownloader: Error downloading ${model.name}: ${e.message}")
            model.hasPartial = tempDir.exists()
        } finally {
            _progress.value = _progress.value.toMutableMap().apply { remove(model.id) }
        }
    }

    private suspend fun downloadLLMModel(model: Model) {
            val token = userPreferencesRepository.hfToken.first()
            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()
            val finalFile = File(modelDir, "${model.id}.task")
            val tempFile = File(modelDir, "${model.id}.task.part")

            if (finalFile.exists()) {
                model.isDownloaded = true
                model.hasPartial = false
                DebugLogger.log("ModelDownloader: ${model.name} already downloaded")
                return
            }

            val existingSize = if (tempFile.exists()) tempFile.length() else 0L
            model.hasPartial = existingSize > 0

            try {
                DebugLogger.log("ModelDownloader: Starting download of ${model.name}")
                val url = URL(model.downloadUrl)
                val connection = url.openConnection() as HttpsURLConnection
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (existingSize > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingSize-")
                }
                connection.connect()

                val contentLength = connection.getHeaderFieldInt("Content-Length", -1)
                val total = if (contentLength > 0) contentLength + existingSize else -1
                val input = connection.inputStream

                val output = FileOutputStream(tempFile, existingSize > 0)

                val buffer = ByteArray(1024)
                var bytesRead: Int
                var downloaded = existingSize
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

                tempFile.renameTo(finalFile)
                model.isDownloaded = true
                model.hasPartial = false
                DebugLogger.log("ModelDownloader: Download of ${model.name} completed")
            } catch (e: Exception) {
                model.hasPartial = tempFile.exists()
                DebugLogger.log("ModelDownloader: Error downloading ${model.name}: ${e.message}")
            } finally {
                _progress.value = _progress.value.toMutableMap().apply { remove(model.id) }
            }
    }
}
