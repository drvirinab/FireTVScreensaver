package com.tvart.slideshow.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.MonthDay
import com.tvart.slideshow.data.SlideshowEntity
import com.tvart.slideshow.data.SlideshowKind
import kotlinx.coroutines.launch

/** Slideshow list, or (with EXTRA_ID) the editor for one slideshow. */
class SlideshowsActivity : ComponentActivity() {
    private lateinit var repo: ArtRepo
    private lateinit var screen: Tv.ListScreen
    private var editId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ArtRepo.get(this)
        editId = intent.getLongExtra(EXTRA_ID, -1)
        screen = Tv.ListScreen(this, if (editId >= 0) "Edit slideshow" else "Slideshows")
        setContentView(screen.root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() = lifecycleScope.launch {
        val memo = saveFocus(screen.body)
        screen.clear()
        if (editId >= 0) buildEditor() else buildList()
        restoreFocus(screen.body, memo)
    }

    private fun kindLabel(kind: Int) = if (kind == SlideshowKind.MANUAL) "On demand" else SlideshowKind.label(kind)

    private val kindOptions = listOf("Season: plays on its dates every year", "Special day: beats the season on its dates", "On demand: plays only when you choose it")

    // ---------------- list ----------------

    private suspend fun buildList() {
        val active = repo.resolveActive()
        val shows = repo.dao.slideshows()
        val ranges = repo.dao.ranges().groupBy { it.slideshowId }
        screen.row("+ Create slideshow") { create() }
        for (kind in listOf(SlideshowKind.DEFAULT, SlideshowKind.SEASON, SlideshowKind.SPECIAL, SlideshowKind.MANUAL)) {
            val group = shows.filter { it.kind == kind }
            if (group.isEmpty()) continue
            screen.header(when (kind) { SlideshowKind.DEFAULT -> "Default"; SlideshowKind.SEASON -> "Seasons"; SlideshowKind.SPECIAL -> "Special days"; else -> "On demand" })
            for (s in group) {
                val n = repo.imageCount(s.id)
                val when_ = if (s.kind == SlideshowKind.DEFAULT) "Plays between seasons and as the fallback"
                else ranges[s.id].orEmpty().joinToString(", ") { com.tvart.slideshow.data.Scheduler.rangeText(MonthDay(it.startMonth, it.startDay), MonthDay(it.endMonth, it.endDay), repo.settings.endExclusive) }.ifEmpty { "Not scheduled" }
                val playing = if (s.id == active.slideshow.id) "  ▶ playing" else ""
                screen.row("${s.icon} ${s.name}$playing", picturesText(n), when_ + if (n == 0) "  ·  ⚠ no pictures" else "") {
                    startActivity(Intent(this, SlideshowsActivity::class.java).putExtra(EXTRA_ID, s.id))
                }
            }
        }
    }

    private fun create() {
        Tv.input(this, "New slideshow name") { name ->
            if (name.isBlank()) return@input
            Tv.choose(this, "Type", kindOptions) { k ->
                val kind = listOf(SlideshowKind.SEASON, SlideshowKind.SPECIAL, SlideshowKind.MANUAL)[k]
                lifecycleScope.launch {
                    val c = Tv.COLORS[(repo.dao.slideshows().size) % Tv.COLORS.size].second
                    val id = repo.createSlideshow(name, kind, c, if (kind == SlideshowKind.SPECIAL) "⭐" else "🖼")
                    startActivity(Intent(this@SlideshowsActivity, SlideshowsActivity::class.java).putExtra(EXTRA_ID, id))
                }
            }
        }
    }

    // ---------------- editor ----------------

    private suspend fun buildEditor() {
        val s = repo.dao.slideshow(editId) ?: run { finish(); return }
        val st = repo.settings
        val n = repo.imageCount(s.id)
        val isDefault = s.kind == SlideshowKind.DEFAULT

        screen.header("${s.icon} ${s.name}")
        screen.note("${picturesText(n)} · ${kindLabel(s.kind)}" + if (n == 0) " · ⚠ add pictures in the Gallery before its dates" else "")
        screen.row("Name", s.name) { Tv.input(this, "Rename", s.name) { if (it.isNotBlank()) save(s.copy(name = it)) } }
        screen.row("Icon", s.icon) { Tv.choose(this, "Icon", Tv.ICONS, Tv.ICONS.indexOf(s.icon)) { save(s.copy(icon = Tv.ICONS[it])) } }
        screen.row("Color", Tv.COLORS.firstOrNull { it.second == s.color }?.first ?: "Custom") {
            Tv.choose(this, "Color", Tv.COLORS.map { it.first }, Tv.COLORS.indexOfFirst { it.second == s.color }) { save(s.copy(color = Tv.COLORS[it].second)) }
        }
        if (!isDefault) {
            val kinds = listOf(SlideshowKind.SEASON, SlideshowKind.SPECIAL, SlideshowKind.MANUAL)
            screen.row("Type", kindLabel(s.kind), "Special days beat seasons. Seasons beat Default.") {
                Tv.choose(this, "Type", kindOptions, kinds.indexOf(s.kind)) { save(s.copy(kind = kinds[it])) }
            }
            screen.row("Dates", null, "Choose the days it plays each year") { startActivity(Intent(this, ScheduleActivity::class.java).putExtra(ScheduleActivity.EXTRA_FOCUS, s.id)) }
        }
        screen.row("Play now…") { chooseOverride(this, repo, s) { refresh() } }

        screen.header("Settings just for ${s.name}")
        val same = "Same as others"
        val secs = listOf<Int?>(null, 10, 15, 20, 30, 45, 60, 80, 120, 180, 300, 600)
        screen.row("Slide time", s.displaySec?.let { secText(it) } ?: "$same (${secText((st.displayMs / 1000).toInt())})") {
            Tv.choose(this, "Slide time", secs.map { it?.let { v -> secText(v) } ?: same }, secs.indexOf(s.displaySec)) { save(s.copy(displaySec = secs[it])) }
        }
        val pans = listOf<Int?>(null, 0, 25, 50, 75, 100)
        screen.row("Drift on", s.panPercent?.let { driftText(it) } ?: "$same (${driftText(st.panPercent)})") {
            Tv.choose(this, "Drift on", pans.map { it?.let { v -> driftText(v) } ?: same }, pans.indexOf(s.panPercent)) { save(s.copy(panPercent = pans[it])) }
        }
        val clocks = listOf<Boolean?>(null, true, false)
        screen.row("Clock", when (s.clockOn) { null -> "$same (${if (st.clockOn) "On" else "Off"})"; true -> "On"; false -> "Off" }) {
            Tv.choose(this, "Clock", listOf(same, "On", "Off"), clocks.indexOf(s.clockOn)) { save(s.copy(clockOn = clocks[it])) }
        }
        val tints = listOf<Pair<String, Int?>>("White" to null, "Warm white" to 0xFFFFE9C7.toInt(), "Cool white" to 0xFFDDEBFF.toInt(),
            "Soft red" to 0xFFFFCDD2.toInt(), "Soft green" to 0xFFDCEDC8.toInt(), "Soft orange" to 0xFFFFE0B2.toInt(), "Soft pink" to 0xFFF8BBD0.toInt())
        screen.row("Clock tint", tints.firstOrNull { it.second == s.clockTint }?.first ?: "Custom") {
            Tv.choose(this, "Clock tint", tints.map { it.first }, tints.indexOfFirst { it.second == s.clockTint }) { save(s.copy(clockTint = tints[it].second)) }
        }
        if (!isDefault) screen.row("Count down to ${s.name}", if (s.countdownOn) "On" else "Off",
            "Shows '10 days until ${s.name}' under the clock") {
            save(s.copy(countdownOn = !s.countdownOn))
        }

        if (!isDefault) {
            screen.row("Delete slideshow", null, "Your pictures stay in the library") {
                Tv.confirm(this, "Delete ${s.name}?", "The slideshow and its dates are removed. Your pictures are kept.", "Delete") {
                    lifecycleScope.launch { repo.deleteSlideshow(s); finish() }
                }
            }
        }
    }

    private fun save(s: SlideshowEntity) = lifecycleScope.launch { repo.updateSlideshow(s); refresh() }

    companion object {
        const val EXTRA_ID = "slideshowId"
    }
}
