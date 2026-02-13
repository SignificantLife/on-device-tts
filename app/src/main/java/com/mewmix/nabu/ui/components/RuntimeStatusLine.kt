package com.mewmix.nabu.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mewmix.nabu.data.ModelManager
import com.mewmix.nabu.data.ModelType
import com.mewmix.nabu.ui.brutalist.Brutal
import com.mewmix.nabu.utils.OnnxRuntimeManager
import com.mewmix.nabu.utils.SettingsManager

@Composable
fun RuntimeStatusLine(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsEngine = SettingsManager.getTtsEngine(context)
    val runtimeStatus = OnnxRuntimeManager.runtimeStatus()
    val modelManager = remember { ModelManager(context) }
    val supertonicModelId = SettingsManager.getSupertonicModelId(context)
    val supertonicModelName = supertonicModelId?.let { id ->
        modelManager.models.firstOrNull { it.type == ModelType.TTS && it.id == id }?.name
    }
    val supertonicModelLabel = supertonicModelName ?: supertonicModelId

    val runtimeLabel = if (ttsEngine == "supertonic") {
        buildString {
            append("SUPERTONIC / CPU")
            supertonicModelLabel?.let { append(" / $it") }
        }
    } else if (ttsEngine == "soprano") {
        "SOPRANO / CPU / soprano-80m-onnx"
    } else {
        if (runtimeStatus == null) {
            "KOKORO / LOADING..."
        } else {
            "KOKORO / ${runtimeStatus.ep.name} / ${runtimeStatus.graphId}"
        }
    }

    Text(
        text = "RUNTIME: $runtimeLabel",
        style = MaterialTheme.typography.labelLarge,
        color = Brutal.textDim,
        modifier = modifier
    )
}
