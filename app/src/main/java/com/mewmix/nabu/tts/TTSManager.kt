package com.mewmix.nabu.tts

import android.content.Context
import com.mewmix.nabu.data.ModelType
import com.mewmix.nabu.data.UserPreferencesRepository
import com.mewmix.nabu.kokoro.KokoroEngine
import com.mewmix.nabu.supertonic.DebugSupertonicEngine
import com.mewmix.nabu.soprano.SopranoEngine
import com.mewmix.nabu.supertonic.SupertonicStyle
import com.mewmix.nabu.utils.DebugLogger
import com.mewmix.nabu.utils.OnnxRuntimeManager
import kotlinx.coroutines.flow.first
import java.io.File
import com.mewmix.nabu.data.ModelManager
import ai.onnxruntime.OrtEnvironment
import com.mewmix.nabu.utils.SettingsManager
import com.mewmix.nabu.kokoro.RunEp

object TTSManager {
    private var activeEngine: TTSEngine? = null
    private var activeType: ModelType = ModelType.LLM // default to LLM/Kokoro logic
    private var activeRuntimePreference: RunEp? = null
    private var activeSupertonicModelId: String? = null
    private var activeSopranoModelId: String? = null
    // Wait, ModelType.LLM is confusing here. I should use a specific TTS type enum or just check.
    // Let's use string id or a separate enum.

    suspend fun getEngine(context: Context, modelManager: ModelManager): TTSEngine? {
        val preferredEngineRaw = SettingsManager.getTtsEngine(context)
        val preferredEngine = preferredEngineRaw
        val preferredRuntime = SettingsManager.getRuntimePreference(context)
        val preferredSupertonicModel = SettingsManager.getSupertonicModelId(context)

        if (activeEngine != null) {
            // Check if active engine matches preference.
            // This is a bit tricky since we don't store the type on the engine instance easily.
            // For now, if preference changed, we might need to close and reload.
            // But getEngine is usually called per synthesis or session.
            // Let's assume for now if it's initialized we re-use it, unless we force reload.
            // Actually, if the user switches engine, we should probably close the old one.
            // But getEngine doesn't know if preference JUST changed.
            // Let's rely on the caller or just check type if possible.
            val isSupertonic = activeEngine?.name == "Supertonic"

            if (preferredEngine == "supertonic" && !isSupertonic) {
                activeEngine?.close()
                activeEngine = null
                activeRuntimePreference = null
                activeSupertonicModelId = null
            } else if (preferredEngine == "kokoro" && (isSupertonic)) {
                activeEngine?.close()
                activeEngine = null
                activeRuntimePreference = null
                activeSupertonicModelId = null
                activeSopranoModelId = null
            } else if (preferredEngine == "supertonic" &&
                preferredSupertonicModel != null &&
                activeSupertonicModelId != preferredSupertonicModel
            ) {
                activeEngine?.close()
                activeEngine = null
                activeRuntimePreference = null
                activeSupertonicModelId = null
                activeSopranoModelId = null
            } else if (preferredEngine == "kokoro" && activeRuntimePreference != preferredRuntime) {
                activeEngine?.close()
                activeEngine = null
                activeRuntimePreference = null
                activeSopranoModelId = null
            } else if (preferredEngine == "soprano" && activeSopranoModelId != "soprano-80m-onnx") {
                // If switching to Soprano or model changed, reset
                activeEngine?.close()
                activeEngine = null
                activeRuntimePreference = null
                activeSupertonicModelId = null
                activeSopranoModelId = null
            } else {
                return activeEngine
            }
        }

        if (preferredEngine == "soprano") {
            val ttsModels = modelManager.models.filter { it.type == ModelType.TTS && it.isDownloaded }
            val model = ttsModels.firstOrNull { it.id == "soprano-80m-onnx" }
            if (model != null) {
                val modelDir = File(context.filesDir, "models/${model.id}")
                return try {
                    val engine = SopranoEngine(modelDir, OrtEnvironment.getEnvironment())
                    activeEngine = BenchmarkingTTSEngine(engine)
                    activeRuntimePreference = RunEp.CPU
                    activeSupertonicModelId = null
                    activeSopranoModelId = model.id
                    DebugLogger.log("TTSManager: Switched to Soprano (${model.name})")
                    activeEngine
                } catch (e: Exception) {
                    DebugLogger.log("TTSManager: Failed to load Soprano: ${e.message}")
                    null
                }
            } else {
                DebugLogger.log("TTSManager: Soprano selected but model not found or not downloaded.")
                return null
            }
        }

        if (preferredEngine == "supertonic") {
            val ttsModels = modelManager.models.filter { it.type == ModelType.TTS && it.isDownloaded }
            val selectedModel = preferredSupertonicModel?.let { modelId ->
                ttsModels.firstOrNull { it.id == modelId }
            }
            val model = if (preferredSupertonicModel != null) {
                selectedModel
            } else {
                ttsModels.firstOrNull()
            }
            if (model != null) {
                val modelDir = File(context.filesDir, "models/${model.id}")
                try {
                    val engine = DebugSupertonicEngine(modelDir)
                    activeEngine = BenchmarkingTTSEngine(engine)
                    activeRuntimePreference = null
                    activeSupertonicModelId = model.id
                    DebugLogger.log("TTSManager: Switched to Supertonic (${model.name})")
                    return activeEngine
                } catch (e: Exception) {
                    DebugLogger.log("TTSManager: Failed to load Supertonic: ${e.message}")
                    // Fallback to Kokoro? Or just return null?
                    // Let's fall back to Kokoro to be safe.
                }
            } else if (preferredSupertonicModel != null) {
                DebugLogger.log("TTSManager: Supertonic selected model missing: $preferredSupertonicModel")
                return null
            } else {
                DebugLogger.log("TTSManager: Supertonic selected but no model found.")
            }
        }

        // Soprano engine switching is disabled; prefer Kokoro or Supertonic.

        // Fallback or default to Kokoro
        try {
            val bundle = OnnxRuntimeManager.initialize(context).getOrNull()
            if (bundle != null) {
                 activeEngine = BenchmarkingTTSEngine(OnnxRuntimeManager.getEngine())
                 activeRuntimePreference = preferredRuntime
                 DebugLogger.log("TTSManager: Switched to Kokoro")
                 return activeEngine
            }
        } catch (e: Exception) {
             DebugLogger.log("TTSManager: Failed to load Kokoro: ${e.message}")
        }

        return null
    }

    fun close() {
        activeEngine?.close()
        activeEngine = null
    }
}
