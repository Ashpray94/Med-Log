package com.suryaprakash.medlog.data

import android.content.Context
import android.net.Uri
import com.suryaprakash.medlog.medlog
import com.suryaprakash.medlog.sync.RestoreSql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup and move to a new phone (plan 15). The backup is a complete copy of the database, encrypted by
 * SQLCipher with the person's own password (the phone's own key can't leave the phone, by design).
 * The person chooses where the file goes.
 */
object Backup {
    private fun columns(db: androidx.sqlite.db.SupportSQLiteDatabase, schema: String): Map<String, List<RestoreSql.ColInfo>> {
        val tables = ArrayList<String>()
        db.query("SELECT name FROM $schema.sqlite_master WHERE type = 'table'").use { while (it.moveToNext()) tables += it.getString(0) }
        return tables.associateWith { t ->
            val cols = ArrayList<RestoreSql.ColInfo>()
            db.query("PRAGMA $schema.table_info(`$t`)").use { c ->
                val n = c.getColumnIndexOrThrow("name"); val ty = c.getColumnIndexOrThrow("type")
                val nn = c.getColumnIndexOrThrow("notnull"); val d = c.getColumnIndexOrThrow("dflt_value")
                while (c.moveToNext()) cols += RestoreSql.ColInfo(c.getString(n), c.getString(ty), c.getInt(nn) != 0, !c.isNull(d))
            }
            cols
        }
    }

    private fun q(s: String) = "'" + s.replace("'", "''") + "'"

    suspend fun export(ctx: Context, uri: Uri, password: String) = withContext(Dispatchers.IO) {
        require(password.length >= 6)
        val db = ctx.medlog.db.openHelper.writableDatabase
        val tmp = File(ctx.cacheDir, "backup.tmp").apply { delete() }
        db.query("PRAGMA wal_checkpoint(FULL)").close()
        db.execSQL("ATTACH DATABASE ${q(tmp.absolutePath)} AS bk KEY ${q(password)}")
        try { db.query("SELECT sqlcipher_export('bk')").use { it.moveToFirst() } } finally { db.execSQL("DETACH DATABASE bk") }
        ctx.contentResolver.openOutputStream(uri, "w")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
        tmp.delete()
    }

    suspend fun import(ctx: Context, uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "restore.tmp").apply { delete() }
        ctx.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val db = ctx.medlog.db.openHelper.writableDatabase
        db.execSQL("ATTACH DATABASE ${q(tmp.absolutePath)} AS bk KEY ${q(password)}")
        try {
            db.query("SELECT count(*) FROM bk.sqlite_master").use { it.moveToFirst() }   // throws if the password is wrong
            db.beginTransaction()
            try {
                // columns that exist in both databases only, so a backup from another version restores (B16)
                for (sql in RestoreSql.statements(columns(db, "main"), columns(db, "bk").mapValues { e -> e.value.map { it.name } })) db.execSQL(sql)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } finally { db.execSQL("DETACH DATABASE bk"); tmp.delete() }
    }
}
