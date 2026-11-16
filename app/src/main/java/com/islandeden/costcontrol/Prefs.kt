package com.islandeden.costcontrol

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** إعدادات التطبيق + بيانات الدخول المشفّرة (لا تُخزَّن كلمة المرور أبداً بدون تشفير) */
class Prefs(ctx: Context) {
    private val plain: SharedPreferences = ctx.getSharedPreferences("cc_prefs", Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "cc_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) { null }

    var server: String
        get() = normalize(plain.getString("server", null) ?: BuildConfig.DEFAULT_SERVER)
        set(v) = plain.edit().putString("server", normalize(v)).apply()

    var lastUser: String
        get() = plain.getString("last_user", "") ?: ""
        set(v) = plain.edit().putString("last_user", v).apply()

    var remember: Boolean
        get() = plain.getBoolean("remember", true)
        set(v) = plain.edit().putBoolean("remember", v).apply()

    /** هل سأل المستخدم عن تفعيل البصمة من قبل؟ */
    var bioAsked: Boolean
        get() = plain.getBoolean("bio_asked", false)
        set(v) = plain.edit().putBoolean("bio_asked", v).apply()

    val hasBio: Boolean get() = secure?.getString("bio_user", null) != null

    fun saveBio(user: String, pass: String) {
        secure?.edit()?.putString("bio_user", user)?.putString("bio_pass", pass)?.apply()
    }
    fun bioCreds(): Pair<String, String>? {
        val sp = secure ?: return null
        val u = sp.getString("bio_user", null) ?: return null
        val p = sp.getString("bio_pass", null) ?: return null
        return u to p
    }
    fun clearBio() { secure?.edit()?.clear()?.apply() }

    companion object {
        fun normalize(s: String): String {
            var u = s.trim()
            if (u.isEmpty()) u = BuildConfig.DEFAULT_SERVER
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            if (!u.endsWith("/")) u += "/"
            return u
        }
    }
}
