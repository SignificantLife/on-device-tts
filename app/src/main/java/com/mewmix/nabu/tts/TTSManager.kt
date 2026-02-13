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
    private var activeRuntimePreference: RunEp? = null
    private var activeSupertonicModelId: String? = null
    private var activeSopranoModelId: String? = null
    // Wait, ModelType.LLM is confusing here. I should use a specific TTS type enum or just check.
    // Let's use string id or a separate enum.

    suspend fun getEngine(context: Context, modelManager: ModelManager): TTSEngine? {
        DebugLogger.log("TTSManager.getEngine: enter")
        val preferredEngineRaw = SettingsManager.getTtsEngine(context)
        val preferredEngine = preferredEngineRaw
        val preferredRuntime = SettingsManager.getRuntimePreference(context)
        val preferredSupertonicModel = SettingsManager.getSupertonicModelId(context)

        DebugLogger.log("Prefs engine=%s runtime=%s supertonicModel=%s active=%s".format(preferredEngine, preferredRuntime, preferredSupertonicModel, activeEngine?.name))

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
                DebugLogger.log("Reusing active engine: %s".format(activeEngine?.name)); return activeEngine
            }
        }

        if (preferredEngine == "soprano") {
            DebugLogger.log("TTSManager: Preference=Soprano. Verifying local model files...")
            val modelId = "soprano-80m-onnx"
            val modelDir = File(context.filesDir, "models/$modelId")
            val required = listOf(
                "soprano_backbone_kv.onnx",
                "soprano_decoder.onnx",
                "soprano_decoder.onnx.data",
                "tokenizer.json"
            )
            val missing = required.filter { !File(modelDir, it).exists() }

            if (missing.isEmpty()) {
                try {
                    DebugLogger.log("TTSManager: Loading Soprano from ${modelDir.absolutePath}")
                    val engine = SopranoEngine(modelDir, OrtEnvironment.getEnvironment())
                    activeEngine = BenchmarkingTTSEngine(engine)
                    activeRuntimePreference = RunEp.CPU
                    activeSupertonicModelId = null
                    activeSopranoModelId = modelId
                    DebugLogger.log("TTSManager: Switched to Soprano ($modelId)")
                    activeEngine
                    return activeEngine
                } catch (e: Exception) {
                    DebugLogger.logErr("TTSManager: Failed to load Soprano from %s".format(modelDir.absolutePath), e)
                    return null
                }
            } else {
                DebugLogger.log("TTSManager: Soprano selected but missing files: ${missing.joinToString()}")
                return null
            }
        }

        if (preferredEngine == "supertonic") {
            // Only consider Supertonic models, not Soprano
            val ttsModels = modelManager.models.filter { it.type == ModelType.TTS && it.isDownloaded && it.id.startsWith("supertonic") }
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
                    DebugLogger.logErr("TTSManager: Failed to load Supertonic", e)
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

        // Fallback or default to Kokoro (but not when user explicitly selected Soprano)
        if (preferredEngine == "soprano") {
            DebugLogger.log("TTSManager: Soprano selected; not falling back to Kokoro")
            return null
        }

        try {
            DebugLogger.log("Kokoro fallback: initializing runtime")
            val bundle = OnnxRuntimeManager.initialize(context).getOrNull()
            if (bundle != null) {
                 activeEngine = BenchmarkingTTSEngine(OnnxRuntimeManager.getEngine())
                 activeRuntimePreference = preferredRuntime
                 DebugLogger.log("TTSManager: Switched to Kokoro (fallback or default)")
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
