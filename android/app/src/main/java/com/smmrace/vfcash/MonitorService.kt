package com.smmrace.vfcash

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MonitorService : Service() {
    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel("vf", "VF Cash", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        startForeground(1001, NotificationCompat.Builder(this, "vf")
            .setContentTitle("VF Cash 🟢 يعمل")
            .setContentText("يراقب رسائل فودافون كاش")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build())
        return START_STICKY
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onTaskRemoved(r: Intent?) {
        val pi = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 3000, pi)
        super.onTaskRemoved(r)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, i: Intent?) {
        if (ctx == null || !Prefs.enabled(ctx)) return
        val svc = Intent(ctx, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(svc)
        else ctx.startService(svc)
    }
}
