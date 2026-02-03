package com.mewmix.nabu.chat

data class LlmRuntimeConfig(
    val nCtx: Int,
    val nBatch: Int,
    val nThreads: Int,
    val nThreadsBatch: Int,
    val maxNewTokens: Int,
    val ttftTimeoutMs: Long,
    val totalTimeoutMs: Long
)

data class LlmRuntimeOverrides(
    val nCtx: Int? = null,
    val nBatch: Int? = null,
    val nThreads: Int? = null,
    val nThreadsBatch: Int? = null,
    val maxNewTokens: Int? = null,
    val ttftTimeoutMs: Long? = null,
    val totalTimeoutMs: Long? = null,
    val threadsAuto: Boolean? = null
)
