package com.mewmix.nabu.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private var logFile: File? = null
    private val logs = mutableListOf<String>()
    private const val MAX_BUFFERED_LOGS = 1000

    @Synchronized
    fun initialize(context: Context) {
        if (logFile == null) {
            val logDirectory = File(context.getExternalFilesDir(null), "logs")
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            logFile = File(logDirectory, "nabu_log.txt")
        }
    }

    private fun callerInfo(): String {
        val st = Thread.currentThread().stackTrace
        for (el in st) {
            val cls = el.className
            if (!cls.contains("DebugLogger") && !cls.contains("java.lang.Thread")) {
                val simple = cls.substringAfterLast('.')
                return "$simple.${el.methodName}:${el.lineNumber}"
            }
        }
        return "?"
    }

    private fun fmt(message: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val thread = Thread.currentThread().name
        return "$timestamp [$thread] ${callerInfo()} - $message"
    }

    @Synchronized
    fun log(message: String) {
        val line = fmt(message)
        if (logs.size > MAX_BUFFERED_LOGS) logs.removeAt(0)
        logs.add(line)
        logFile?.let {
            Log.d("Nabu", line)
            try {
                FileWriter(it, true).use { fw ->
                    fw.append(line).append('\n')
                }
            } catch (e: IOException) {
                Log.e("Nabu", "Failed to write to log file", e)
            }
        }
    }

    @Synchronized
    fun logErr(message: String, t: Throwable?) {
        val line = fmt(message)
        if (logs.size > MAX_BUFFERED_LOGS) logs.removeAt(0)
        logs.add(line)
        logFile?.let {
            Log.e("Nabu", line, t)
            try {
                FileWriter(it, true).use { fw ->
                    fw.append(line).append('\n')
                    if (t != null) {
                        fw.append(Log.getStackTraceString(t)).append('\n')
                    }
                }
            } catch (e: IOException) {
                Log.e("Nabu", "Failed to write to log file", e)
            }
        }
    }

    @Synchronized
    fun trace(label: String = "trace") {
        val sb = StringBuilder()
        sb.append("$label\n")
        Thread.currentThread().stackTrace.take(20).forEach { el ->
            sb.append("    at ${el.className}.${el.methodName}(${el.fileName}:${el.lineNumber})\n")
        }
        log(sb.toString().trimEnd())
    }

    fun getLogs(): List<String> = logs.toList()
}
