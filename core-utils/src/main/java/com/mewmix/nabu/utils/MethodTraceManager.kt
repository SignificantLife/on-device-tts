package com.mewmix.nabu.utils

import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object MethodTraceManager {
    private val running = AtomicBoolean(false)
    @Volatile private var path: String? = null

    @Synchronized
    fun start(context: Context): Boolean {
        if (running.get()) return true
        return runCatching {
            val logsDir: File? = context.getExternalFilesDir(null)?.resolve("logs")
            logsDir?.mkdirs()
            val traceFile = logsDir?.resolve("method.trace")
            require(traceFile != null) { "No external files dir available" }
            Debug.startMethodTracing(traceFile.absolutePath)
            path = traceFile.absolutePath
            running.set(true)
            Log.i("Nabu", "Method tracing started: ${traceFile.absolutePath}")
            DebugLogger.log("MethodTraceManager: started at ${traceFile.absolutePath}")
            true
        }.onFailure {
            Log.w("Nabu", "Failed to start method tracing", it)
            DebugLogger.log("MethodTraceManager: failed to start - ${it.message}")
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop(): Boolean {
        if (!running.get()) return false
        return runCatching {
            Debug.stopMethodTracing()
            running.set(false)
            Log.i("Nabu", "Method tracing stopped")
            DebugLogger.log("MethodTraceManager: stopped")
            true
        }.onFailure {
            Log.w("Nabu", "Failed to stop method tracing", it)
            DebugLogger.log("MethodTraceManager: failed to stop - ${it.message}")
        }.getOrDefault(false)
    }

    fun isRunning(): Boolean = running.get()

    fun tracePath(context: Context): String? {
        val p = path
        if (p != null) return p
        val logsDir: File? = context.getExternalFilesDir(null)?.resolve("logs")
        return logsDir?.resolve("method.trace")?.absolutePath
    }
}

