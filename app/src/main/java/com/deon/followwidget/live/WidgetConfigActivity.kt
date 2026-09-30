package com.deon.followwidget.live

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.InputType
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

/**
 * Launched when a widget is added to the home screen. Just a username and
 * a card theme — the follower count, name, photo and verified badge are
 * fetched from Instagram automatically in the background.
 *
 * Compact Material You design, matching the main settings screen.
 * After adding, the user is taken to the home screen to see the widget.
 */
class WidgetConfigActivity : Activity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun sp(v: TextView, n: Float) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, n)
    }
    private fun bold(v: TextView) {
        v.setTypeface(v.typeface, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        M3Ui.dark = WidgetStore.isAppDarkTheme(this)
        window.statusBarColor = M3Ui.BG

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED)

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val scroll = ScrollView(ctx).apply {
            addView(root)
            setBackgroundColor(M3Ui.BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- compact header ----
        root.addView(TextView(ctx).apply {
            text = "Add widget"
            setTextColor(M3Ui.INK)
            sp(this, 22f)
            bold(this)
        })
        root.addView(TextView(ctx).apply {
            text = "Pick a username and theme — details fetch automatically."
            setTextColor(M3Ui.GREY)
            sp(this, 13f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })

        // ---- widget details ----
        root.addView(TextView(ctx).apply {
            text = "WIDGET DETAILS"
            setTextColor(M3Ui.TEAL)
            sp(this, 13f)
            bold(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); bottomMargin = dp(6) }
        })
        root.addView(TextView(ctx).apply {
            text = "Username"
            setTextColor(M3Ui.INK)
            sp(this, 12f)
            bold(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(4); bottomMargin = dp(3) }
        })
        val usernameInput = EditText(ctx).apply {
            hint = "deepak.deon"
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
            setText(WidgetStore.getDefaultUsername(ctx))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        root.addView(usernameInput)

        var verifiedChecked = WidgetStore.isVerified(ctx, usernameInput.text.toString())
        val switchRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val switchTexts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        switchTexts.addView(TextView(ctx).apply {
            text = "Verified badge"
            setTextColor(M3Ui.INK)
            sp(this, 15f)
            bold(this)
        })
        switchTexts.addView(TextView(ctx).apply {
            text = "Show the blue check next to your name"
            setTextColor(M3Ui.GREY)
            sp(this, 12f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        })
        switchRow.addView(switchTexts)
        switchRow.addView(M3Ui.m3Switch(ctx, verifiedChecked) { verifiedChecked = it })
        root.addView(switchRow)

        // ---- card theme ----
        root.addView(TextView(ctx).apply {
            text = "CARD THEME"
            setTextColor(M3Ui.TEAL)
            sp(this, 13f)
            bold(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); bottomMargin = dp(6) }
        })
        val themeOptions = listOf(
            "light" to "Light",
            "dark" to "Dark",
            "liquid_glass" to "Liquid Glass",
            "liquid_glass_dark" to "Liquid Glass Dark",
            "frosted_white" to "Frosted White",
            "frosted_black" to "Frosted Black",
            "system" to "System",
            "system_frosted" to "System (Frosted)",
            "system_liquid" to "System (Liquid)"
        )
        var selectedTheme = WidgetStore.getDefaultTheme(ctx)
        val themeCard = LinearLayout(ctx).apply {
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
            themeOptions.forEachIndexed { i, (key, _) ->
                radioViews[i].setImageDrawable(M3Ui.radioDrawable(ctx, key == selectedTheme))
            }
        }
        themeOptions.forEachIndexed { index, (key, title) ->
            val row = LinearLayout(ctx).apply {
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
            row.addView(TextView(ctx).apply {
                text = title
                setTextColor(M3Ui.INK)
                sp(this, 15f)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            val radio = ImageView(ctx).apply {
                setImageDrawable(M3Ui.radioDrawable(ctx, key == selectedTheme))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            radioViews.add(radio)
            row.addView(radio)
            row.setOnClickListener {
                selectedTheme = key
                refreshRadios()
            }
            themeCard.addView(row)
            if (index < themeOptions.size - 1) {
                themeCard.addView(View(ctx).apply {
                    setBackgroundColor(M3Ui.DIVIDER)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { leftMargin = dp(12); rightMargin = dp(12) }
                })
            }
        }

        // ---- update button ----
        root.addView(Button(ctx).apply {
            text = "Update widget"
            isAllCaps = false
            setTextColor(Color.WHITE)
            sp(this, 14f)
            bold(this)
            minimumHeight = dp(44)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(M3Ui.TEAL)
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                val username = usernameInput.text.toString().trim().trimStart('@').lowercase()
                if (username.isEmpty()) {
                    Toast.makeText(ctx, "Enter a username", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                WidgetStore.setUsername(ctx, appWidgetId, username)
                WidgetStore.setTheme(ctx, appWidgetId, selectedTheme)
                WidgetStore.setDefaultUsername(ctx, username)
                WidgetStore.setDefaultTheme(ctx, selectedTheme)
                WidgetStore.setVerified(ctx, username, verifiedChecked)

                FollowerWidgetProvider.schedulePeriodic(ctx)
                // Fetch in the background; the widget populates when done.
                Thread {
                    runCatching { IgFetch.fetchAndStore(ctx, username) }
                    FollowerWidgetProvider.updateWidget(ctx, appWidgetId)
                }.start()

                val result =
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, result)
                Toast.makeText(ctx, "Fetching details…", Toast.LENGTH_SHORT).show()
                // Take the user straight to the home screen to see the new widget.
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                startActivity(home)
                finish()
            }
        })

        setContentView(scroll)
    }
}
