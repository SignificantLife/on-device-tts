package com.mewmix.nabu.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mewmix.nabu.data.ModelManager
import com.mewmix.nabu.soprano.SopranoEngine
import com.mewmix.nabu.utils.DebugLogger
import com.mewmix.nabu.utils.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SopranoSelectionInstrumentedTest {

    @After
    fun cleanup() {
        TTSManager.close()
    }

    @Test
    fun sopranoSelectionUsesSopranoEngineAndSynthesizesPhrase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DebugLogger.initialize(context)

        val modelManager = ModelManager(context)
        val sopranoModel = modelManager.models.firstOrNull { it.id == "soprano-80m-onnx" }
        assumeTrue("Soprano model is not downloaded on this device", sopranoModel?.isDownloaded == true)

        SettingsManager.setTtsEngine(context, "soprano")
        TTSManager.close()

        val engine = TTSManager.getEngine(context, modelManager)
        assertNotNull("Expected a non-null TTS engine", engine)

        val rawEngine = if (engine is BenchmarkingTTSEngine) engine.delegate else engine
        assertTrue("Expected SopranoEngine but got ${rawEngine?.name}", rawEngine is SopranoEngine)

        val phrase = "do not be alarmed i am simply testing the update for alex"
        val result = engine!!.synthesize(phrase, 1.0f)
        DebugLogger.log("SopranoSelectionInstrumentedTest: synthesized phrase '$phrase' samples=${result.wav.size}")

        assertTrue("Expected synthesized audio to have samples", result.wav.isNotEmpty())
        assertEquals("Expected Soprano sample rate", 32000, result.sampleRate)
    }
}
