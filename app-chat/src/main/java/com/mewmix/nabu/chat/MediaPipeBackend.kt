package com.mewmix.nabu.chat

import android.content.Context
import com.mewmix.nabu.utils.DebugLogger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions

class MediaPipeBackend(
    private val context: Context,
    private val modelPath: String
) : LlmBackend {

    private var llmInference: LlmInference? = null

    override fun initialize() {
        DebugLogger.log("MediaPipeBackend initialize with model $modelPath")
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    override fun sendMessage(
        conversation: List<LlmMessage>,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    ) {
        if (conversation.isEmpty()) {
            DebugLogger.log("MediaPipeBackend sendMessage received empty conversation; aborting request")
            return
        }
        if (llmInference == null) {
            initialize()
        }

        val payload = buildConversationPayload(conversation)
        DebugLogger.log("MediaPipeBackend sendMessage with ${conversation.size} turns")
        llmInference?.generateResponseAsync(payload, resultListener)
    }

    override fun sendMessage(prompt: String, resultListener: (partialResult: String, done: Boolean) -> Unit) {
        if (llmInference == null) {
            initialize()
        }

        DebugLogger.log("MediaPipeBackend sendMessage: $prompt")
        llmInference?.generateResponseAsync(prompt, resultListener)
    }

    private fun buildConversationPayload(conversation: List<LlmMessage>): String {
        val sb = StringBuilder()
        conversation.forEach { message ->
            sb.append("<start_of_turn>${message.role}\n${message.content}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    override fun close() {
        llmInference?.close()
    }

}
