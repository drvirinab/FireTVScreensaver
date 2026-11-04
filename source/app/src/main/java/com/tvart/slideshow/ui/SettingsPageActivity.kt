package com.tvart.slideshow.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.ArtRepo.Companion.fmtBytes
import com.tvart.slideshow.data.Settings
import com.tvart.slideshow.data.SlideshowEntity
import com.tvart.slideshow.system.SystemHooks
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sub-screens of the main settings list, chosen by [EXTRA_PAGE]: Look & timing, Screensaver & YouTube,
 * More, and the "Finish setup" checklist. Also home of the row helpers and wording shared with the other screens.
 */
class SettingsPageActivity : ComponentActivity() {
    private lateinit var repo: ArtRepo
    private lateinit var hooks: SystemHooks
    private lateinit var screen: Tv.ListScreen
    private lateinit var page: String
    private val buildLock = Mutex()
    private val s get() = repo.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ArtRepo.get(this)
        hooks = SystemHooks(this)
        page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_LOOK
        screen = Tv.ListScreen(this, when (page) {
            PAGE_SCREENSAVER -> "Screensaver & YouTube"
            PAGE_MORE -> "More"
            PAGE_SETUP -> "Finish setup"
            else -> "Look & timing"
        })
        setContentView(screen.root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() { lifecycleScope.launch { rebuild() } }

    private suspend fun rebuild() = buildLock.withLock {
        val memo = saveFocus(screen.body)
        screen.clear()
        when (page) {
            PAGE_SCREENSAVER -> buildScreensaver()
            PAGE_MORE -> buildMore()
            PAGE_SETUP -> buildSetup()
            else -> buildLook()
        }
        restoreFocus(screen.body, memo)
    }

    // ---------------- Look & timing ----------------

    private fun buildLook() {
        choiceRow("Slide time", listOf(10, 15, 20, 30, 45, 60, 80, 120, 180, 300, 600), (s.displayMs / 1000).toInt(), ::secText) { s.displayMs = it * 1000L }
        choiceRow("Transition", listOf(0, 500, 1000, 1500, 2000, 3000, 5000), s.crossfadeMs.toInt(), ::transitionText) { s.crossfadeMs = it.toLong() }
        toggleRow("Slow zoom", s.zoomEnabled) { s.zoomEnabled = it }
        choiceRow("Zoom amount", listOf(105, 110, 115, 120, 125, 130), (s.maxZoom * 100 + 0.5f).toInt(), { "${it - 100}%" }) { s.maxZoom = it / 100f }
        toggleRow("Slow drift", s.panEnabled) { s.panEnabled = it }
        choiceRow("Drift on", listOf(0, 25, 50, 75, 100), s.panPercent, ::driftText) { s.panPercent = it }
        choiceRow("Motion lasts", listOf(10, 20, 30, 45, 60), (s.motionMs / 1000).toInt(), ::secText) { s.motionMs = it * 1000L }
        choiceRow("Order", listOf(true, false), s.shuffle, { if (it) "Shuffle" else "By file name" }) { s.shuffle = it }
        choiceRow("Show favorites", listOf(1, 2, 3, 4), s.favoriteWeight, { if (it == 1) "Normal" else if (it == 2) "Twice as often" else "$it times as often" }) { s.favoriteWeight = it }
        toggleRow("Clock", s.clockOn) { s.clockOn = it }
        if (s.clockOn) choiceRow("Clock over bright art",
            listOf(Settings.CLOCK_LIKE_AMAZON, Settings.CLOCK_GLOW, Settings.CLOCK_STRONG_SHADOW), s.clockStyle,
            { when (it) { Settings.CLOCK_GLOW -> "Soft glow behind it"; Settings.CLOCK_STRONG_SHADOW -> "Stronger shadow"; else -> "Like Amazon's" } },
        ) { s.clockStyle = it }
    }

    // ---------------- Screensaver & YouTube ----------------

    private fun buildScreensaver() {
        val keyHint = if (s.toggleKey == KeyEvent.KEYCODE_MENU) "≡" else keyName(s.toggleKey)
        if (hooks.canWrite()) {
            val ss = hooks.screensaverActive()
            screen.row("Art screensaver", if (ss) "On" else "Off", "Starts after your TV's usual idle time. Off brings back Amazon's.") {
                if (ss) hooks.disableScreensaver() else hooks.enableScreensaver()
                refresh()
            }
            val ov = hooks.overlayEnabledInSettings()
            screen.row("Art over YouTube", if (ov) (if (hooks.overlayRunning()) "On" else "Starting…") else "Off",
                "Double-press $keyHint in YouTube to show or hide your art.") {
                if (ov) hooks.disableOverlay() else hooks.enableOverlay()
                screen.body.postDelayed({ refresh() }, 800)
            }
        } else {
            screen.banner("One-time setup from a computer is needed before these switches work. See INSTALL.md.", Tv.WARN)
            screen.note(hooks.grantCommand)
        }
        screen.row("Remote button", "${keyName(s.toggleKey)} (double-press)", "Back or Home also hides your art.") {
            val opts = listOf("Menu ≡ (recommended)", "Another button…")
            Tv.choose(this, "Remote button", opts, if (s.toggleKey == KeyEvent.KEYCODE_MENU) 0 else 1) {
                if (it == 0) { s.toggleKey = KeyEvent.KEYCODE_MENU; refresh() } else captureKey()
            }
        }
        screen.note("To use it: play music in YouTube, then double-press $keyHint for your art, or triple-press $keyHint for a dark wall with a faint clock.")
    }

    private fun captureKey() {
        val d = AlertDialog.Builder(this).setTitle("Remote button")
            .setMessage("Press the remote button you want to double-press to show or hide your art.\n\nCurrent: ${keyName(s.toggleKey)}\n\n(Back cancels.)")
            .setNegativeButton("Cancel", null).create()
        d.setOnKeyListener { dlg, code, ev ->
            if (ev.action != KeyEvent.ACTION_UP) return@setOnKeyListener code != KeyEvent.KEYCODE_BACK
            when (code) {
                KeyEvent.KEYCODE_BACK -> { dlg.dismiss(); true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_HOME -> true
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    Toast.makeText(this, "YouTube needs that button for playback. Try another.", Toast.LENGTH_LONG).show(); true
                }
                else -> { s.toggleKey = code; dlg.dismiss(); refresh(); true }
            }
        }
        d.show()
    }

    // ---------------- More ----------------

    private suspend fun buildMore() {
        val shows = repo.dao.slideshows()
        val storage = repo.storageInfo()
        val usb = repo.usbTvArtFolders()

        if (usb.isNotEmpty()) screen.row("Add art from USB drive", null, "Copies the TVArt folder on the drive") { importUsb(usb, shows) }

        screen.header("Storage")
        screen.row("Your art", "${picturesText(storage.artCount)} · ${fmtBytes(storage.artBytes)}")
        screen.row("Free space on TV", fmtBytes(storage.freeBytes))
        screen.row("Recently deleted", "${storage.trashCount} · ${fmtBytes(storage.trashBytes)}", "Kept 30 days, then removed") {
            startActivity(Intent(this, GalleryActivity::class.java).putExtra(GalleryActivity.EXTRA_TRASH, true))
        }
        if (storage.trashCount > 0) screen.row("Empty Recently deleted now", fmtBytes(storage.trashBytes)) {
            Tv.confirm(this, "Empty Recently deleted?", "Permanently delete ${picturesText(storage.trashCount)}. This can't be undone.", "Delete permanently") {
                lifecycleScope.launch { repo.emptyTrash(); refresh() }
            }
        }

        screen.header("Schedule")
        val defaultOpts = listOf("Art not in a season", "All my art")
        screen.row("Between seasons, show", defaultOpts[if (s.defaultUnassignedOnly) 0 else 1]) {
            Tv.choose(this, "Between seasons, show", defaultOpts, if (s.defaultUnassignedOnly) 0 else 1) { s.defaultUnassignedOnly = it == 0; repo.touch(); refresh() }
        }

        screen.header("Night")
        val nightShow = shows.firstOrNull { it.id == s.quietSlideshowId }
        toggleRow("Night slideshow", s.quietOn, "A different slideshow at night") { s.quietOn = it }
        if (s.quietOn) {
            val hours = (0..23).toList()
            choiceRow("Night starts", hours, s.quietStartHour, ::hourText) { s.quietStartHour = it }
            choiceRow("Night ends", hours, s.quietEndHour, ::hourText) { s.quietEndHour = it }
            screen.row("Night slideshow plays", nightShow?.name ?: "Not set") {
                Tv.choose(this, "Night slideshow plays", shows.map { "${it.icon} ${it.name}" }, shows.indexOf(nightShow)) {
                    s.quietSlideshowId = shows[it].id; repo.touch(); refresh()
                }
            }
        }

        screen.header("Library")
        screen.row("Check for new art", null, "Finds pictures copied onto the TV") {
            lifecycleScope.launch {
                val n = repo.rescan()
                Tv.info(this@SettingsPageActivity, "Check for new art", if (n == 0) "No new pictures found." else "${picturesText(n)} found.")
                refresh()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun importUsb(folders: List<java.io.File>, shows: List<SlideshowEntity>) {
        val go = { dir: java.io.File ->
            val targets = listOf("Don't add to a slideshow") + shows.map { "${it.icon} ${it.name}" }
            Tv.choose(this, "Add to which slideshow?", targets) { ti ->
                val target = if (ti == 0) null else shows[ti - 1].id
                val name = "USB · " + SimpleDateFormat("MMM d", Locale.US).format(Date())
                val pd = ProgressDialog(this).apply { setMessage("Adding art from USB…"); setCancelable(false); show() }
                lifecycleScope.launch {
                    // Duplicates (byte-identical files) are always skipped; a match is still added to the chosen slideshow.
                    val r = repo.importFromFolder(dir, name, target, skipDuplicates = true)
                    pd.dismiss()
                    Tv.info(this@SettingsPageActivity, "Add art from USB drive",
                        "Added ${picturesText(r.added)}. ${r.duplicates} already in your library, ${r.failed} failed.")
                    refresh()
                }
            }
        }
        if (folders.size == 1) go(folders[0])
        else Tv.choose(this, "Which folder?", folders.map { it.path }) { go(folders[it]) }
    }

    // ---------------- Finish setup ----------------

    private suspend fun buildSetup() {
        val steps = setupSteps(this, repo, hooks) { refresh() }
        if (steps.isEmpty()) screen.note("Everything is set up.")
        for (st in steps) screen.row(st.title, null, st.sub, st.fix)
    }

    // ---------------- row helpers ----------------

    private fun <T> choiceRow(title: String, options: List<T>, current: T, label: (T) -> String, set: (T) -> Unit) {
        screen.row(title, label(current)) {
            Tv.choose(this, title, options.map(label), options.indexOf(current)) { set(options[it]); repo.touch(); refresh() }
        }
    }

    private fun toggleRow(title: String, on: Boolean, sub: String? = null, set: (Boolean) -> Unit) {
        screen.row(title, if (on) "On" else "Off", sub) { set(!on); repo.touch(); refresh() }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_LOOK = "look"
        const val PAGE_SCREENSAVER = "screensaver"
        const val PAGE_MORE = "more"
        const val PAGE_SETUP = "setup"
    }
}

// ---------------- shared wording & helpers (used by Main, Slideshows, Schedule) ----------------

internal class SetupStep(val title: String, val sub: String?, val fix: (() -> Unit)?)

/** Everything the "Finish setup" checklist should list. The storage permission has its own banner on the main screen. */
internal suspend fun setupSteps(act: ComponentActivity, repo: ArtRepo, hooks: SystemHooks, refresh: () -> Unit): List<SetupStep> {
    val out = mutableListOf<SetupStep>()
    val st = repo.settings
    if (!hooks.canWrite()) {
        out += SetupStep("One-time setup from a computer is needed", "Before the art screensaver and art over YouTube switches work. See INSTALL.md. Select to see the command.") {
            Tv.info(act, "One-time setup", "On a computer with this TV connected, run:\n\n${hooks.grantCommand}\n\nThen reopen TV Art.")
        }
    } else {
        if (st.expectScreensaver && !hooks.screensaverActive())
            out += SetupStep("A Fire TV update turned off the art screensaver. Select to turn it back on.", null) { hooks.enableScreensaver(); refresh() }
        if (st.expectOverlay && !hooks.overlayEnabledInSettings())
            out += SetupStep("A Fire TV update turned off art over YouTube. Select to turn it back on.", null) { hooks.enableOverlay(); refresh() }
    }
    // With no art at all, the Gallery is a dead end: send them to the phone/PC uploader instead.
    if (repo.storageInfo().artCount == 0) {
        out += SetupStep("Add your art", "Select to turn on Add art from phone or computer") {
            com.tvart.slideshow.web.WebManagerService.start(act); act.finish()
        }
        return out
    }
    val openGallery = { act.startActivity(Intent(act, GalleryActivity::class.java)) }
    val active = repo.resolveActive()
    if (active.slideshow.kind != com.tvart.slideshow.data.SlideshowKind.DEFAULT && repo.imageCount(active.slideshow.id) == 0)
        out += SetupStep("${active.slideshow.name} is playing but has no pictures", "It shows Default instead. Select to add art in the Gallery.", openGallery)
    val next = active.nextSlideshow
    if (next != null && next.kind != com.tvart.slideshow.data.SlideshowKind.DEFAULT && repo.imageCount(next.id) == 0)
        out += SetupStep("${next.name} starts ${fmtDay(active.next!!.date.timeInMillis)} and has no pictures yet", "Select to add art in the Gallery before then.", openGallery)
    return out
}

/** Four override lengths, the same everywhere. */
internal fun chooseOverride(act: ComponentActivity, repo: ArtRepo, show: SlideshowEntity, done: () -> Unit) {
    val opts = listOf("Until the next season change", "For 1 day", "For 7 days", "Until I turn it off")
    Tv.choose(act, "Play ${show.name}…", opts) { o ->
        act.lifecycleScope.launch {
            when (o) {
                0 -> repo.setOverride(show.id, ArtRepo.OverrideMode.UNTIL_NEXT_SWITCH)
                1 -> repo.setOverride(show.id, ArtRepo.OverrideMode.DAYS, 1)
                2 -> repo.setOverride(show.id, ArtRepo.OverrideMode.DAYS, 7)
                else -> repo.setOverride(show.id, ArtRepo.OverrideMode.UNTIL_OFF)
            }
            done()
        }
    }
}

internal fun chooseOverrideShow(act: ComponentActivity, repo: ArtRepo, shows: List<SlideshowEntity>, done: () -> Unit) {
    Tv.choose(act, "Choose what plays", shows.map { "${it.icon} ${it.name}" }) { chooseOverride(act, repo, shows[it], done) }
}

/** Why the active slideshow is playing, in plain words. */
internal fun reasonText(a: ArtRepo.Active, s: Settings): String = when (a.reason) {
    ArtRepo.Reason.SCHEDULED -> "On schedule" + (a.next?.let { " · through ${lastDayText(it.date.timeInMillis)}" } ?: "")
    ArtRepo.Reason.OVERRIDE -> "You picked it · " + if (s.overrideUntil == Long.MAX_VALUE) "until you turn it off" else "through ${lastDayText(s.overrideUntil)}"
    ArtRepo.Reason.QUIET -> "Night slideshow"
    ArtRepo.Reason.DEFAULT -> "Nothing scheduled, showing Default"
}

internal fun fmtDay(ms: Long): String = SimpleDateFormat("MMM d", Locale.US).format(ms)

/** The day before [ms] (a midnight switch time), i.e. the last day something plays. Calendar math, so DST-safe. */
internal fun lastDayText(ms: Long): String =
    fmtDay(java.util.Calendar.getInstance().apply { timeInMillis = ms; add(java.util.Calendar.DAY_OF_MONTH, -1) }.timeInMillis)

internal fun hourText(h: Int) = when { h == 0 -> "12 am"; h < 12 -> "$h am"; h == 12 -> "12 pm"; else -> "${h - 12} pm" }

internal fun picturesText(n: Int) = if (n == 1) "1 picture" else "$n pictures"

internal fun secText(sec: Int): String = when {
    sec < 60 -> "$sec sec"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "${sec / 60} min ${sec % 60} sec"
}

internal fun transitionText(ms: Int): String = when {
    ms == 0 -> "None"
    ms % 1000 == 0 -> secText(ms / 1000)
    else -> "%.1f sec".format(ms / 1000f)
}

internal fun driftText(percent: Int): String = when (percent) {
    0 -> "None"
    25 -> "Some of the pictures"
    50 -> "Half the pictures"
    75 -> "Most of the pictures"
    100 -> "All the pictures"
    else -> "$percent%"
}

/** Settings-screen key name; same as the on-screen hint except Menu gets its word too. */
internal fun keyName(code: Int) =
    if (code == KeyEvent.KEYCODE_MENU) "Menu ≡" else com.tvart.slideshow.system.OverlayService.keyLabel(code)

// ---------------- focus restoration across rebuilds ----------------

internal class FocusMemo(val key: String?, val index: Int)

/** A row's identity is its title (first text inside the row); a banner's is its own text. */
private fun rowKey(v: View): String? = when (v) {
    is TextView -> v.text.toString()
    is ViewGroup -> ((v.getChildAt(0) as? ViewGroup)?.getChildAt(0) as? TextView)?.text?.toString()
    else -> null
}

internal fun saveFocus(body: ViewGroup): FocusMemo? {
    val f = body.focusedChild ?: return null
    return FocusMemo(rowKey(f), body.indexOfChild(f))
}

/** Refocus the row with the same title, else the same position, else [fallback] / the first focusable row. */
internal fun restoreFocus(body: ViewGroup, memo: FocusMemo?, fallback: View? = null) {
    val kids = (0 until body.childCount).map { body.getChildAt(it) }
    val byKey = memo?.key?.let { k -> kids.firstOrNull { it.isFocusable && rowKey(it) == k } }
    val byIndex = memo?.let { kids.getOrNull(it.index.coerceAtMost(kids.size - 1))?.takeIf { v -> v.isFocusable } }
    val target = byKey ?: byIndex ?: fallback ?: kids.firstOrNull { it.isFocusable }
    target?.requestFocus()
}
