package com.islandeden.costcontrol

import android.webkit.CookieManager
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** الاتصال بخادم البرنامج — كل الطلبات تمر عبر /?_p= (يعمل على أي استضافة) */
object Api {
    class LoginResult(val ok: Boolean, val name: String = "", val error: String = "", val mustChange: Boolean = false)

    private fun open(server: String, path: String, method: String): HttpURLConnection {
        val c = URL(server + "?_p=" + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "CostControlApp/" + BuildConfig.VERSION_NAME + " (Android)")
        c.instanceFollowRedirects = true
        return c
    }

    private fun readBody(c: HttpURLConnection): String = try {
        (if (c.responseCode >= 400) c.errorStream else c.inputStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    } catch (e: IOException) { "" }

    /** تسجيل الدخول: يحفظ جلسة الخادم (cc_sid) داخل متصفح التطبيق */
    fun login(server: String, user: String, pass: String): LoginResult {
        return try {
            val c = open(server, "/api/login", "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("username", user).put("password", pass).toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val txt = readBody(c)
            val json = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
            if (code != 200) {
                return LoginResult(false, error = json.optString("error").ifBlank { "تعذر تسجيل الدخول (رمز $code)" })
            }
            val cookies = c.headerFields.entries.filter { it.key != null && it.key.equals("Set-Cookie", true) }.flatMap { it.value }
            val sid = cookies.firstOrNull { it.trim().startsWith("cc_sid=") }
                ?: return LoginResult(false, error = "الخادم لم يُرجع جلسة دخول")
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setCookie(server, sid.substringBefore(";") + "; Path=/; Max-Age=" + 14 * 86400 + (if (server.startsWith("https")) "; Secure" else ""))
            cm.flush()
            LoginResult(true, name = json.optString("name"), mustChange = json.optBoolean("mustChange"))
        } catch (e: Exception) {
            LoginResult(false, error = "تعذر الاتصال بالخادم — تأكد من الإنترنت ومن عنوان الخادم\n(${e.javaClass.simpleName})")
        }
    }

    /** هل الجلسة المحفوظة ما زالت صالحة؟ null = لا يوجد اتصال */
    fun sessionValid(server: String): Boolean? {
        val cookie = CookieManager.getInstance().getCookie(server) ?: return false
        if (!cookie.contains("cc_sid=")) return false
        return try {
            val c = open(server, "/api/me", "GET")
            c.setRequestProperty("Cookie", cookie)
            val code = c.responseCode
            readBody(c)
            code == 200
        } catch (e: Exception) { null }
    }

    fun logout(server: String) {
        try {
            val cookie = CookieManager.getInstance().getCookie(server)
            if (cookie != null) {
                val c = open(server, "/api/logout", "POST")
                c.setRequestProperty("Cookie", cookie)
                c.doOutput = true
                c.outputStream.use { it.write("{}".toByteArray()) }
                c.responseCode
            }
        } catch (_: Exception) { }
        clearSession()
    }

    fun clearSession() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }
}
