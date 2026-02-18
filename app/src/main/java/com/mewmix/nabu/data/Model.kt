package com.mewmix.nabu.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ModelType {
    LLM,
    TTS
}

class Model(
    val id: String,
    val name: String,
    val description: String,
    val repo: String,
    val downloadUrl: String,
    val gated: Boolean,
    val type: ModelType = ModelType.LLM,
    initialIsDownloaded: Boolean = false,
    initialHasPartial: Boolean = false,
    initialBackend: String = "mediapipe"
) {
    var isDownloaded by mutableStateOf(initialIsDownloaded)
    var hasPartial by mutableStateOf(initialHasPartial)
    var backend by mutableStateOf(initialBackend)
}
