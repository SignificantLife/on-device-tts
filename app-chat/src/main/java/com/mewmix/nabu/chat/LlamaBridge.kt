package com.mewmix.nabu.chat

internal object LlamaBridge {
    val isAvailable: Boolean = try {
        System.loadLibrary("llama_jni")
        true
    } catch (t: Throwable) {
        false
    }

    interface TokenCallback {
        fun onToken(chunk: String)
        fun onComplete()
        fun onError(message: String)
    }

    @JvmStatic external fun init(
        modelPath: String,
        nCtx: Int,
        nBatch: Int,
        nThreads: Int,
        nThreadsBatch: Int
    ): Long

    @JvmStatic external fun setThreads(
        handle: Long,
        nThreads: Int,
        nThreadsBatch: Int
    )

    @JvmStatic external fun close(handle: Long)
    @JvmStatic external fun cancel(handle: Long)

    @JvmStatic external fun generate(
        handle: Long,
        prompt: String,
        maxNewTokens: Int,
        ttftTimeoutMs: Long,
        totalTimeoutMs: Long,
        callback: TokenCallback
    ): Boolean
}
