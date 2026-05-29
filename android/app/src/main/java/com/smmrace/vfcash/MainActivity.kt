package com.smmrace.vfcash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl:    EditText
    private lateinit var etToken:  EditText
    private lateinit var etPhone:  EditText
    private lateinit var swOn:     Switch
    private lateinit var tvStatus: TextView
    private lateinit var lvLogs:   ListView
    private lateinit var tvCount:  TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)

        etUrl    = findViewById(R.id.etUrl)
        etToken  = findViewById(R.id.etToken)
        etPhone  = findViewById(R.id.etPhone)
        swOn     = findViewById(R.id.swOn)
        tvStatus = findViewById(R.id.tvStatus)
        lvLogs   = findViewById(R.id.lvLogs)
        tvCount  = findViewById(R.id.tvCount)

        etUrl.setText(Prefs.apiUrl(this))
        etToken.setText(Prefs.token(this))
        etPhone.setText(Prefs.vfPhone(this))
        swOn.isChecked = Prefs.enabled(this)

        refreshStatus(); loadLogs()

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url   = etUrl.text.toString().trim()
            val token = etToken.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (url.isEmpty() || token.isEmpty() || phone.isEmpty()) {
                toast("أكمل كل الحقول"); return@setOnClickListener
            }
            Prefs.save(this, url, token, phone)
            toast("✅ تم الحفظ"); refreshStatus()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val url   = Prefs.apiUrl(this)
            val token = Prefs.token(this)
            val phone = Prefs.vfPhone(this)
            if (url.isEmpty() || token.isEmpty()) { toast("احفظ الإعدادات أولاً"); return@setOnClickListener }
            toast("جاري الاختبار...")
            Thread {
                val fakeBody = "لقد استلمت 1.00 جنيه من $phone"
                val t = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val r = Api.verify(url, token, phone, 1.0, fakeBody, t)
                runOnUiThread {
                    toast(if (r.ok) "✅ API يعمل: ${r.msg}" else "❌ ${r.msg} (${r.code})")
                    loadLogs()
                }
            }.start()
        }

        swOn.setOnCheckedChangeListener { _, on ->
            Prefs.setEnabled(this, on)
            if (on) requestPerms() else stopService(Intent(this, MonitorService::class.java))
            refreshStatus()
        }

        requestBatteryExemption()
        requestPerms()
    }

    override fun onResume() { super.onResume(); loadLogs() }

    private fun loadLogs() {
        val logs = LocalDb(this).logs(60)
        tvCount.text = "${logs.size} عملية"
        lvLogs.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logs)
    }

    private fun refreshStatus() {
        val on = Prefs.enabled(this)
        tvStatus.text = buildString {
            appendLine("الحالة: ${if(on) "🟢 تعمل" else "🔴 متوقفة"}")
            appendLine("API: ${Prefs.apiUrl(this@MainActivity).ifEmpty{"غير محدد"}}")
            val t = Prefs.token(this@MainActivity)
            appendLine("Token: ${if(t.isNotEmpty()) "✅ (${t.take(8)}...)" else "❌ غير محدد"}")
            appendLine("رقم VF: ${Prefs.vfPhone(this@MainActivity)}")
        }
    }

    private fun requestPerms() {
        val need = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        else if (Prefs.enabled(this)) startMonitor()
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(req, perms, grants)
        if (req == 1001) {
            if (grants.all { it == PackageManager.PERMISSION_GRANTED }) { if (Prefs.enabled(this)) startMonitor() }
            else { toast("⚠️ يجب منح إذن SMS"); swOn.isChecked = false; Prefs.setEnabled(this, false) }
        }
    }

    private fun startMonitor() {
        val i = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
