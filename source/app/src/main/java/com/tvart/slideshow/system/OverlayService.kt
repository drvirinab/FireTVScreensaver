package com.tvart.slideshow.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.tvart.slideshow.data.Defaults
import com.tvart.slideshow.data.Settings
import com.tvart.slideshow.engine.DarkWallView
import com.tvart.slideshow.engine.SlideshowView

/**
 * Overlay engine. Draws the slideshow (or the dark wall) with TYPE_ACCESSIBILITY_OVERLAY (no SYSTEM_ALERT_WINDOW)
 * and watches remote keys without taking focus, so YouTube keeps playing and keeps receiving every key.
 *
 * Toggle key (default Menu ≡), presses counted within [Defaults.DOUBLE_PRESS_MS] of each other:
 * - double-press: hides whatever is up at once; with nothing up, shows the art after a short wait
 *   (the wait lets a third press choose the dark wall instead);
 * - triple-press: shows the dark wall (black, faint centered clock).
 * The first press passes through to YouTube; the 2nd and 3rd are consumed.
 * Back only hides (consumed); Home hides and passes through.
 */
class OverlayService : AccessibilityService() {
    private lateinit var settings: Settings
    private var view: View? = null
    private var lastToggleDown = 0L
    private var pressCount = 0
    private var swallowUp = false
    private val handler = Handler(Looper.getMainLooper())
    /** Shows the art once no third press came. Ends the sequence via lastToggleDown, so a late press counts as a new 1st. */
    private val showArtLater = Runnable { lastToggleDown = 0; show() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = Settings(this)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        instance = this
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == settings.toggleKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (swallowUp) {
                        if (event.repeatCount > 0) return true // auto-repeat of the consumed press
                        swallowUp = false // a lost UP must not eat the next real press
                    }
                    if (event.repeatCount == 0) {
                        pressCount = if (event.eventTime - lastToggleDown <= Defaults.DOUBLE_PRESS_MS) pressCount + 1 else 1
                        lastToggleDown = event.eventTime
                        when (pressCount) {
                            1 -> return false // single press belongs to YouTube
                            2 -> {
                                swallowUp = true
                                if (view != null) {
                                    // Leaving the dark wall ends the sequence; leaving the art, a 3rd press may still pick the dark wall.
                                    val wasDark = dark
                                    hide()
                                    if (wasDark) pressCount = 0
                                } else {
                                    // A little past the counting window, so a 3rd press at its edge still wins.
                                    handler.postDelayed(showArtLater, Defaults.DOUBLE_PRESS_MS + 100)
                                }
                                return true
                            }
                            else -> {
                                swallowUp = true
                                handler.removeCallbacks(showArtLater)
                                pressCount = 0
                                lastToggleDown = 0
                                showDark()
                                return true
                            }
                        }
                    }
                }
                KeyEvent.ACTION_UP -> if (swallowUp) { swallowUp = false; return true }
            }
            return false
        }
        // Back only hides the art (like the stock screensaver swallowing its wake key), so it can't
        // also navigate YouTube. Its matching UP is swallowed too. Home hides and passes through.
        // Back/Home always win: cancel art that is about to appear and end any press sequence.
        if ((code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME) && event.action == KeyEvent.ACTION_DOWN) {
            handler.removeCallbacks(showArtLater)
            pressCount = 0
            lastToggleDown = 0
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && view != null) { hide(); swallowBackUp = true; return true }
            if (event.action == KeyEvent.ACTION_DOWN && swallowBackUp) {
                if (event.repeatCount > 0) return true // auto-repeat while held
                swallowBackUp = false // a lost UP must not eat the next real press
            }
            if (event.action == KeyEvent.ACTION_UP && swallowBackUp) { swallowBackUp = false; return true }
            return false
        }
        if (view != null && event.action == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_HOME) hide()
        return false
    }

    private var swallowBackUp = false

    /** True while the dark wall (not the art) is up. */
    private var dark = false

    fun toggle() { if (view == null) show() else hide() }

    /** Shows the art slideshow. */
    fun show() {
        if (view != null) return
        val key = keyLabel(settings.toggleKey)
        addOverlay(SlideshowView(this, hint = "Press Back to return to YouTube · double-press $key to bring the art back"), isDark = false)
    }

    /** Shows the dark wall, replacing the art if it is up. No hint: it would defeat the darkness. */
    fun showDark() {
        if (view != null && dark) return
        hide()
        addOverlay(DarkWallView(this), isDark = true)
    }

    private fun addOverlay(v: View, isDark: Boolean) {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply { title = "TV Art overlay" }
        v.keepScreenOn = true
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
            view = v
            dark = isDark
        } catch (_: Exception) {}
    }

    fun hide() {
        handler.removeCallbacks(showArtLater)
        val v = view ?: return
        view = null
        dark = false
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
    }

    val isShowing get() = view != null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        hide()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** Running instance, so the settings screen can test the overlay. */
        @Volatile var instance: OverlayService? = null
            private set

        /** Short readable name of a remote key for the on-screen hint (Menu shows as its symbol). */
        fun keyLabel(code: Int): String = when (code) {
            KeyEvent.KEYCODE_MENU -> "≡"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "Rewind"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Fast-forward"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pause"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Next"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous"
            else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("MEDIA_")
                .lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}
