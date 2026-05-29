package com.smmrace.vfcash

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Api {
    data class Res(val ok: Boolean, val msg: String, val code: Int)

    fun verify(apiUrl: String, token: String, phone: String, amount: Double, smsText: String, smsTime: String): Res {
        return post("$apiUrl?action=verify", JSONObject().apply {
            put("token", token); put("phone", phone); put("amount", amount)
            put("sms_text", smsText); put("sms_time", smsTime)
        })
    }

    private fun post(url: String, body: JSONObject): Res = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true; connectTimeout = 20_000; readTimeout = 20_000
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        val code = conn.responseCode
        val txt  = try { conn.inputStream.bufferedReader().readText() }
                   catch(e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        conn.disconnect()
        val j = JSONObject(txt)
        Res(j.optBoolean("success"), j.optString("message"), code)
    } catch (e: Exception) {
        Res(false, e.message ?: "Error", -1)
    }
}
