package com.smmrace.vfcash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
        if (ctx == null || !Prefs.enabled(ctx)) return
        val url   = Prefs.apiUrl(ctx)
        val token = Prefs.token(ctx)
        val vfNum = Prefs.vfPhone(ctx)
        if (url.isEmpty() || token.isEmpty()) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (sms in msgs) {
            val body   = sms.messageBody ?: continue
            val sender = sms.originatingAddress ?: ""
            val time   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(sms.timestampMillis))
            Log.d("VF_SMS", "from=$sender body=$body")

            if (!SmsParser.isVF(body, sender, vfNum)) continue

            val db = LocalDb(ctx)
            if (db.isDup(body)) { Log.w("VF_SMS", "Duplicate"); continue }

            val r = SmsParser.parse(body)
            if (r == null) { db.insert(body, null, null, "PARSE_FAIL"); continue }

            Thread {
                val res = Api.verify(url, token, r.phone, r.amount, body, time)
                db.insert(body, r.phone, r.amount, if(res.ok) "OK" else "FAIL", res.code, res.msg)
                Log.i("VF_SMS", "${if(res.ok)"✅" else "❌"} ${res.msg}")
            }.start()
        }
    }
}
