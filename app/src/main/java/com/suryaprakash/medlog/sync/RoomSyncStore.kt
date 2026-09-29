package com.suryaprakash.medlog.sync

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/** [SqlDb] over the phone's (SQLCipher) database. */
class SupportSqlDb(private val db: SupportSQLiteDatabase) : SqlDb {
    override fun query(sql: String, args: List<Any?>): List<Map<String, Any?>> =
        db.query(SimpleSQLiteQuery(sql, args.toTypedArray())).use { c ->
            val out = ArrayList<Map<String, Any?>>()
            while (c.moveToNext()) {
                val m = HashMap<String, Any?>()
                for (i in 0 until c.columnCount) m[c.getColumnName(i)] = when (c.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    android.database.Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> null
                }
                out += m
            }
            out
        }

    override fun exec(sql: String, args: List<Any?>) = db.execSQL(sql, args.toTypedArray())

    override fun <T> transaction(block: () -> T): T {
        db.beginTransaction()
        try { val r = block(); db.setTransactionSuccessful(); return r } finally { db.endTransaction() }
    }
}

/**
 * The phone's [SyncStore]: all the logic is in [SqlSyncStore]. Use it from ONE thread at a time (a single-threaded dispatcher).
 * Each call of apply() returns which tables changed (Applied.tables) so alarms can be planned again after "medicines" or "doses".
 */
class RoomSyncStore(db: SupportSQLiteDatabase) : SqlSyncStore(SupportSqlDb(db))
