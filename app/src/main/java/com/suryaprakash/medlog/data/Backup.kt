package com.suryaprakash.medlog.data

import android.content.Context
import android.net.Uri
import com.suryaprakash.medlog.medlog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup and move to a new phone (plan 15). The backup is a complete copy of the database, encrypted by
 * SQLCipher with the person's own password (the phone's own key can't leave the phone, by design).
 * The person chooses where the file goes.
 */
object Backup {
    private val TABLES = listOf("profile", "helpers", "notes", "medicines", "doses", "appointments", "doc_lines", "inbox")

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
                for (t in TABLES) { db.execSQL("DELETE FROM main.$t"); db.execSQL("INSERT INTO main.$t SELECT * FROM bk.$t") }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } finally { db.execSQL("DETACH DATABASE bk"); tmp.delete() }
    }
}
