package com.tvart.slideshow.system

import android.service.dreams.DreamService
import android.view.WindowManager
import com.tvart.slideshow.engine.SlideshowView

/** Screensaver: the same SlideshowView. Not interactive, so any key press exits. */
class ArtDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        setContentView(SlideshowView(this))
    }
}
