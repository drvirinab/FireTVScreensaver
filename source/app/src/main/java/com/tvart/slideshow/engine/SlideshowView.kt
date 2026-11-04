package com.tvart.slideshow.engine

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.Defaults
import com.tvart.slideshow.data.ImageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The one slideshow component, shared by the overlay, the screensaver and previews.
 * It resolves the active slideshow at every image change, so schedule switches and
 * overrides apply live at the next crossfade.
 *
 * @param fixedImageId when set, shows only this image (gallery preview of the crop + zoom).
 * @param hint when set, a pill with this text fades in at the bottom on attach, holds, then fades away
 *   (used by the overlay to tell a guest how to get back to YouTube).
 */
class SlideshowView(
    context: Context,
    private val fixedImageId: Long? = null,
    private val hint: String? = null,
) : FrameLayout(context) {
    private val repo = ArtRepo.get(context)
    private val settings = repo.settings
    private val art = ArtView(context)
    private val clock = ClockView(context)
    private val message = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 0f)
        gravity = Gravity.CENTER
        visibility = GONE
    }
    private var hintView: TextView? = null
    private var scope: CoroutineScope? = null
    private val rnd = Random(SystemClock.uptimeMillis())

    init {
        setBackgroundColor(Color.BLACK)
        addView(art, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(clock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        keepScreenOn = true
    }

    /** Bottom-center pill (1920x1080 values scaled by height): 34px bold, white on 65% black, 40px above the 5% safe area. */
    private fun startHint(text: String, h: Int) {
        val k = h / 1080f
        val pill = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 34f * k)
            typeface = Typeface.create(Fonts.regular, Typeface.BOLD)
            setSingleLine()
            val ph = (30f * k).toInt(); val pv = (14f * k).toInt()
            setPadding(ph, pv, ph, pv)
            background = GradientDrawable().apply {
                setColor(0xA6000000.toInt())
                cornerRadius = 60f * k
            }
            alpha = 0f
        }
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = (h * 0.05f + 40f * k).toInt()
        addView(pill, lp)
        hintView = pill
        pill.animate().alpha(1f).setDuration(500).withEndAction {
            pill.animate().alpha(0f).setStartDelay(5_000).setDuration(1_000).withEndAction {
                pill.visibility = GONE
            }.start()
        }.start()
    }

    private class Prepared(val slide: ArtView.Slide, val displayMs: Long, val clockOn: Boolean, val tint: Int, val countdown: String?)

    private fun screenSize(): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        return maxOf(dm.widthPixels, dm.heightPixels) to minOf(dm.widthPixels, dm.heightPixels)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        val scan: Job = s.launch { try { repo.rescan() } catch (_: Exception) {} }
        s.launch { loop(scan) }
        if (hint != null && hintView == null) startHint(hint, screenSize().second)
    }

    override fun onDetachedFromWindow() {
        scope?.cancel(); scope = null
        hintView?.let { it.animate().cancel(); removeView(it) }
        hintView = null
        art.clear()
        super.onDetachedFromWindow()
    }

    private suspend fun loop(scan: Job) {
        val (w, h) = screenSize()
        var next: Prepared? = null
        var first = true
        while (scope?.isActive == true) {
            val p = next ?: prepare(w, h)
            next = null
            if (p == null) {
                showMessage(if (scan.isActive) "Getting your art ready…" else EMPTY_TEXT)
                delay(if (scan.isActive) 2_000 else 15_000)
                continue
            }
            hideMessage()
            p.slide.bitmap.prepareToDraw()
            art.show(p.slide, if (first) 700 else settings.crossfadeMs)
            clock.visibility = if (p.clockOn) VISIBLE else GONE
            clock.tint = p.tint
            clock.countdown = p.countdown
            first = false

            val shownAt = SystemClock.uptimeMillis()
            // Decode the next image a little before the crossfade so at most two bitmaps are alive.
            val lead = minOf(LEAD_MS, p.displayMs / 2)
            delay((p.displayMs - lead).coerceAtLeast(0))
            next = prepare(w, h)
            delay((shownAt + p.displayMs - SystemClock.uptimeMillis()).coerceAtLeast(0))
        }
    }

    private suspend fun prepare(w: Int, h: Int): Prepared? = withContext(Dispatchers.IO) {
        try {
            if (fixedImageId != null) {
                val img = repo.dao.image(fixedImageId) ?: return@withContext null
                return@withContext build(img, w, h, settings.displayMs, settings.clockOn, null, null, panPercent = 100)
            }
            val active = repo.resolveActive()
            var show = active.slideshow
            var images = repo.imagesFor(show.id)
            if (images.isEmpty()) {
                show = repo.dao.defaultSlideshow() ?: return@withContext null
                images = repo.imagesFor(show.id)
            }
            if (images.isEmpty()) return@withContext null
            val displayMs = show.displaySec?.let { it * 1000L } ?: settings.displayMs
            val clockOn = show.clockOn ?: settings.clockOn
            val tint = show.clockTint
            val countdown = repo.countdownText()
            val panPct = show.panPercent ?: settings.panPercent
            repeat(3) {
                val img = ShuffleBag.next(show.id, images, settings.shuffle, settings.favoriteWeight) ?: return@withContext null
                build(img, w, h, displayMs, clockOn, tint, countdown, panPct)?.let { return@withContext it }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun build(img: ImageEntity, w: Int, h: Int, displayMs: Long, clockOn: Boolean, tint: Int?, countdown: String?, panPercent: Int): Prepared? {
        val maxZoom = settings.maxZoom.coerceAtLeast(1f)
        val bmp = Decode.forScreen(repo.fileOf(img), w, h, maxOf(maxZoom, 1f)) ?: return null
        val pan = settings.panEnabled && rnd.nextInt(100) < panPercent
        val motion = Motion(w, h, bmp.width, bmp.height, img.width, img.height,
            settings.zoomEnabled, maxZoom, Defaults.MIN_ZOOM_TO_ANIMATE, pan, rnd)
        val motionMs = minOf(settings.motionMs, displayMs)
        return Prepared(ArtView.Slide(bmp, motion, motionMs), displayMs, clockOn, tint ?: Color.WHITE, countdown)
    }

    private fun showMessage(text: String) {
        art.clear()
        message.setTextSize(TypedValue.COMPLEX_UNIT_PX, height / 1080f * 40f)
        message.text = text
        message.visibility = VISIBLE
    }

    private fun hideMessage() { message.visibility = GONE }

    companion object {
        const val LEAD_MS = 8_000L
        const val EMPTY_TEXT = "No art yet.\n\nOpen TV Art on this TV and choose Add art from phone or computer."
    }
}
