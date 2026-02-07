package com.mewmix.nabu.chat

interface LlmBackend {
    fun initialize()

    fun sendMessage(
        conversation: List<LlmMessage>,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    )

    fun sendMessage(
        prompt: String,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    )

    fun cancel()

    fun close()
}
