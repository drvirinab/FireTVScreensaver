package com.tvart.slideshow.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.tvart.slideshow.data.MonthDay
import com.tvart.slideshow.data.Scheduler

/**
 * Year view: one bar row per scheduled slideshow, then a "Plays" row with the winner for every day
 * (gaps = Default, overlaps show the winner), month grid and a today marker.
 */
class TimelineView(context: Context) : View(context) {
    class Lane(val label: String, val color: Int, val segments: List<Pair<MonthDay, MonthDay>>)

    var lanes: List<Lane> = emptyList()
    /** 365 ARGB colors (non-leap day index), the winner per day. */
    var winners: IntArray = IntArray(365)
    var defaultColor = 0xFF455A64.toInt()
    var todayIndex = 0
    var endExclusive = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Tv.TEXT }
    private val r = RectF()

    fun laneCount() = lanes.size + 1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowH = Tv.dp(context, ROW_DP)
        setMeasuredDimension(w, Tv.dp(context, TOP_DP) + rowH * laneCount() + Tv.dp(context, 24f))
    }

    override fun onDraw(c: Canvas) {
        val labelW = Tv.dp(context, 170f).toFloat()
        val top = Tv.dp(context, TOP_DP).toFloat()
        val rowH = Tv.dp(context, ROW_DP).toFloat()
        val gap = Tv.dp(context, 4f).toFloat()
        val x0 = labelW; val x1 = width.toFloat() - Tv.dp(context, 8f)
        val dayW = (x1 - x0) / 365f
        txt.textSize = Tv.dp(context, 16f).toFloat()

        // month grid + labels
        p.color = 0x22FFFFFF
        for (m in 1..12) {
            val x = x0 + Scheduler.dayOfYear(MonthDay(m, 1)) * dayW
            c.drawRect(x, top - 4, x + 1, top + rowH * laneCount(), p)
            txt.color = Tv.SUBTLE
            c.drawText(MonthDay.MONTHS[m - 1], x + 4, top - Tv.dp(context, 8f), txt)
        }

        // lanes
        lanes.forEachIndexed { i, lane ->
            val y = top + i * rowH
            txt.color = Tv.TEXT
            c.drawText(ellipsize(lane.label, labelW - 8), 0f, y + rowH * 0.7f, txt)
            p.color = lane.color
            for ((s, e) in lane.segments) {
                val a = Scheduler.dayOfYear(s)
                var b = Scheduler.dayOfYear(e) + if (endExclusive && s != e) 0 else 1
                if (s == e) b = a + 1
                if (b > a) bar(c, x0 + a * dayW, x0 + b * dayW, y + gap, y + rowH - gap)
                else { // wraps the new year
                    bar(c, x0 + a * dayW, x1, y + gap, y + rowH - gap)
                    bar(c, x0, x0 + b * dayW, y + gap, y + rowH - gap)
                }
            }
        }

        // effective winner row
        val y = top + lanes.size * rowH
        txt.color = Tv.ACCENT
        c.drawText("Plays", 0f, y + rowH * 0.7f, txt)
        var start = 0
        for (d in 1..365) {
            if (d == 365 || winners[d] != winners[start]) {
                p.color = winners[start]
                c.drawRect(x0 + start * dayW, y + gap, x0 + d * dayW, y + rowH - gap, p)
                start = d
            }
        }

        // today
        val tx = x0 + (todayIndex + 0.5f) * dayW
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(tx - 1.5f, top - 6, tx + 1.5f, top + rowH * laneCount() + 2, p)
        txt.color = 0xFFFFFFFF.toInt()
        val tw = txt.measureText("Today")
        val lx = if (tx + 4 + tw > width) tx - 4 - tw else tx + 4 // keep it on screen near year end
        c.drawText("Today", lx, top + rowH * laneCount() + Tv.dp(context, 18f), txt)
    }

    private fun bar(c: Canvas, l: Float, rr: Float, t: Float, b: Float) {
        r.set(l, t, rr, b)
        c.drawRoundRect(r, 4f, 4f, p)
    }

    companion object {
        private const val ROW_DP = 28f
        private const val TOP_DP = 30f
    }

    private fun ellipsize(s: String, max: Float): String {
        if (txt.measureText(s) <= max) return s
        var e = s
        while (e.isNotEmpty() && txt.measureText("$e…") > max) e = e.dropLast(1)
        return "$e…"
    }
}
