package com.example.kokoro82m.utils

import android.content.Context

object BookmarkManager {
    fun save(context: Context, uri: String, line: Int) {
        DatabaseManager.setBookmark(context, uri, line)
    }

    fun load(context: Context, uri: String): Int {
        return DatabaseManager.getBookmark(context, uri) ?: -1
    }

    fun clear(context: Context, uri: String) {
        DatabaseManager.clearBookmark(context, uri)
    }
}
