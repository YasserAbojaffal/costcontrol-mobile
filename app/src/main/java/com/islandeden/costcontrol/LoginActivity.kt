package com.islandeden.costcontrol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.islandeden.costcontrol.databinding.ActivityLoginBinding
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var checking = true
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { checking }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        Fonts.apply(b.root)
        prefs = Prefs(this)

        // مسافة شريط الحالة أعلى الشاشة
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (b.btnServer.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + dp(8)
            b.root.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        b.etUser.setText(prefs.lastUser)
        b.cbRemember.isChecked = prefs.remember
        b.tvServer.text = prefs.server.removeSuffix("/")
        b.tvBrand.text = "Island Eden · Cost Control"

        b.btnLogin.setOnClickListener { doLogin(text(b.etUser), text(b.etPass), false) }
        b.etPass.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(text(b.etUser), text(b.etPass), false); true } else false
        }
        b.btnBio.setOnClickListener { bioLogin() }
        b.btnServer.setOnClickListener { serverDialog() }
        b.tvForgot.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("نسيت كلمة المرور؟")
                .setMessage("لأمان حسابك، إعادة تعيين كلمة المرور تتم بواسطة مدير النظام فقط.\n\nتواصل معه ليعطيك كلمة مؤقتة، وسيطلب منك البرنامج تغييرها عند أول دخول.")
                .setPositiveButton("حسناً", null).show()
        }
        b.tvPowered.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/201010439139"))) } catch (_: Exception) { }
        }
        b.tvSecure.text = getString(R.string.secure) + " · v" + BuildConfig.VERSION_NAME

        val expired = intent.getBooleanExtra("expired", false)
        val loggedOut = intent.getBooleanExtra("logout", false)
        if (expired) showError("انتهت الجلسة — سجّل الدخول مرة أخرى")

        // الجلسة المحفوظة ما زالت صالحة؟ ادخل مباشرة
        if (!expired && !loggedOut) {
            io.execute {
                val ok = Api.sessionValid(prefs.server)
                runOnUiThread {
                    checking = false
                    if (ok == true) openMain() else afterCheck(auto = true)
                }
            }
        } else { checking = false; afterCheck(auto = false) }
    }

    private fun afterCheck(auto: Boolean) {
        refreshBio()
        if (auto && prefs.hasBio && bioAvailable()) b.root.postDelayed({ bioLogin() }, 350)
        else if (text(b.etUser).isEmpty()) b.etUser.requestFocus()
    }

    private fun text(e: EditText) = e.text?.toString()?.trim() ?: ""
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun setBusy(on: Boolean) {
        busy = on
        b.btnLogin.isEnabled = !on
        b.btnBio.isEnabled = !on
        b.btnLogin.text = if (on) "" else getString(R.string.login)
        b.progress.visibility = if (on) View.VISIBLE else View.GONE
        b.etUser.isEnabled = !on
        b.etPass.isEnabled = !on
    }

    private fun showError(msg: String?) {
        if (msg.isNullOrBlank()) { b.errBox.visibility = View.GONE; return }
        b.tvError.text = msg
        b.errBox.visibility = View.VISIBLE
        b.errBox.alpha = 0f
        b.errBox.animate().alpha(1f).setDuration(180).start()
    }

    private fun doLogin(user: String, pass: String, fromBio: Boolean) {
        if (busy) return
        b.tilUser.error = null; b.tilPass.error = null
        if (user.isEmpty()) { b.tilUser.error = "اكتب اسم المستخدم"; b.etUser.requestFocus(); return }
        if (pass.isEmpty()) { b.tilPass.error = "اكتب كلمة المرور"; b.etPass.requestFocus(); return }
        showError(null)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(b.root.windowToken, 0)
        setBusy(true)
        val server = prefs.server
        io.execute {
            val r = Api.login(server, user, pass)
            runOnUiThread {
                setBusy(false)
                if (!r.ok) {
                    if (fromBio && r.error.contains("كلمة المرور")) {
                        prefs.clearBio(); refreshBio()
                        showError("تغيّرت كلمة المرور — سجّل الدخول بالكلمة الجديدة لتفعيل البصمة مرة أخرى")
                    } else showError(r.error)
                    b.card.animate().translationX(dp(10).toFloat()).setDuration(60).withEndAction {
                        b.card.animate().translationX(-dp(8).toFloat()).setDuration(60).withEndAction {
                            b.card.animate().translationX(0f).setDuration(60).start()
                        }.start()
                    }.start()
                    return@runOnUiThread
                }
                prefs.remember = b.cbRemember.isChecked
                prefs.lastUser = if (b.cbRemember.isChecked) user else ""
                if (!fromBio && b.cbRemember.isChecked && bioAvailable() && !prefs.hasBio && !prefs.bioAsked) {
                    askEnableBio(user, pass)
                } else {
                    if (!fromBio && prefs.hasBio) prefs.saveBio(user, pass) // تحديث بعد تغيير كلمة المرور
                    openMain()
                }
            }
        }
    }

    private fun askEnableBio(user: String, pass: String) {
        prefs.bioAsked = true
        MaterialAlertDialogBuilder(this)
            .setTitle("الدخول بالبصمة")
            .setIcon(R.drawable.ic_fingerprint)
            .setMessage("هل تريد الدخول في المرات القادمة ببصمة الإصبع أو الوجه بدلاً من كتابة كلمة المرور؟\nبياناتك تُحفظ مشفّرة على هذا الجهاز فقط.")
            .setPositiveButton("تفعيل") { _, _ ->
                authenticate("تفعيل الدخول بالبصمة") { prefs.saveBio(user, pass); Toast.makeText(this, "تم تفعيل الدخول بالبصمة", Toast.LENGTH_SHORT).show(); openMain() }
            }
            .setNegativeButton("ليس الآن") { _, _ -> openMain() }
            .setCancelable(false)
            .show()
    }

    private fun bioAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ||
            bm.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun refreshBio() {
        b.bioGroup.visibility = if (prefs.hasBio && bioAvailable()) View.VISIBLE else View.GONE
    }

    private fun bioLogin() {
        val creds = prefs.bioCreds() ?: return
        authenticate("الدخول إلى Cost Control") {
            b.etUser.setText(creds.first)
            doLogin(creds.first, creds.second, true)
        }
    }

    private fun authenticate(title: String, onOk: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON && code != BiometricPrompt.ERROR_CANCELED) showError(msg.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(prefs.bioCreds()?.first ?: text(b.etUser))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    private fun serverDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(this)
        input.setText(prefs.server)
        input.textDirection = View.TEXT_DIRECTION_LTR
        input.setSingleLine()
        val til = com.google.android.material.textfield.TextInputLayout(this)
        til.hint = getString(R.string.server)
        til.addView(input)
        val box = FrameLayout(this)
        box.setPadding(dp(22), dp(8), dp(22), 0)
        box.addView(til)
        MaterialAlertDialogBuilder(this)
            .setTitle("إعدادات الاتصال")
            .setMessage("عنوان البرنامج على الإنترنت أو على شبكة الفندق")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val n = Prefs.normalize(input.text?.toString() ?: "")
                if (n != prefs.server) { Api.clearSession(); prefs.clearBio(); prefs.bioAsked = false }
                prefs.server = n
                b.tvServer.text = n.removeSuffix("/")
                refreshBio()
            }
            .setNeutralButton("الافتراضي") { _, _ -> prefs.server = BuildConfig.DEFAULT_SERVER; b.tvServer.text = prefs.server.removeSuffix("/") }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() { super.onDestroy(); io.shutdown() }
}
