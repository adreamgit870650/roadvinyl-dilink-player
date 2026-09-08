package com.car.mp3player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class AudioFingerprint(
    val fileSize: Long,
    val modifiedAt: Long
) {
    val cacheable: Boolean get() = fileSize > 0L || modifiedAt > 0L
}

internal data class MusicIndexRecord(
    val cacheKey: String,
    val songPath: String,
    val title: String,
    val fingerprint: AudioFingerprint,
    val artist: String,
    val durationMs: Long
) {
    fun matches(candidate: AudioFingerprint): Boolean =
        candidate.cacheable && fingerprint == candidate
}

internal class MusicIndexStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_MUSIC (
                cache_key TEXT PRIMARY KEY NOT NULL,
                song_path TEXT NOT NULL,
                title TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                artist TEXT NOT NULL,
                duration_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_SCAN_STATE (
                id INTEGER PRIMARY KEY NOT NULL,
                state TEXT NOT NULL,
                started_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MUSIC")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCAN_STATE")
        onCreate(db)
    }

    fun beginScan() {
        updateScanState(writableDatabase, STATE_RUNNING)
    }

    /** Marks an ACC/process interruption without touching indexed songs. */
    fun recoverInterruptedScan(): Boolean {
        val db = writableDatabase
        val wasRunning = db.query(
            TABLE_SCAN_STATE,
            arrayOf(COL_STATE),
            "$COL_STATE_ID = ?",
            arrayOf(SCAN_STATE_ROW_ID.toString()),
            null, null, null
        ).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == STATE_RUNNING
        }
        if (wasRunning) updateScanState(db, STATE_INTERRUPTED)
        return wasRunning
    }

    fun loadAll(): Map<String, MusicIndexRecord> = runCatching {
        val result = HashMap<String, MusicIndexRecord>()
        readableDatabase.query(
            TABLE_MUSIC,
            arrayOf(COL_KEY, COL_PATH, COL_TITLE, COL_SIZE, COL_MODIFIED, COL_ARTIST, COL_DURATION),
            null, null, null, null, null
        ).use { cursor ->
            val keyColumn = cursor.getColumnIndexOrThrow(COL_KEY)
            val pathColumn = cursor.getColumnIndexOrThrow(COL_PATH)
            val titleColumn = cursor.getColumnIndexOrThrow(COL_TITLE)
            val sizeColumn = cursor.getColumnIndexOrThrow(COL_SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(COL_MODIFIED)
            val artistColumn = cursor.getColumnIndexOrThrow(COL_ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(COL_DURATION)
            while (cursor.moveToNext()) {
                val record = MusicIndexRecord(
                    cacheKey = cursor.getString(keyColumn),
                    songPath = cursor.getString(pathColumn),
                    title = cursor.getString(titleColumn),
                    fingerprint = AudioFingerprint(
                        fileSize = cursor.getLong(sizeColumn),
                        modifiedAt = cursor.getLong(modifiedColumn)
                    ),
                    artist = cursor.getString(artistColumn),
                    durationMs = cursor.getLong(durationColumn)
                )
                result[record.cacheKey] = record
            }
        }
        result
    }.getOrDefault(emptyMap())

    fun saveScan(
        records: List<MusicIndexRecord>,
        seenKeys: Set<String>,
        scanComplete: Boolean
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            records.forEach { record ->
                val values = ContentValues().apply {
                    put(COL_KEY, record.cacheKey)
                    put(COL_PATH, record.songPath)
                    put(COL_TITLE, record.title)
                    put(COL_SIZE, record.fingerprint.fileSize)
                    put(COL_MODIFIED, record.fingerprint.modifiedAt)
                    put(COL_ARTIST, record.artist)
                    put(COL_DURATION, record.durationMs)
                }
                db.insertWithOnConflict(TABLE_MUSIC, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (scanComplete) {
                val staleKeys = mutableListOf<String>()
                db.query(TABLE_MUSIC, arrayOf(COL_KEY), null, null, null, null, null).use { cursor ->
                    val keyColumn = cursor.getColumnIndexOrThrow(COL_KEY)
                    while (cursor.moveToNext()) {
                        cursor.getString(keyColumn).takeUnless(seenKeys::contains)?.let(staleKeys::add)
                    }
                }
                staleKeys.forEach { key ->
                    db.delete(TABLE_MUSIC, "$COL_KEY = ?", arrayOf(key))
                }
            }
            updateScanState(db, if (scanComplete) STATE_COMPLETE else STATE_INCOMPLETE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        runCatching {
            writableDatabase.apply {
                delete(TABLE_MUSIC, null, null)
                delete(TABLE_SCAN_STATE, null, null)
            }
        }
    }

    private fun updateScanState(db: SQLiteDatabase, state: String) {
        val values = ContentValues().apply {
            put(COL_STATE_ID, SCAN_STATE_ROW_ID)
            put(COL_STATE, state)
            put(COL_STARTED_AT, System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_SCAN_STATE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val DATABASE_NAME = "music_index.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_MUSIC = "music_index"
        private const val TABLE_SCAN_STATE = "scan_state"
        private const val COL_KEY = "cache_key"
        private const val COL_PATH = "song_path"
        private const val COL_TITLE = "title"
        private const val COL_SIZE = "file_size"
        private const val COL_MODIFIED = "modified_at"
        private const val COL_ARTIST = "artist"
        private const val COL_DURATION = "duration_ms"
        private const val COL_STATE_ID = "id"
        private const val COL_STATE = "state"
        private const val COL_STARTED_AT = "started_at"
        private const val SCAN_STATE_ROW_ID = 1
        private const val STATE_RUNNING = "RUNNING"
        private const val STATE_COMPLETE = "COMPLETE"
        private const val STATE_INCOMPLETE = "INCOMPLETE"
        private const val STATE_INTERRUPTED = "INTERRUPTED"
    }
}
