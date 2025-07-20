package com.example.kokoro82m.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues


class DatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks(uri TEXT PRIMARY KEY, line INTEGER, position INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS projects(uri TEXT PRIMARY KEY, styles TEXT, weights TEXT, mode TEXT, speed REAL, bookmark_line INTEGER, bookmark_position INTEGER, audio_path TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS bookmarks")
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks(uri TEXT PRIMARY KEY, line INTEGER, position INTEGER)")
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS projects(uri TEXT PRIMARY KEY, styles TEXT, weights TEXT, mode TEXT, speed REAL, bookmark_line INTEGER, bookmark_position INTEGER, audio_path TEXT)")
        }
    }

    companion object {
        private const val DATABASE_NAME = "app.db"
        private const val DATABASE_VERSION = 3

        @Volatile private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

object DatabaseManager {
    private fun helper(context: Context) = DatabaseHelper.getInstance(context)

    fun setSetting(context: Context, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        helper(context).writableDatabase.insertWithOnConflict(
            "settings", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getSetting(context: Context, key: String, default: String? = null): String? {
        val db = helper(context).readableDatabase
        db.query("settings", arrayOf("value"), "key=?", arrayOf(key), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        return default
    }

    fun setBookmark(context: Context, uri: String, line: Int, position: Int) {
        val values = ContentValues().apply {
            put("uri", uri)
            put("line", line)
            put("position", position)
        }
        helper(context).writableDatabase.insertWithOnConflict(
            "bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getBookmark(context: Context, uri: String): Bookmark? {
        val db = helper(context).readableDatabase
        db.query("bookmarks", arrayOf("line", "position"), "uri=?", arrayOf(uri), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) {
                    val line = cursor.getInt(0)
                    val position = cursor.getInt(1)
                    return Bookmark(line, position)
                }
            }
        return null
    }

    fun clearBookmark(context: Context, uri: String) {
        helper(context).writableDatabase.delete("bookmarks", "uri=?", arrayOf(uri))
    }

    fun setProject(context: Context, project: Project) {
        val values = ContentValues().apply {
            put("uri", project.uri)
            put("styles", project.styles.joinToString(","))
            put("weights", project.weights.entries.joinToString(",") { "${it.key}|${it.value}" })
            put("mode", project.mode.name)
            put("speed", project.speed)
            project.bookmark?.let {
                put("bookmark_line", it.line)
                put("bookmark_position", it.position)
            }
            put("audio_path", project.audioPath)
        }
        helper(context).writableDatabase.insertWithOnConflict(
            "projects", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getProject(context: Context, uri: String): Project? {
        val db = helper(context).readableDatabase
        db.query("projects", null, "uri=?", arrayOf(uri), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val styles = cursor.getString(cursor.getColumnIndexOrThrow("styles"))
                    ?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
                val weightsString = cursor.getString(cursor.getColumnIndexOrThrow("weights"))
                val weights = mutableMapOf<String, Float>()
                weightsString?.split(',')?.forEach { entry ->
                    val parts = entry.split('|')
                    if (parts.size == 2) {
                        weights[parts[0]] = parts[1].toFloatOrNull() ?: 1f
                    }
                }
                val mode = InterpolationMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("mode")))
                val speed = cursor.getFloat(cursor.getColumnIndexOrThrow("speed"))
                val lineIndex = cursor.getColumnIndex("bookmark_line")
                val posIndex = cursor.getColumnIndex("bookmark_position")
                val bookmark = if (lineIndex >= 0 && !cursor.isNull(lineIndex) && posIndex >= 0 && !cursor.isNull(posIndex)) {
                    Bookmark(cursor.getInt(lineIndex), cursor.getInt(posIndex))
                } else null
                val audioPath = cursor.getString(cursor.getColumnIndexOrThrow("audio_path"))
                return Project(uri, styles, weights, mode, speed, bookmark, audioPath)
            }
        }
        return null
    }
}

