package com.tvart.slideshow.ui

import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.tvart.slideshow.data.ArtRepo
import com.tvart.slideshow.data.MonthDay
import com.tvart.slideshow.data.RangeEntity
import com.tvart.slideshow.data.Scheduler
import com.tvart.slideshow.data.SlideshowEntity
import com.tvart.slideshow.data.SlideshowKind
import kotlinx.coroutines.launch
import java.util.Calendar

/** Timeline + yearly ranges + special days + manual override. */
class ScheduleActivity : ComponentActivity() {
    private lateinit var repo: ArtRepo
    private lateinit var screen: Tv.ListScreen
    private lateinit var timeline: TimelineView
    private var focusShowId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ArtRepo.get(this)
        focusShowId = intent.getLongExtra(EXTRA_FOCUS, -1)
        screen = Tv.ListScreen(this, "Schedule")
        timeline = TimelineView(this)
        setContentView(screen.root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() = lifecycleScope.launch {
        val memo = saveFocus(screen.body)
        screen.clear()
        build()
        // The timeline scrolls with the list (it grows with every slideshow); it isn't focusable,
        // so bring it back into view when focus reaches the first row.
        (0 until screen.body.childCount).map { screen.body.getChildAt(it) }.firstOrNull { it.isFocusable }
            ?.setOnFocusChangeListener { _, has -> if (has) screen.scroll.smoothScrollTo(0, 0) }
        restoreFocus(screen.body, memo)
    }

    private suspend fun build() {
        val st = repo.settings
        val shows = repo.dao.slideshows()
        val byId = shows.associateBy { it.id }
        val ranges = repo.dao.ranges()
        val sched = repo.schedRanges()
        val active = repo.resolveActive()
        val default = shows.first { it.kind == SlideshowKind.DEFAULT }

        // ---- timeline ----
        screen.body.addView(timeline, 0, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Tv.dp(this@ScheduleActivity, 8f) })
        val scheduledShows = shows.filter { s -> ranges.any { it.slideshowId == s.id } }
        timeline.lanes = scheduledShows.map { s ->
            TimelineView.Lane("${s.icon} ${s.name}", s.color,
                ranges.filter { it.slideshowId == s.id }.map { MonthDay(it.startMonth, it.startDay) to MonthDay(it.endMonth, it.endDay) })
        }
        val c = Calendar.getInstance().apply { set(2025, Calendar.JANUARY, 1) } // a non-leap year
        timeline.winners = IntArray(365) {
            val md = MonthDay.of(c); c.add(Calendar.DAY_OF_MONTH, 1)
            Scheduler.winner(sched, md, st.endExclusive)?.let { byId[it.slideshowId]?.color } ?: default.color
        }
        timeline.todayIndex = Scheduler.dayOfYear(MonthDay.of(Calendar.getInstance()))
        timeline.endExclusive = st.endExclusive
        timeline.requestLayout(); timeline.invalidate()

        // ---- now / next ----
        screen.header("Now")
        screen.row("${active.slideshow.icon} ${active.slideshow.name}", null, reasonText(active, st))
        active.next?.let { n ->
            screen.row("Up next", "${active.nextSlideshow?.name ?: "Default"} starts ${fmtDay(n.date.timeInMillis)}", "in ${n.daysUntil} ${if (n.daysUntil == 1) "day" else "days"}")
        }
        if (active.reason == ArtRepo.Reason.OVERRIDE) {
            screen.row("Return to schedule", null, "Go back to what the schedule plays") { repo.clearOverride(); refresh() }
        } else {
            screen.row("Choose what plays") { chooseOverrideShow(this, repo, shows) { refresh() } }
        }
        screen.note("Special days beat seasons. If two overlap, the shorter one wins.")

        // ---- ranges per slideshow ----
        for (kind in listOf(SlideshowKind.SPECIAL, SlideshowKind.SEASON, SlideshowKind.MANUAL)) {
            val group = shows.filter { it.kind == kind }
            if (group.isEmpty()) continue
            screen.header(if (kind == SlideshowKind.SPECIAL) "Special days" else if (kind == SlideshowKind.SEASON) "Seasons" else "On demand")
            for (s in group) {
                val rs = ranges.filter { it.slideshowId == s.id }
                if (rs.isEmpty()) {
                    val row = screen.row("${s.icon} ${s.name}", "Not scheduled", "Select to add dates") { editRange(s, null) }
                    if (s.id == focusShowId) row.view.post { row.view.requestFocus() }
                }
                for (r in rs) {
                    val count = repo.imageCount(s.id)
                    val row = screen.row("${s.icon} ${s.name}", rangeText(r),
                        "${Scheduler.lengthDays(schedRangeOf(r, s), st.endExclusive)} days" + if (count == 0) " · ⚠ no pictures" else "") { rangeMenu(s, r) }
                    if (s.id == focusShowId) row.view.post { row.view.requestFocus() }
                }
            }
        }
        screen.header("Add")
        screen.row("+ Add a date range to a slideshow…") {
            val opts = shows.filter { it.kind != SlideshowKind.DEFAULT }
            Tv.choose(this, "Which slideshow?", opts.map { "${it.icon} ${it.name}" }) { editRange(opts[it], null) }
        }
        screen.row("+ Add a special day…", null, "Birthday, anniversary, Christmas Day… beats the season") { addSpecialDay() }
    }

    private fun rangeText(r: RangeEntity) =
        Scheduler.rangeText(MonthDay(r.startMonth, r.startDay), MonthDay(r.endMonth, r.endDay), repo.settings.endExclusive)

    private fun schedRangeOf(r: RangeEntity, s: SlideshowEntity) =
        com.tvart.slideshow.data.SchedRange(s.id, SlideshowKind.priority(s.kind), MonthDay(r.startMonth, r.startDay), MonthDay(r.endMonth, r.endDay))

    private fun rangeMenu(s: SlideshowEntity, r: RangeEntity) {
        Tv.choose(this, "${s.name}: ${rangeText(r)}",
            listOf("Change dates", "Add another range", "Delete this range")) {
            when (it) {
                0 -> editRange(s, r)
                1 -> editRange(s, null)
                2 -> lifecycleScope.launch { repo.dao.deleteRange(r); repo.touch(); refresh() }
            }
        }
    }

    /** Pick the first and last day (shown inclusive; stored per Scheduler.storedEnd); [existing] null = new range. */
    private fun editRange(s: SlideshowEntity, existing: RangeEntity?, single: Boolean = false) {
        pickDay("${s.name}: first day", existing?.let { MonthDay(it.startMonth, it.startDay) }) { start ->
            val finish = { last: MonthDay ->
                val end = Scheduler.storedEnd(start, last, repo.settings.endExclusive)
                lifecycleScope.launch {
                    if (existing != null) repo.dao.updateRange(existing.copy(startMonth = start.month, startDay = start.day, endMonth = end.month, endDay = end.day))
                    else repo.dao.insertRange(RangeEntity(slideshowId = s.id, startMonth = start.month, startDay = start.day, endMonth = end.month, endDay = end.day))
                    repo.touch(); refresh()
                }
            }
            if (single) finish(start)
            else pickDay("${s.name}: last day",
                existing?.let { Scheduler.lastDay(MonthDay(it.startMonth, it.startDay), MonthDay(it.endMonth, it.endDay), repo.settings.endExclusive) } ?: start) { finish(it) }
        }
    }

    private fun pickDay(title: String, initial: MonthDay?, onPick: (MonthDay) -> Unit) {
        val months = MonthDay.MONTHS.toList()
        Tv.choose(this, "$title: month", months, (initial?.month ?: 1) - 1) { mi ->
            val days = (1..(if (mi == 1) 28 else MonthDay.DAYS_IN_MONTH[mi])).map { it.toString() } // no Feb 29: yearly ranges
            Tv.choose(this, "$title: day", days, ((initial?.day ?: 1) - 1).coerceIn(0, days.size - 1)) { di ->
                onPick(MonthDay(mi + 1, di + 1))
            }
        }
    }

    private fun addSpecialDay() {
        lifecycleScope.launch {
            val specials = repo.dao.slideshows().filter { it.kind == SlideshowKind.SPECIAL }
            val opts = listOf("+ New special slideshow…") + specials.map { "${it.icon} ${it.name}" }
            Tv.choose(this@ScheduleActivity, "Special day", opts) { i ->
                if (i > 0) { editRange(specials[i - 1], null, single = false); return@choose }
                Tv.input(this@ScheduleActivity, "Name (e.g. Mom's birthday)") { name ->
                    if (name.isBlank()) return@input
                    lifecycleScope.launch {
                        val id = repo.createSlideshow(name, SlideshowKind.SPECIAL, Tv.COLORS[2].second, "⭐")
                        val s = repo.dao.slideshow(id)!!
                        Tv.choose(this@ScheduleActivity, "How long?", listOf("One day", "A date range")) { k -> editRange(s, null, single = k == 0) }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_FOCUS = "focusSlideshow"
    }
}
