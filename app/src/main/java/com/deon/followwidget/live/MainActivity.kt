package com.deon.followwidget.live

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * InstaW Live settings — compact Material You screen. Just a username:
 * the follower count, display name, photo and verified badge are fetched
 * from Instagram automatically and refreshed every ~15 minutes.
 * No login, no account involved.
 */
class MainActivity : Activity() {

    private var themeKey: String = "light"
    private var cardStyleKey: String = "count_top"
    private var loginStatus: TextView? = null
    private var loginBtn: Button? = null
    private var userInputField: EditText? = null
    private val previewHolders = mutableListOf<android.widget.FrameLayout>()
    private val styleKeys = listOf("count_top", "name_top")
    private val themeKeys = listOf(
        "light" to "Light",
        "dark" to "Dark",
        "liquid_glass" to "Liquid Glass",
        "frosted_white" to "Frosted White",
        "frosted_black" to "Frosted Black",
        "system" to "System",
        "system_frosted" to "System (Frosted)"
    )

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun sp(v: TextView, n: Float) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, n)
    }
    private fun bold(v: TextView) {
        v.setTypeface(v.typeface, Typeface.BOLD)
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(M3Ui.TEAL)
        sp(this, 13f)
        bold(this)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16); bottomMargin = dp(6) }
    }

    private fun compactInput(parent: LinearLayout, label: String, hintText: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(M3Ui.INK)
            sp(this, 12f)
            bold(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); bottomMargin = dp(3) }
        })
        val field = EditText(this).apply {
            hint = hintText
            setTextColor(M3Ui.INK)
            sp(this, 15f)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(M3Ui.FIELD_BG)
                        setStroke(dp(2), M3Ui.TEAL)
                    }
                )
                addState(
                    intArrayOf(),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(M3Ui.FIELD_BG)
                        setStroke(dp(1), M3Ui.FIELD_OUTLINE)
                    }
                )
            }
            setPadding(dp(14), dp(9), dp(14), dp(9))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        parent.addView(field)
        return field
    }

    private fun compactButton(text: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(if (filled) Color.WHITE else M3Ui.TEAL)
            sp(this, 14f)
            bold(this)
            minimumHeight = dp(44)
            background = if (filled) {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(22).toFloat()
                    setColor(M3Ui.TEAL)
                }
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(22).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2), M3Ui.TEAL)
                }
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun agoText(whenMs: Long): String {
        if (whenMs <= 0) return "not fetched yet"
        val mins = (System.currentTimeMillis() - whenMs) / 60000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 1440 -> "${mins / 60} hr ago"
            else -> "${mins / 1440} days ago"
        }
    }

    // ------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        refreshLoginState()
        // Re-render immediately so a system theme toggle is reflected at
        // once, then fetch fresh data in the background.
        FollowerWidgetProvider.allWidgetIds(this).forEach {
            FollowerWidgetProvider.updateWidget(this, it)
        }
        FollowerWidgetProvider.refreshAllProviders(this)
    }

    /**
     * Re-renders both CARD STYLE previews with the live fetched data for the
     * current username and the currently selected card theme.
     */
    private fun renderStylePreviews() {
        val input = userInputField ?: return
        val u = input.text.toString().trim().trimStart('@').lowercase()
            .ifEmpty { WidgetStore.getDefaultUsername(this) }
        val density = resources.displayMetrics.density
        styleKeys.forEachIndexed { i, style ->
            if (i >= previewHolders.size) return@forEachIndexed
            val holder = previewHolders[i]
            holder.removeAllViews()
            val rv = FollowerWidgetProvider.createStylePreview(
                this, style, themeKey, u, forPreview = true
            )
            val pv = rv.apply(this, holder)
            // Preview layouts use wrap_content; let them size naturally.
            pv.layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            pv.isClickable = false
            pv.isFocusable = false
            (pv.findViewById<View>(R.id.card))?.apply {
                isClickable = false
                isFocusable = false
            }
            holder.addView(pv)
            holder.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (14 * density).toFloat()
                setColor(Color.TRANSPARENT)
                if (style == cardStyleKey) setStroke(
                    (3 * density).toInt(), M3Ui.TEAL
                ) else setStroke(
                    (1 * density).toInt(), M3Ui.FIELD_OUTLINE
                )
            }
            val p = (3 * density).toInt()
            holder.setPadding(p, p, p, p)
        }
    }

    private fun refreshLoginState() {        val status = loginStatus ?: return
        val btn = loginBtn ?: return
        if (IgSession.isLoggedIn(this)) {
            val name = IgSession.username(this)
            status.setTextColor(M3Ui.TEAL_DARK)
            status.text = if (name.isNotEmpty()) {
                "✓ Logged in as @$name — fetches use your session."
            } else {
                "✓ Logged in — fetches use your session."
            }
            btn.text = "Log out"
        } else {
            status.setTextColor(M3Ui.GREY)
            status.text = "Not logged in. Log in once — your password never " +
                "leaves this phone; only the session is kept, privately."
            btn.text = "Log in with Instagram"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        M3Ui.dark = WidgetStore.isAppDarkTheme(this)
        window.statusBarColor = M3Ui.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val scroll = ScrollView(this).apply {
            addView(root)
            setBackgroundColor(M3Ui.BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- compact header ----
        root.addView(TextView(this).apply {
            text = "InstaW Live by Deon"
            setTextColor(M3Ui.INK)
            sp(this, 22f)
            bold(this)
        })
        root.addView(TextView(this).apply {
            text = "Enter a username — details fetch automatically."
            setTextColor(M3Ui.GREY)
            sp(this, 13f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })

        // ---- instagram account ----
        root.addView(sectionLabel("Instagram account"))
        val userInput = compactInput(root, "Username", "deepak.deon")
        userInput.setText(WidgetStore.getDefaultUsername(this))
        userInputField = userInput

        val statusLine = TextView(this).apply {
            setTextColor(M3Ui.TEAL_DARK)
            sp(this, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); bottomMargin = dp(4) }
        }
        root.addView(statusLine)

        fun refreshStatus(u: String) {
            val count = WidgetStore.getCount(this, u)
            val whenMs = WidgetStore.getLastFetch(this, u)
            statusLine.text = if (count > 0 && whenMs > 0) {
                "✓ ${String.format(Locale.US, "%,d", count)} followers · updated ${agoText(whenMs)}"
            } else {
                "No data yet — tap Fetch details."
            }
        }
        refreshStatus(userInput.text.toString())

        val fetchBtn = compactButton("Fetch details", filled = true) {}
        root.addView(fetchBtn)
        fetchBtn.setOnClickListener {
            val u = userInput.text.toString().trim().trimStart('@').lowercase()
            if (u.isEmpty()) {
                Toast.makeText(this, "Enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchBtn.isEnabled = false
            statusLine.setTextColor(M3Ui.GREY)
            statusLine.text = "Fetching…"
            WidgetStore.setDefaultUsername(this, u)
            Thread {
                val ok = runCatching { IgFetch.fetchAndStore(this, u) }.getOrDefault(false)
                runOnUiThread {
                    fetchBtn.isEnabled = true
                    if (ok) {
                        statusLine.setTextColor(M3Ui.TEAL_DARK)
                        FollowerWidgetProvider.allWidgetIds(this)
                            .filter { WidgetStore.getUsername(this, it) == u }
                            .forEach { FollowerWidgetProvider.updateWidget(this, it) }
                        Toast.makeText(this, "Details updated", Toast.LENGTH_SHORT).show()
                        refreshStatus(u)
                        renderStylePreviews()
                    } else {
                        statusLine.setTextColor(Color.parseColor("#B3261E"))
                        statusLine.text = when (WebFetch.lastFailure) {
                            "login_required" -> "Session expired — log in again below."
                            "not_found" -> "Account not found — check the username."
                            "dash_nolink" -> "Your profile opened, but the dashboard link wasn't found."
                            "dash_nocount" -> "Dashboard opened but the follower count wasn't visible."
                            "dash_noload", "dash_timeout" -> "Couldn't load Instagram — check your connection and retry."
                            else -> "Couldn't reach Instagram — try again later."
                        }
                        Toast.makeText(
                            this,
                            "Instagram blocked the request — will retry automatically",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }

        // ---- instagram login ----
        root.addView(sectionLabel("Instagram login"))
        loginStatus = TextView(this).apply {
            setTextColor(M3Ui.GREY)
            sp(this, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); bottomMargin = dp(4) }
        }
        root.addView(loginStatus)
        loginBtn = compactButton("", filled = true) {}
        root.addView(loginBtn)

        refreshLoginState()
        loginBtn!!.setOnClickListener {
            if (IgSession.isLoggedIn(this)) {
                // Confirm first — the button is easy to hit by accident.
                android.app.AlertDialog.Builder(this)
                    .setTitle("Log out?")
                    .setMessage("Are you sure to logout?")
                    .setPositiveButton("Yes") { d, _ ->
                        IgSession.clear(this)
                        // wipe the WebView cookies too so the account is fully signed out
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.CookieManager.getInstance().flush()
                        refreshLoginState()
                        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                        d.dismiss()
                    }
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .create()
                    .also { dlg ->
                        dlg.setOnShowListener {
                            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                                ?.setTextColor(Color.parseColor("#C62828"))
                            dlg.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                                ?.setTextColor(Color.parseColor("#2E7D32"))
                        }
                        dlg.show()
                    }
            } else {
                startActivity(android.content.Intent(this, LoginActivity::class.java))
            }
        }

        // ---- card style ----
        root.addView(sectionLabel("Card style"))
        // Resolve the saved default theme early so the previews render
        // with the right theme on first draw.
        themeKey = when (WidgetStore.getDefaultTheme(this@MainActivity)) {
            "dark" -> "dark"
            "liquid_glass" -> "liquid_glass"
            "frosted_white" -> "frosted_white"
            "frosted_black" -> "frosted_black"
            "system" -> "system"
            "system_frosted" -> "system_frosted"
            else -> "light"
        }
        cardStyleKey = WidgetStore.getDefaultCardStyle(this@MainActivity)
        previewHolders.clear()

        val styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(styleRow)
        styleKeys.forEachIndexed { i, style ->
            val holder = android.widget.FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply {
                    if (i == 0) rightMargin = dp(6) else leftMargin = dp(6)
                }
                isClickable = true
                isFocusable = true
            }
            holder.setOnClickListener {
                cardStyleKey = style
                WidgetStore.setDefaultCardStyle(this@MainActivity, style)
                renderStylePreviews()
                // Only the default for NEW widgets is changed here — existing
                // widgets keep their own style, so both styles can live on
                // the home screen at once. (No toast: silent switch.)
            }
            previewHolders.add(holder)
            styleRow.addView(holder)
        }
        renderStylePreviews()

        // ---- card theme ----
        root.addView(sectionLabel("Card theme"))
        // themeKey already initialized above (card style previews need it).
        val themeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(M3Ui.CARD)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(themeCard)
        val radioViews = mutableListOf<ImageView>()
        fun refreshRadios() {
            themeKeys.forEachIndexed { i, (key, _) ->
                radioViews[i].setImageDrawable(M3Ui.radioDrawable(this, key == themeKey))
            }
        }
        themeKeys.forEachIndexed { index, (key, title) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
                isFocusable = true
            }
            row.addView(TextView(this).apply {
                text = title
                setTextColor(M3Ui.INK)
                sp(this, 15f)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            val radio = ImageView(this).apply {
                setImageDrawable(M3Ui.radioDrawable(this@MainActivity, key == themeKey))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            radioViews.add(radio)
            row.addView(radio)
            row.setOnClickListener {
                // Persist immediately and push to existing widgets that were
                // following the previous default theme, so the app's theme
                // picker acts as a global setting (a widget the user
                // deliberately customized keeps its own theme).
                val oldDefault = WidgetStore.getDefaultTheme(this@MainActivity)
                themeKey = key
                WidgetStore.setDefaultTheme(this@MainActivity, key)
                refreshRadios()
                renderStylePreviews()
                val ids = FollowerWidgetProvider.allWidgetIds(this@MainActivity)
                ids.forEach { id ->
                    if (WidgetStore.getTheme(this@MainActivity, id) == oldDefault) {
                        WidgetStore.setTheme(this@MainActivity, id, key)
                    }
                    FollowerWidgetProvider.updateWidget(this@MainActivity, id)
                }
                if (ids.isNotEmpty()) {
                    FollowerWidgetProvider.enqueueRefresh(this@MainActivity, ids)
                }
                Toast.makeText(this@MainActivity, "Theme updated", Toast.LENGTH_SHORT).show()
            }
            themeCard.addView(row)
            if (index < themeKeys.size - 1) {
                themeCard.addView(View(this).apply {
                    setBackgroundColor(M3Ui.DIVIDER)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { leftMargin = dp(12); rightMargin = dp(12) }
                })
            }
        }

        root.addView(compactButton("Add widget", filled = true) {
            val u = userInput.text.toString().trim().trimStart('@').lowercase()
            if (u.isEmpty()) {
                Toast.makeText(this, "Enter a username first", Toast.LENGTH_SHORT).show()
            } else {
                WidgetStore.setDefaultUsername(this, u)
                WidgetStore.setDefaultTheme(this, themeKey)
                val mgr = getSystemService(AppWidgetManager::class.java)
                if (mgr.isRequestPinAppWidgetSupported) {
                    mgr.requestPinAppWidget(
                        ComponentName(this, FollowerWidgetProvider::class.java), null, null
                    )
                } else {
                    Toast.makeText(
                        this,
                        "Long-press the home screen → Widgets → InstaW Live",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        // ---- update ----
        root.addView(sectionLabel("Update"))
        val updateBtn = compactButton("Update widgets now", filled = false) {
            // Re-render immediately (theme/count), then fetch fresh data.
            val ids = FollowerWidgetProvider.allWidgetIds(this)
            ids.forEach { FollowerWidgetProvider.updateWidget(this, it) }
            FollowerWidgetProvider.enqueueRefresh(this, ids)
            Toast.makeText(this, "Updating…", Toast.LENGTH_SHORT).show()
        }
        updateBtn.layoutParams = (updateBtn.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = dp(2)
        }
        root.addView(updateBtn)

        // ---- app theme (the app's own UI) ----
        root.addView(sectionLabel("App theme"))
        var appThemeKey = WidgetStore.getAppTheme(this)
        val appThemeKeys = listOf(
            "light" to "Light",
            "dark" to "Dark",
            "system" to "System"
        )
        val appThemeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(M3Ui.CARD)
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(appThemeCard)
        val appRadioViews = mutableListOf<ImageView>()
        fun refreshAppRadios() {
            appThemeKeys.forEachIndexed { i, (key, _) ->
                appRadioViews[i].setImageDrawable(M3Ui.radioDrawable(this, key == appThemeKey))
            }
        }
        appThemeKeys.forEachIndexed { index, (key, title) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(TextView(this).apply {
                text = title
                setTextColor(M3Ui.INK)
                sp(this, 15f)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            val radio = ImageView(this).apply {
                setImageDrawable(M3Ui.radioDrawable(this@MainActivity, key == appThemeKey))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            appRadioViews.add(radio)
            row.addView(radio)
            row.setOnClickListener {
                appThemeKey = key
                WidgetStore.setAppTheme(this@MainActivity, key)
                M3Ui.dark = WidgetStore.isAppDarkTheme(this@MainActivity)
                recreate()
            }
            appThemeCard.addView(row)
            if (index < appThemeKeys.size - 1) {
                appThemeCard.addView(View(this).apply {
                    setBackgroundColor(M3Ui.DIVIDER)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { leftMargin = dp(12); rightMargin = dp(12) }
                })
            }
        }

        // ---- how it works ----
        root.addView(sectionLabel("How it works"))
        root.addView(TextView(this).apply {
            text = "Just a username — the app fetches the follower count, name, " +
                "photo and verified badge itself, and refreshes every ~15 minutes. " +
                "Instagram blocks anonymous requests, so log in once above " +
                "(password never leaves this phone) for reliable fetching. " +
                "If a refresh is blocked, the last fetched details stay on the widget."
            setTextColor(M3Ui.GREY)
            sp(this, 13f)
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); rightMargin = dp(4) }
        })

        // ---- footer credit ----
        root.addView(View(this).apply {
            setBackgroundColor(M3Ui.DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(18); leftMargin = dp(4); rightMargin = dp(4) }
        })
        root.addView(TextView(this).apply {
            text = "This app is made by Deon"
            setTextColor(M3Ui.GREY)
            sp(this, 13f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        })
        root.addView(TextView(this).apply {
            val handle = SpannableString("@deepak.deon")
            handle.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/deepak.deon"))
                    )
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = M3Ui.TEAL
                    ds.isUnderlineText = true
                }
            }, 0, handle.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = handle
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
            sp(this, 14f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(4) }
        })
        root.addView(TextView(this).apply {
            val pi = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            text = if (pi != null) "v${pi.versionName} (${pi.versionCode})" else ""
            setTextColor(M3Ui.GREY)
            sp(this, 11f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2); bottomMargin = dp(10) }
        })

        setContentView(scroll)
    }
}
