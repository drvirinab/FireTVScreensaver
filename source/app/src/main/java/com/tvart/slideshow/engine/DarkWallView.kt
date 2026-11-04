package com.tvart.slideshow.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * "Dark wall": a black screen with the usual date and time, large and centered, barely visible.
 * Same formats and fonts as [ClockView]; sizes are on a 1920x1080 canvas and scaled.
 */
class DarkWallView(context: Context) : View(context) {
    private companion object {
        const val REF_H = 1080f
        /** 10% white. */
        const val TEXT_COLOR = 0x1AFFFFFF
        const val DATE_CAP = 64f
        const val TIME_CAP = 280f
        const val GAP = 60f
        /** Burn-in insurance: the block moves within this many px, once an hour. */
        const val DRIFT = 40f
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textAlign = Paint.Align.CENTER; color = TEXT_COLOR }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.light; textAlign = Paint.Align.CENTER; color = TEXT_COLOR }
    private val bounds = Rect()
    private var dateText = ""
    private var timeText = ""
    private var hour = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = update()
    }

    init {
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
    }

    private fun update() {
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

    private fun sizeByCap(p: Paint, probe: String, capPx: Float) {
        p.textSize = 100f
        p.getTextBounds(probe, 0, probe.length, bounds)
        p.textSize = 100f * capPx / bounds.height().coerceAtLeast(1)
    }

    override fun onDraw(canvas: Canvas) {
        if (height == 0) return
        val k = height / REF_H
        sizeByCap(datePaint, "H", DATE_CAP * k)
        sizeByCap(timePaint, "0", TIME_CAP * k)
        val dx = ((hour % 3) - 1) * DRIFT * k
        val dy = (((hour / 3) % 3) - 1) * DRIFT * k
        // Center the date + time block on screen.
        val blockH = (DATE_CAP + GAP + TIME_CAP) * k
        val top = (height - blockH) / 2f + dy
        val x = width / 2f + dx
        val dateBase = top + DATE_CAP * k
        canvas.drawText(dateText, x, dateBase, datePaint)
        canvas.drawText(timeText, x, dateBase + GAP * k + TIME_CAP * k, timePaint)
    }
}
