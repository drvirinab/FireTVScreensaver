package com.tvart.slideshow.engine

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/**
 * Draws the current slide (with zoom/pan) and, during a dissolve, the outgoing one beneath it.
 * Redraws every frame only while something moves; a held image costs nothing.
 */
class ArtView(context: Context) : View(context) {
    class Slide(val bitmap: Bitmap, val motion: Motion, val motionMs: Long) {
        var startAt = 0L
    }

    private var current: Slide? = null
    private var outgoing: Slide? = null
    private var fadeStart = 0L
    private var fadeMs = 1L
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val animator = TimeAnimator().apply { setTimeListener { _, _, _ -> invalidate() } }

    init {
        setBackgroundColor(Color.BLACK)
    }

    /** Crossfades to [slide] over [crossfadeMs]. The previous outgoing bitmap (if any) is released. */
    fun show(slide: Slide, crossfadeMs: Long) {
        outgoing?.let { release(it) }
        outgoing = current
        current = slide
        val now = SystemClock.uptimeMillis()
        slide.startAt = now
        fadeStart = now
        fadeMs = crossfadeMs.coerceAtLeast(1)
        if (!animator.isStarted) animator.start()
        invalidate()
    }

    fun clear() {
        animator.cancel()
        outgoing?.let { release(it) }; outgoing = null
        current?.let { release(it) }; current = null
        invalidate()
    }

    private fun release(s: Slide) {
        // Let the render thread finish any frame that still references it.
        postDelayed({ if (!s.bitmap.isRecycled) s.bitmap.recycle() }, 500)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val cur = current ?: return
        val fade = ((now - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
        val out = outgoing
        if (out != null && fade < 1f) {
            drawSlide(canvas, out, now, 255)
        }
        drawSlide(canvas, cur, now, (fade * 255).toInt())
        if (fade >= 1f && out != null) {
            outgoing = null
            release(out)
        }
        val moving = cur.motion.animates && now - cur.startAt < cur.motionMs
        if (fade >= 1f && !moving) animator.end()
    }

    private fun drawSlide(canvas: Canvas, s: Slide, now: Long, alpha: Int) {
        if (s.bitmap.isRecycled) return
        val p = if (s.motionMs <= 0) 1f else (now - s.startAt).toFloat() / s.motionMs
        s.motion.matrixAt(p, matrix)
        paint.alpha = alpha
        canvas.drawBitmap(s.bitmap, matrix, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clear()
    }
}
