package com.islandeden.costcontrol

import android.webkit.JavascriptInterface

/** واجهة يستدعيها البرنامج (window.CCApp) من داخل الصفحة */
class AppBridge(private val a: MainActivity) {
    @JavascriptInterface fun sessionExpired() = a.runOnUiThread { a.toLogin(expired = true) }
    @JavascriptInterface fun logout() = a.runOnUiThread { a.toLogin(expired = false) }
    @JavascriptInterface fun saveFile(base64: String, name: String, mime: String) = a.runOnUiThread { a.saveBase64(base64, name, mime) }
    @JavascriptInterface fun printHtml(html: String, title: String) = a.runOnUiThread { a.printHtml(html, title) }
    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
}
