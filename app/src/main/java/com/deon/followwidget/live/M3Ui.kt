package com.deon.followwidget.live

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * Shared Material You (M3) UI building blocks, extracted from MainActivity's
 * design language so other screens (e.g. WidgetConfigActivity) can match it.
 */
object M3Ui {
    /**
     * App UI theme flag. Activities set this from WidgetStore.isAppDarkTheme()
     * in onCreate before building views; every color below adapts, so all
     * screens (main, widget config, login) follow the chosen app theme.
     */
    var dark: Boolean = false

    /**
     * App accent colour key ("teal", "green", "purple", "gold", "pink").
     * Activities set this from WidgetStore.getAccent() in onCreate before
     * building views; TEAL / TEAL_DARK / TEAL_CONTAINER follow it, so every
     * accent-tinted element (buttons, radios, toggles, links) adapts.
     */
    var accentKey: String = "teal"

    private data class Accent(
        val light: String, val dark: String,
        val lightDeep: String, val darkDeep: String,
        val lightContainer: String, val darkContainer: String
    )

    private val ACCENTS = mapOf(
        "teal" to Accent("#0E7C7B", "#5BBAB6", "#06302E", "#7FD1CC", "#BFE8E5", "#1E3A38"),
        "green" to Accent("#2E7D32", "#81C784", "#1B4D1F", "#A5D6A7", "#C8E6C9", "#1E3A24"),
        "purple" to Accent("#6A3EC9", "#B39DDB", "#3F2380", "#D1C4E9", "#D9CFF5", "#2C2145"),
        "gold" to Accent("#9C7A1A", "#D9B64A", "#5C4A10", "#E8D189", "#F0E4BC", "#3A2F14"),
        "pink" to Accent("#D84A6A", "#F48BA2", "#8C2340", "#F8BBCA", "#F9D2DC", "#451E29")
    )

    private val accent: Accent get() = ACCENTS[accentKey] ?: ACCENTS.getValue("teal")

    /** Swatch hex (light variant) for the accent picker UI. */
    fun accentSwatch(key: String): String =
        (ACCENTS[key] ?: ACCENTS.getValue("teal")).light

    val TEAL get() = Color.parseColor(if (dark) accent.dark else accent.light)
    val TEAL_DARK get() = Color.parseColor(if (dark) accent.darkDeep else accent.lightDeep)
    val TEAL_CONTAINER get() = Color.parseColor(if (dark) accent.darkContainer else accent.lightContainer)
    val TONAL_BG get() = Color.parseColor(if (dark) "#1A2B2A" else "#CDE8E5")
    val BG get() = Color.parseColor(if (dark) "#101413" else "#FBF8F5")
    val CARD get() = Color.parseColor(if (dark) "#1C2221" else "#EAF0F3")
    val INK get() = Color.parseColor(if (dark) "#E8EBEA" else "#191C1D")
    val GREY get() = Color.parseColor(if (dark) "#9BA5A3" else "#5C6066")
    val DIVIDER get() = Color.parseColor(if (dark) "#2E3534" else "#D3DCE1")
    val FIELD_OUTLINE get() = Color.parseColor(if (dark) "#6B7472" else "#7A838B")
    val TRACK_OFF get() = Color.parseColor(if (dark) "#2E3534" else "#E4E7EB")
    val FIELD_BG get() = Color.parseColor(if (dark) "#242B2A" else "#FFFFFF")
    val RADIO_OFF get() = Color.parseColor(if (dark) "#9BA5A3" else "#757B83")

    fun dp(ctx: Context, n: Int): Int = (n * ctx.resources.displayMetrics.density).toInt()

    fun sp(v: TextView, n: Float) {
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, n)
    }

    fun bold(v: TextView) {
        v.setTypeface(v.typeface, android.graphics.Typeface.BOLD)
    }

    fun radioDrawable(ctx: Context, checked: Boolean): Drawable {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(ctx, 2), if (checked) TEAL else RADIO_OFF)
            setSize(dp(ctx, 22), dp(ctx, 22))
        }
        if (!checked) return ring
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(TEAL)
            setSize(dp(ctx, 10), dp(ctx, 10))
        }
        return LayerDrawable(arrayOf(ring, dot)).apply {
            val inset = dp(ctx, 6)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    fun m3Switch(ctx: Context, initial: Boolean, onToggle: (Boolean) -> Unit): Switch =
        Switch(ctx).apply {
            isChecked = initial
            trackDrawable = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_checked),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 16).toFloat()
                        setColor(TEAL)
                        setSize(dp(ctx, 52), dp(ctx, 32))
                    }
                )
                addState(
                    intArrayOf(),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 16).toFloat()
                        setColor(TRACK_OFF)
                        setStroke(dp(ctx, 2), RADIO_OFF)
                        setSize(dp(ctx, 52), dp(ctx, 32))
                    }
                )
            }
            // default thumb is fine on the colored track
            setOnCheckedChangeListener { _, c -> onToggle(c) }
        }

    fun sectionHeader(ctx: Context, glyph: String, title: String): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 30); bottomMargin = dp(ctx, 10) }
            addView(TextView(ctx).apply {
                text = glyph
                setTextColor(Color.WHITE)
                sp(this, 19f)
                bold(this)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(TEAL)
                }
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42))
            })
            addView(TextView(ctx).apply {
                text = title
                setTextColor(INK)
                sp(this, 21f)
                bold(this)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(ctx, 14) }
            })
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, 28).toFloat()
            setColor(CARD)
        }
        setPadding(dp(ctx, 8), dp(ctx, 10), dp(ctx, 8), dp(ctx, 10))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(DIVIDER)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)
        ).apply {
            leftMargin = dp(ctx, 22); rightMargin = dp(ctx, 22)
            topMargin = dp(ctx, 6); bottomMargin = dp(ctx, 6)
        }
    }

    /** Labelled M3 outlined text field added to [parent]; returns the EditText. */
    fun labeledInput(
        ctx: Context,
        parent: LinearLayout,
        label: String,
        hintText: String
    ): EditText {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(ctx, 12); rightMargin = dp(ctx, 12)
                topMargin = dp(ctx, 8); bottomMargin = dp(ctx, 8)
            }
        }
        wrap.addView(TextView(ctx).apply {
            text = label
            setTextColor(INK)
            sp(this, 14f)
            bold(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(ctx, 6); bottomMargin = dp(ctx, 6) }
        })
        val field = EditText(ctx).apply {
            hint = hintText
            setTextColor(INK)
            sp(this, 16f)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(FIELD_BG)
                        setStroke(dp(ctx, 2), TEAL)
                    }
                )
                addState(
                    intArrayOf(),
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(ctx, 14).toFloat()
                        setColor(FIELD_BG)
                        setStroke(dp(ctx, 1), FIELD_OUTLINE)
                    }
                )
            }
            setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14))
        }
        wrap.addView(field)
        parent.addView(wrap)
        return field
    }

    fun switchRow(
        ctx: Context,
        parent: LinearLayout,
        title: String,
        subtitle: String,
        initial: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(ctx).apply {
            text = title
            setTextColor(INK)
            sp(this, 16f)
            bold(this)
        })
        texts.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(GREY)
            sp(this, 13.5f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 3) }
        })
        row.addView(texts)
        row.addView(m3Switch(ctx, initial, onToggle))
        parent.addView(row)
    }

    fun styledButton(ctx: Context, text: String, onClick: () -> Unit): Button =
        Button(ctx).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            sp(this, 15f)
            bold(this)
            minimumHeight = dp(ctx, 54)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(ctx, 27).toFloat()
                setColor(TEAL)
            }
            setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 18) }
            setOnClickListener { onClick() }
        }
}
