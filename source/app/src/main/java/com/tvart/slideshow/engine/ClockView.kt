package com.tvart.slideshow.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import com.tvart.slideshow.data.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date + time like the Fire TV screensaver clock, measured from a photo of the stock screensaver
 * on the target TV. Values are on a 1920x1080 canvas and scaled; text is sized by its actual cap/digit
 * height so it matches whatever the font metrics are. Not affected by zoom or crossfade.
 */
class ClockView(context: Context) : View(context) {
    private companion object {
        const val REF_H = 1080f
        const val DATE_CAP = 26f      // cap height of "FRI, 20 JUN"
        const val TIME_CAP = 78f      // digit height of "5:09"
        const val COUNTDOWN_CAP = 22f
        const val RIGHT_MARGIN = 117f // both lines right-aligned here
        const val TOP = 57f           // top of the date's capitals
        const val GAP = 27f           // date baseline to top of the time digits
        const val COUNTDOWN_GAP = 24f
        const val GLOW_RADIUS = 520f
        /** Burn-in insurance: the whole clock shifts within this many px, once an hour. */
        const val DRIFT = 2f
    }

    private val settings = Settings(context)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textAlign = Paint.Align.RIGHT }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.light; textAlign = Paint.Align.RIGHT }
    private val cdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textAlign = Paint.Align.RIGHT }
    private val glowPaint = Paint()
    private val bounds = Rect()
    private var dateText = ""
    private var timeText = ""
    private var hour = 0
    var countdown: String? = null
        set(v) { field = v; invalidate() }
    var tint: Int = Color.WHITE
        set(v) { field = v; invalidate() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = update()
    }

    fun update() {
        val now = Date()
        val tz = TimeZone.getDefault()
        dateText = SimpleDateFormat("EEE, d MMM", Locale.US).apply { timeZone = tz }.format(now).uppercase(Locale.US)
        timeText = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = tz }.format(now)
        hour = Calendar.getInstance(tz).get(Calendar.HOUR_OF_DAY)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        update()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    /** Sets [p]'s text size so the glyph [probe] is [capPx] tall. */
    private fun sizeByCap(p: Paint, probe: String, capPx: Float) {
        p.textSize = 100f
        p.getTextBounds(probe, 0, probe.length, bounds)
        p.textSize = 100f * capPx / bounds.height().coerceAtLeast(1)
    }

    override fun onDraw(canvas: Canvas) {
        if (height == 0) return
        val k = height / REF_H
        val style = settings.clockStyle
        val (shadowR, shadowA) = if (style == Settings.CLOCK_STRONG_SHADOW) 12f to 0xB3 else 6f to 0x80
        for (p in listOf(datePaint, timePaint, cdPaint)) {
            p.color = tint
            p.setShadowLayer(shadowR * k, 0f, 2f * k, shadowA shl 24)
        }
        sizeByCap(datePaint, "H", DATE_CAP * k)
        sizeByCap(timePaint, "0", TIME_CAP * k)
        sizeByCap(cdPaint, "H", COUNTDOWN_CAP * k)

        // Hourly 1–2 px drift in a 3x3 pattern; invisible, but no pixel holds the clock for days.
        val dx = ((hour % 3) - 1) * DRIFT * k
        val dy = (((hour / 3) % 3) - 1) * DRIFT * k
        val x = width - RIGHT_MARGIN * k + dx
        val dateBase = TOP * k + DATE_CAP * k + dy
        val timeBase = dateBase + GAP * k + TIME_CAP * k
        val cdBase = timeBase + COUNTDOWN_GAP * k + COUNTDOWN_CAP * k

        if (style == Settings.CLOCK_GLOW) {
            val cx = x - timePaint.measureText(timeText) / 2f
            val cy = (TOP * k + dy + timeBase) / 2f
            val r = GLOW_RADIUS * k
            glowPaint.shader = RadialGradient(cx, cy, r, 0x47000000, 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, glowPaint)
        }

        canvas.drawText(dateText, x, dateBase, datePaint)
        canvas.drawText(timeText, x, timeBase, timePaint)
        countdown?.let { canvas.drawText(it, x, cdBase, cdPaint) }
    }
}
