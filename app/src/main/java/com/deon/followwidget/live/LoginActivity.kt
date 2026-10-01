package com.deon.followwidget.live

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * In-app Instagram login. The user logs in on instagram.com inside this
 * WebView — the password is typed into Instagram's own page and never
 * touches our code. On Done we keep only the session cookie, privately
 * on this device, so fetches go out as a logged-in user (far less
 * likely to be blocked than anonymous requests).
 */
class LoginActivity : Activity() {

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun loggedInCookies(): Triple<String, String, String>? {
        val cm = CookieManager.getInstance()
        val raw = cm.getCookie("https://www.instagram.com/") ?: return null
        val map = raw.split(";").mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
        }.toMap()
        val sid = map["sessionid"].orEmpty()
        if (sid.isEmpty()) return null
        return Triple(sid, map["csrftoken"].orEmpty(), map["ds_user_id"].orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        M3Ui.dark = WidgetStore.isAppDarkTheme(this)
        M3Ui.accentKey = WidgetStore.getAccent(this)
        window.statusBarColor = M3Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(M3Ui.BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "Log in with Instagram"
            setTextColor(M3Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
        })
        val hint = TextView(this).apply {
            text = "Log in on Instagram's page below, then tap Done."
            setTextColor(M3Ui.GREY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        header.addView(hint)
        root.addView(header)

        val web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            CookieManager.getInstance().setAcceptCookie(true)
        }

        val doneBtn = Button(this).apply {
            text = "Done"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            isEnabled = false
            alpha = 0.4f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(M3Ui.TEAL)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(16); rightMargin = dp(16)
                topMargin = dp(10); bottomMargin = dp(16)
            }
        }
        root.addView(web)
        root.addView(doneBtn)

        fun refreshDoneState() {
            val ok = loggedInCookies() != null
            doneBtn.isEnabled = ok
            doneBtn.alpha = if (ok) 1f else 0.4f
            hint.text = if (ok) {
                "✓ Logged in — tap Done to save the session."
            } else {
                "Log in on Instagram's page below, then tap Done."
            }
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                refreshDoneState()
            }
        }
        web.loadUrl("https://www.instagram.com/accounts/login/")

        doneBtn.setOnClickListener {
            val cookies = loggedInCookies()
            if (cookies == null) {
                Toast.makeText(this, "Not logged in yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (sid, csrf, uid) = cookies
            IgSession.save(this, sid, csrf, uid, "")
            Toast.makeText(this, "Logged in", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }

        setContentView(root)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}
