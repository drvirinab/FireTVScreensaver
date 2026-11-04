package com.tvart.slideshow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.system.SystemHooks
import com.tvart.slideshow.web.WebManagerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The app's tile in Your Apps: what's playing, plus the gallery, slideshows, schedule and a few sub-screens. */
class MainActivity : ComponentActivity() {
    private lateinit var repo: ArtRepo
    private lateinit var hooks: SystemHooks
    private lateinit var screen: Tv.ListScreen
    private val buildLock = Mutex()
    private val s get() = repo.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ArtRepo.get(this)
        hooks = SystemHooks(this)
        screen = Tv.ListScreen(this, "TV Art")
        setContentView(screen.root)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            rebuild()
            // First scan of a big folder hashes every file; show the screen first, then refresh counts.
            if (hasStorage()) try { if (repo.rescan() > 0) rebuild() } catch (_: Exception) {}
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        focusPlay = hasStorage()
        lifecycleScope.launch { if (hasStorage()) repo.rescan(); rebuild() }
    }

    /** After the storage card disappears, land on Play rather than whatever took its position. */
    private var focusPlay = false

    private fun hasStorage() = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun refresh() { lifecycleScope.launch { rebuild() } }

    private suspend fun rebuild() = buildLock.withLock {
        val memo = saveFocus(screen.body)
        screen.clear()
        val active = repo.resolveActive()
        val shows = repo.dao.slideshows()
        val storage = repo.storageInfo()
        var primary: View? = null

        // ---------- banners (only when something is wrong) ----------
        if (!hasStorage()) {
            primary = screen.banner("TV Art needs to read pictures on this TV. Select to allow.", Tv.BAD) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }
        val steps = setupSteps(this, repo, hooks) { refresh() }.size
        if (steps > 0) screen.banner("Finish setup ($steps ${if (steps == 1) "step" else "steps"})", Tv.WARN) {
            startActivity(Intent(this, SettingsPageActivity::class.java).putExtra(SettingsPageActivity.EXTRA_PAGE, SettingsPageActivity.PAGE_SETUP))
        }

        // ---------- playing ----------
        val play = screen.row("Play slideshow", "${active.slideshow.icon} ${active.slideshow.name}", "${reasonText(active, s)}. Back to stop.") {
            startActivity(Intent(this, PreviewActivity::class.java))
        }
        if (primary == null) primary = play.view
        if (active.reason == ArtRepo.Reason.OVERRIDE) {
            screen.row("Return to schedule", null, "Go back to what the schedule plays") { repo.clearOverride(); refresh() }
        } else {
            screen.row("Choose what plays") { chooseOverrideShow(this, repo, shows) { refresh() } }
        }

        // ---------- library & schedule ----------
        screen.row("Gallery", picturesText(storage.artCount)) { open(GalleryActivity::class.java) }
        screen.row("Slideshows", "${shows.size}") { open(SlideshowsActivity::class.java) }
        val nextText = active.next?.let { "Next: ${active.nextSlideshow?.name ?: "Default"}, ${fmtDay(it.date.timeInMillis)}" }
        screen.row("Schedule", nextText) { open(ScheduleActivity::class.java) }

        // ---------- add art from phone or computer ----------
        val webOn = WebManagerService.isRunning
        screen.row("Add art from phone or computer", if (webOn) "On" else "Off", "Turns off by itself after 30 minutes") {
            if (webOn) WebManagerService.stop(this) else WebManagerService.start(this)
            screen.body.postDelayed({ refresh() }, 600)
        }
        if (webOn) {
            val fullUrl = WebManagerService.url(this)
            val url = fullUrl?.removePrefix("http://")
            if (url != null) {
                screen.row("On your phone, go to $url", "PIN ${s.webPin}")
                qrBlock(fullUrl)?.let { screen.body.addView(it) }
            } else screen.row("On your phone, go to", "No network")
            screen.row("Change PIN") { s.webPin = s.newPin(); refresh() }
        }

        // ---------- sub-screens ----------
        screen.row("Look & timing ›") { openPage(SettingsPageActivity.PAGE_LOOK) }
        screen.row("Screensaver & YouTube ›") { openPage(SettingsPageActivity.PAGE_SCREENSAVER) }
        screen.row("More ›") { openPage(SettingsPageActivity.PAGE_MORE) }

        // Keep the user's place; otherwise start on the storage prompt (first run) or Play.
        if (focusPlay) { focusPlay = false; restoreFocus(screen.body, null, play.view) }
        else restoreFocus(screen.body, memo, primary)
    }

    /** QR code for [url] with a caption; dark modules on white with a quiet zone so phone cameras read it on a dark screen. */
    private fun qrBlock(url: String): View? = try {
        val px = Tv.dp(this, 220f)
        val m = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 4))
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
        for (y in 0 until m.height) for (x in 0 until m.width) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = Tv.dp(this@MainActivity, 12f); setPadding(p, p / 2, p, p)
            addView(ImageView(this@MainActivity).apply { setImageBitmap(bmp) }, LinearLayout.LayoutParams(px, px))
            addView(Tv.text(this@MainActivity, "Or scan this with your phone's camera,\nthen enter the PIN.", 16f, Tv.SUBTLE).apply {
                setPadding(Tv.dp(this@MainActivity, 16f), 0, 0, 0)
            })
        }
    } catch (_: Exception) { null }

    private fun open(c: Class<*>) = startActivity(Intent(this, c))

    private fun openPage(page: String) =
        startActivity(Intent(this, SettingsPageActivity::class.java).putExtra(SettingsPageActivity.EXTRA_PAGE, page))
}
