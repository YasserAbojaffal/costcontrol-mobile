package com.islandeden.costcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.islandeden.costcontrol.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var printView: WebView? = null
    private var lastBack = 0L
    private var mainError = false

    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        finishChooser(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
    }

    @Suppress("UNCHECKED_CAST")
    private fun finishChooser(v: Array<Uri>?) {
        val cb = fileCb as ValueCallback<Array<Uri>?>?
        fileCb = null
        cb?.onReceiveValue(v)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark

        // الصفحة تحت شريط الحالة وفوق شريط التنقل (الصفحة نفسها تتعامل مع النوتش)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, 0)
            insets
        }

        val w = b.web
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString CostControlApp/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        w.addJavascriptInterface(AppBridge(this), "CCApp")
        w.overScrollMode = View.OVER_SCROLL_NEVER

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                val host = Uri.parse(prefs.server).host
                if (u.host == host && (u.scheme == "https" || u.scheme == "http")) return false
                // روابط خارجية (واتساب، هاتف، بريد…) تفتح بتطبيقاتها
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { mainError = false }
            override fun onPageFinished(view: WebView, url: String?) {
                b.swipe.isRefreshing = false
                if (!mainError) b.offline.visibility = View.GONE
                CookieManager.getInstance().flush()
            }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) { mainError = true; b.offline.visibility = View.VISIBLE; b.swipe.isRefreshing = false }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                b.bar.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                b.bar.setProgressCompat(p, true)
            }
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                finishChooser(null)
                fileCb = cb
                return try {
                    val i = params.createIntent()
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    pickFiles.launch(Intent.createChooser(i, "اختر صورة أو ملف"))
                    true
                } catch (e: Exception) { fileCb = null; false }
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        w.setDownloadListener { url, ua, cd, mime, _ -> download(url, ua, cd, mime) }

        // السحب للتحديث فقط عندما تكون الصفحة في أعلاها
        b.swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_ink))
        b.swipe.setOnChildScrollUpCallback { _, _ -> w.scrollY > 0 }
        b.swipe.setOnRefreshListener { w.reload() }
        b.btnRetry.setOnClickListener { b.offline.visibility = View.GONE; w.reload() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (w.canGoBack()) { w.goBack(); return }
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) finish() else { lastBack = now; Toast.makeText(this@MainActivity, "اضغط رجوع مرة أخرى للخروج", Toast.LENGTH_SHORT).show() }
            }
        })

        if (savedInstanceState != null) w.restoreState(savedInstanceState) else w.loadUrl(prefs.server)
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); b.web.saveState(outState) }
    override fun onPause() { super.onPause(); CookieManager.getInstance().flush() }

    fun toLogin(expired: Boolean) {
        if (!expired) { Thread { Api.logout(prefs.server) }.start() } else Api.clearSession()
        startActivity(Intent(this, LoginActivity::class.java).putExtra(if (expired) "expired" else "logout", true))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // ===== تنزيل الملفات (Excel / نسخ احتياطية) بنفس جلسة الدخول =====
    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 7)
            Toast.makeText(this, "اسمح بالتخزين ثم أعد التنزيل", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val r = DownloadManager.Request(Uri.parse(url))
            r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            r.addRequestHeader("User-Agent", ua ?: "")
            r.setMimeType(mime)
            r.setTitle(name)
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
            Snackbar.make(b.root, "جاري تنزيل $name — ستجده في التنزيلات", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Snackbar.make(b.root, "تعذر التنزيل: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    // ===== حفظ ملف أنشأته الصفحة (Excel الصفحة) في التنزيلات =====
    fun saveBase64(data: String, name: String, mime: String) {
        try {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
            val uri: Uri = if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw Exception("تعذر إنشاء الملف")
                contentResolver.openOutputStream(u)?.use { it.write(bytes) }
                u
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
                val f = File(dir, safe)
                f.writeBytes(bytes)
                FileProvider.getUriForFile(this, "$packageName.files", f)
            }
            Snackbar.make(b.root, "تم حفظ $safe في التنزيلات", Snackbar.LENGTH_LONG).setAction("فتح") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (_: Exception) { Toast.makeText(this, "لا يوجد تطبيق لفتح هذا الملف (مثل Excel)", Toast.LENGTH_LONG).show() }
            }.show()
        } catch (e: Exception) {
            Snackbar.make(b.root, "تعذر حفظ الملف: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    // ===== طباعة / حفظ PDF بترويسة الشركة =====
    @SuppressLint("SetJavaScriptEnabled")
    fun printHtml(html: String, title: String) {
        val pv = WebView(this)
        printView = pv
        pv.settings.javaScriptEnabled = false
        pv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                val name = "CostControl - $title"
                val landscape = Regex("@page\\s*\\{[^}]*landscape").containsMatchIn(html)
                val attrs = PrintAttributes.Builder()
                    .setMediaSize(if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4)
                    .build()
                pm.print(name, view.createPrintDocumentAdapter(name), attrs)
            }
        }
        pv.loadDataWithBaseURL(prefs.server, html, "text/html", "utf-8", null)
    }
}
