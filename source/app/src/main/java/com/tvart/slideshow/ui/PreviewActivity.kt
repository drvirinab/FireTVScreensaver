package com.tvart.slideshow.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.tvart.slideshow.engine.SlideshowView

/**
 * Full-screen preview. With EXTRA_IMAGE_ID it shows one image exactly as the slideshow would
 * (crop + zoom + pan); without it, it plays the active slideshow. Back exits.
 */
class PreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val id = intent.getLongExtra(EXTRA_IMAGE_ID, -1L)
        setContentView(SlideshowView(this, if (id >= 0) id else null))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    companion object {
        const val EXTRA_IMAGE_ID = "imageId"
    }
}
