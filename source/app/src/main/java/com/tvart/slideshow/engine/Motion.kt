package com.tvart.slideshow.engine

import android.graphics.Matrix
import kotlin.math.max
import kotlin.random.Random

/**
 * Zoom-out + pan plan for one slide, replicating the Fire TV "Dissolve" screensaver.
 * All math is in screen pixels; [bmpW]/[bmpH] are the decoded bitmap size, [srcW]/[srcH] the original image size.
 */
class Motion(
    private val screenW: Int, private val screenH: Int,
    private val bmpW: Int, private val bmpH: Int,
    srcW: Int, srcH: Int,
    zoomEnabled: Boolean, maxZoom: Float, minZoom: Float,
    pan: Boolean, rnd: Random,
) {
    /** Cover scale for the decoded bitmap. */
    private val s0 = max(screenW.toFloat() / bmpW, screenH.toFloat() / bmpH)
    /** Starting zoom, never upscaling past 1 screen px per original image px. */
    val startZoom: Float
    private val startOx: Float
    private val startOy: Float

    init {
        val s0src = max(screenW.toFloat() / srcW, screenH.toFloat() / srcH)
        var z = if (zoomEnabled) minOf(maxZoom, 1f / s0src) else 1f
        if (z < minZoom) z = 1f
        startZoom = z
        // Overflow at zoom 1 along the axis the center-crop cuts.
        val overX = bmpW * s0 - screenW
        val overY = bmpH * s0 - screenH
        var ox = 0f; var oy = 0f
        if (pan) {
            val sign = if (rnd.nextBoolean()) 1f else -1f
            if (overX > 1f && overX >= overY) ox = sign * overX / 2f
            else if (overY > 1f) oy = sign * overY / 2f
        }
        startOx = ox; startOy = oy
    }

    val animates get() = startZoom > 1f || startOx != 0f || startOy != 0f

    /** Fills [m] for progress [p] in 0..1 through the motion window (ease-out), then holds. */
    fun matrixAt(p: Float, m: Matrix) {
        val t = p.coerceIn(0f, 1f)
        val e = 1f - (1f - t) * (1f - t) * (1f - t) // cubic ease-out
        val z = startZoom + (1f - startZoom) * e
        val ox = startOx * (1f - e)
        val oy = startOy * (1f - e)
        val s = s0 * z
        m.setScale(s, s)
        m.postTranslate(screenW / 2f + ox - bmpW * s / 2f, screenH / 2f + oy - bmpH * s / 2f)
    }
}
