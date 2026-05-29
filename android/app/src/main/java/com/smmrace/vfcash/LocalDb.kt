package com.smmrace.vfcash

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx, "vf.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) = db.execSQL("""
        CREATE TABLE log(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            hash TEXT NOT NULL UNIQUE,
            phone TEXT, amount REAL,
            status TEXT, http INTEGER, msg TEXT,
            ts TEXT DEFAULT (datetime('now','localtime'))
        )""")
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { db.execSQL("DROP TABLE IF EXISTS log"); onCreate(db) }

    fun isDup(text: String) = readableDatabase
        .query("log", arrayOf("id"), "hash=?", arrayOf(sha256(text)), null,null,null)
        .use { it.count > 0 }

    fun insert(text: String, phone: String?, amount: Double?, status: String, http: Int=0, msg: String="") =
        writableDatabase.insertWithOnConflict("log", null, ContentValues().apply {
            put("hash",   sha256(text)); put("phone", phone); put("amount", amount)
            put("status", status); put("http", http); put("msg", msg)
        }, SQLiteDatabase.CONFLICT_IGNORE)

    fun logs(n: Int=60): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT ts,status,phone,amount,http FROM log ORDER BY id DESC LIMIT $n", null).use { c ->
            while (c.moveToNext())
                out += "${c.getString(0)} | ${c.getString(1)} | ${c.getString(2)?:""} | ${c.getDouble(3)}ج | ${c.getInt(4)}"
        }
        return out
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
