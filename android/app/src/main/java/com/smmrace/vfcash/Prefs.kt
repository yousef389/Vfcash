package com.smmrace.vfcash

import android.content.Context

object Prefs {
    private const val NAME = "vf_prefs"
    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun apiUrl(c: Context)  = sp(c).getString("api_url",  "https://smmrace.space/apivfcash.php") ?: ""
    fun token(c: Context)   = sp(c).getString("token",    "") ?: ""
    fun vfPhone(c: Context) = sp(c).getString("vf_phone", "01107122194") ?: ""
    fun enabled(c: Context) = sp(c).getBoolean("enabled",  false)

    fun save(c: Context, url: String, token: String, phone: String) =
        sp(c).edit().putString("api_url", url).putString("token", token).putString("vf_phone", phone).apply()

    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()
}
