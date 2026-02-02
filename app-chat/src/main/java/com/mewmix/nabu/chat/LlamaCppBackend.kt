package com.mewmix.nabu.chat

import android.content.Context
import com.mewmix.nabu.utils.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LlamaCppBackend(
    private val context: Context,
    private val modelPath: String
) : LlmBackend {

    override fun initialize() {
        DebugLogger.log("LlamaCppBackend initialize with model $modelPath (Stub)")
    }

    override fun sendMessage(
        conversation: List<LlmMessage>,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    ) {
        DebugLogger.log("LlamaCppBackend sendMessage (Stub)")
        // Simulate streaming response
        CoroutineScope(Dispatchers.IO).launch {
            val response = "This is a stub response from Llama.cpp backend. Real inference coming soon!"
            val chunks = response.split(" ")
            chunks.forEachIndexed { index, chunk ->
                delay(100)
                resultListener("$chunk ", false)
            }
            resultListener("", true)
        }
    }

    override fun sendMessage(
        prompt: String,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    ) {
        DebugLogger.log("LlamaCppBackend sendMessage prompt (Stub)")
        CoroutineScope(Dispatchers.IO).launch {
             val response = "This is a stub response from Llama.cpp backend (prompt mode)."
             delay(500)
             resultListener(response, true)
        }
    }

    override fun close() {
        DebugLogger.log("LlamaCppBackend close")
    }
}
