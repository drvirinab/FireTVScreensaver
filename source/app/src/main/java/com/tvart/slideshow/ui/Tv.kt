package com.tvart.slideshow.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Tiny remote-friendly UI kit shared by every TV screen. Sizes are in dp at TV density. */
object Tv {
    const val BG = 0xFF101418.toInt()
    const val CARD = 0xFF1C232B.toInt()
    const val ACCENT = 0xFF4FC3F7.toInt()
    const val TEXT = 0xFFECEFF1.toInt()
    const val SUBTLE = 0xFF90A4AE.toInt()
    const val WARN = 0xFFFFB74D.toInt()
    const val BAD = 0xFFE57373.toInt()

    fun dp(c: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    /** Background that highlights clearly when focused by the D-pad. */
    fun focusBg(c: Context, normal: Int = Color.TRANSPARENT, radius: Float = 8f): StateListDrawable {
        fun d(fill: Int, stroke: Int) = GradientDrawable().apply {
            cornerRadius = dp(c, radius).toFloat(); setColor(fill)
            if (stroke != 0) setStroke(dp(c, 2f), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), d(0xFF2B3A47.toInt(), ACCENT))
            addState(intArrayOf(android.R.attr.state_pressed), d(0xFF2B3A47.toInt(), ACCENT))
            addState(intArrayOf(), d(normal, 0))
        }
    }

    fun text(c: Context, s: CharSequence, sizeSp: Float = 18f, color: Int = TEXT, bold: Boolean = false) = TextView(c).apply {
        text = s; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** A vertical, scrollable list screen with a title. Add rows with the helpers below. */
    class ListScreen(val context: Context, title: String) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            val p = dp(context, 32f); setPadding(p * 2, p, p * 2, p)
        }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(body) }

        init {
            root.addView(text(context, title, 30f, TEXT, true).apply { setPadding(0, 0, 0, dp(context, 12f)) })
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        fun clear() = body.removeAllViews()

        fun header(s: String) {
            body.addView(text(context, s.uppercase(), 14f, ACCENT, true).apply {
                setPadding(dp(context, 12f), dp(context, 20f), 0, dp(context, 6f))
            })
        }

        fun note(s: CharSequence, color: Int = SUBTLE) {
            body.addView(text(context, s, 15f, color).apply { setPadding(dp(context, 12f), dp(context, 2f), dp(context, 12f), dp(context, 6f)) })
        }

        /** Colored banner; focusable when [onClick] is given. */
        fun banner(s: CharSequence, color: Int = WARN, onClick: (() -> Unit)? = null): View {
            val v = text(context, s, 17f, Color.BLACK, true).apply {
                val p = dp(context, 14f); setPadding(p, p, p, p)
                background = GradientDrawable().apply { cornerRadius = dp(context, 8f).toFloat(); setColor(color) }
                if (onClick != null) {
                    isFocusable = true
                    setOnClickListener { onClick() }
                    foreground = focusBg(context)
                }
            }
            body.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(context, 10f)
            })
            return v
        }

        /** A focusable row: title on the left, value on the right. */
        fun row(title: CharSequence, value: CharSequence? = null, sub: CharSequence? = null, onClick: (() -> Unit)? = null): Row {
            val r = Row(context, title, value, sub)
            if (onClick != null) r.view.setOnClickListener { onClick() } else r.view.isFocusable = false
            body.addView(r.view)
            return r
        }
    }

    class Row(c: Context, title: CharSequence, value: CharSequence?, sub: CharSequence?) {
        val titleView = text(c, title, 20f)
        val valueView = text(c, value ?: "", 18f, ACCENT).apply { gravity = Gravity.END }
        val subView = text(c, sub ?: "", 14f, SUBTLE).apply { visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE }
        val view = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = focusBg(c)
            val p = dp(c, 12f); setPadding(p, dp(c, 10f), p, dp(c, 10f))
            val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; addView(titleView); addView(subView) }
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueView)
        }

        fun value(v: CharSequence?) { valueView.text = v ?: "" }
        fun sub(v: CharSequence?) { subView.text = v ?: ""; subView.visibility = if (v.isNullOrEmpty()) View.GONE else View.VISIBLE }
    }

    // ---------------- dialogs ----------------

    fun choose(c: Context, title: String, items: List<String>, checked: Int = -1, onPick: (Int) -> Unit) {
        val b = AlertDialog.Builder(c).setTitle(title)
        if (checked >= 0) b.setSingleChoiceItems(items.toTypedArray(), checked) { d, i -> d.dismiss(); onPick(i) }
        else b.setItems(items.toTypedArray()) { d, i -> d.dismiss(); onPick(i) }
        b.setNegativeButton("Cancel", null).show()
    }

    fun confirm(c: Context, title: String, message: String, okLabel: String = "OK", onOk: () -> Unit) {
        AlertDialog.Builder(c).setTitle(title).setMessage(message)
            .setPositiveButton(okLabel) { _, _ -> onOk() }
            .setNegativeButton("Cancel", null).show()
    }

    fun info(c: Context, title: String, message: String) {
        AlertDialog.Builder(c).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }

    fun input(c: Context, title: String, initial: String = "", numeric: Boolean = false, onOk: (String) -> Unit) {
        val e = EditText(c).apply {
            setText(initial); setSelection(initial.length); setSingleLine()
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
        }
        val wrap = LinearLayout(c).apply { val p = dp(c, 20f); setPadding(p, p / 2, p, 0); addView(e, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(c).setTitle(title).setView(wrap)
            .setPositiveButton("OK") { _, _ -> onOk(e.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    val COLORS = listOf(
        "Red" to 0xFFC62828.toInt(), "Pink" to 0xFFE91E63.toInt(), "Purple" to 0xFF7E57C2.toInt(),
        "Blue" to 0xFF1E88E5.toInt(), "Teal" to 0xFF00897B.toInt(), "Green" to 0xFF7CB342.toInt(),
        "Amber" to 0xFFFFB300.toInt(), "Orange" to 0xFFEF6C00.toInt(), "Brown" to 0xFF6D4C41.toInt(),
        "Gray" to 0xFF607D8B.toInt(),
    )
    val ICONS = listOf("🖼", "🎄", "💘", "🌷", "✨", "☀", "🎃", "🎂", "💍", "❄", "🍂", "🌙", "⭐", "🎆", "🦃", "🐰", "🌊", "🏔")
}
