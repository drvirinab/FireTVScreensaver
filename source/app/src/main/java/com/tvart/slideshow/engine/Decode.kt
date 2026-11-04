package com.tvart.slideshow.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Memory-safe decoding. Never loads a full-resolution bitmap. */
object Decode {
    data class Bounds(val width: Int, val height: Int, val rotation: Int)

    /** Oriented size (EXIF rotation applied), or null if not a decodable image. */
    fun bounds(file: File): Bounds? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val rot = rotation(file)
        return if (rot == 90 || rot == 270) Bounds(o.outHeight, o.outWidth, rot) else Bounds(o.outWidth, o.outHeight, rot)
    }

    private fun rotation(file: File): Int = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

    /**
     * Decodes so the bitmap is at most [headroom] x the size needed to center-crop-fill [screenW]x[screenH].
     * Returns null on failure (corrupt file, OOM).
     */
    fun forScreen(file: File, screenW: Int, screenH: Int, headroom: Float): Bitmap? {
        val b = bounds(file) ?: return null
        val cover = max(screenW.toFloat() / b.width, screenH.toFloat() / b.height)
        // Target (oriented) size: cover * headroom, but never larger than the source.
        val scale = minOf(1f, cover * headroom)
        val targetW = (b.width * scale).roundToInt().coerceAtLeast(1)
        return decodeScaled(file, b, targetW)
    }

    /** Thumbnail, center-cropped to exactly [w]x[h]. */
    fun thumbnail(file: File, w: Int = 320, h: Int = 180): Bitmap? {
        val b = bounds(file) ?: return null
        val cover = max(w.toFloat() / b.width, h.toFloat() / b.height)
        val src = decodeScaled(file, b, (b.width * minOf(1f, cover)).roundToInt().coerceAtLeast(1)) ?: return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val m = Matrix().apply {
            setScale(s, s)
            postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }

    /** Decode to roughly [targetW] wide (oriented), using inSampleSize + density scaling in one pass. */
    private fun decodeScaled(file: File, b: Bounds, targetW: Int): Bitmap? {
        val rawW = if (b.rotation == 90 || b.rotation == 270) b.height else b.width
        val rawTargetW = if (b.rotation == 90 || b.rotation == 270) (targetW.toLong() * b.height / b.width).toInt() else targetW
        var sample = 1
        while (rawW / (sample * 2) >= rawTargetW) sample *= 2
        val o = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            val sampledW = rawW / sample
            if (sampledW > rawTargetW * 1.02f) {
                inScaled = true
                inDensity = sampledW
                inTargetDensity = rawTargetW
            }
        }
        val bmp = try { BitmapFactory.decodeFile(file.path, o) } catch (_: OutOfMemoryError) { null } ?: return null
        if (b.rotation == 0) return bmp
        return try {
            val m = Matrix().apply { postRotate(b.rotation.toFloat()) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
        } catch (_: OutOfMemoryError) { bmp.recycle(); null }
    }

    @Suppress("unused")
    fun rect(w: Int, h: Int) = Rect(0, 0, w, h)
}
