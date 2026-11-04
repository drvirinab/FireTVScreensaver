package com.tvart.slideshow.data

import java.util.Calendar

/** Month/day pair, month 1..12. */
data class MonthDay(val month: Int, val day: Int) : Comparable<MonthDay> {
    val key get() = month * 100 + day
    override fun compareTo(other: MonthDay) = key.compareTo(other.key)
    override fun toString() = "${MONTHS[month - 1]} $day"

    companion object {
        val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val DAYS_IN_MONTH = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        fun of(c: Calendar) = MonthDay(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}

data class SchedRange(val slideshowId: Long, val priority: Int, val start: MonthDay, val end: MonthDay)

/** Pure scheduling logic (no Android), unit-tested in SchedulerTest. */
object Scheduler {
    private val CUM = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    /** Day of a non-leap year, 0..364 (Feb 29 shares Feb 28's slot). */
    fun dayOfYear(md: MonthDay) = CUM[md.month - 1] + minOf(md.day, if (md.month == 2) 28 else 31) - 1

    fun contains(r: SchedRange, d: MonthDay, endExclusive: Boolean): Boolean {
        val s = r.start.key; val e = r.end.key; val k = d.key
        if (s == e) return k == s // a single special day
        return if (s < e) {
            if (endExclusive) k in s until e else k in s..e
        } else { // wraps the new year
            if (endExclusive) k >= s || k < e else k >= s || k <= e
        }
    }

    fun lengthDays(r: SchedRange, endExclusive: Boolean): Int {
        if (r.start == r.end) return 1
        val n = (dayOfYear(r.end) - dayOfYear(r.start) + 365) % 365
        return if (endExclusive) n else n + 1
    }

    /** Highest priority wins; ties go to the shorter range; then lowest id for stability. */
    fun winner(ranges: List<SchedRange>, d: MonthDay, endExclusive: Boolean): SchedRange? =
        ranges.filter { contains(it, d, endExclusive) }
            .minWithOrNull(compareByDescending<SchedRange> { it.priority }
                .thenBy { lengthDays(it, endExclusive) }
                .thenBy { it.slideshowId })

    data class Switch(val date: Calendar, val slideshowId: Long?, val daysUntil: Int)

    /** First day after [today] whose winner differs from today's (null id = Default). */
    fun nextSwitch(ranges: List<SchedRange>, today: Calendar, endExclusive: Boolean): Switch? {
        val todayId = winner(ranges, MonthDay.of(today), endExclusive)?.slideshowId
        val c = today.clone() as Calendar
        startOfDay(c)
        for (i in 1..370) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            val id = winner(ranges, MonthDay.of(c), endExclusive)?.slideshowId
            if (id != todayId) return Switch(c.clone() as Calendar, id, i)
        }
        return null
    }

    /** Next date (today included) on which [slideshowId] starts winning; for countdowns. */
    fun nextStartOf(slideshowId: Long, ranges: List<SchedRange>, today: Calendar, endExclusive: Boolean): Int? {
        val c = today.clone() as Calendar
        startOfDay(c)
        var prev = winner(ranges, MonthDay.of(c), endExclusive)?.slideshowId
        if (prev == slideshowId) return 0
        for (i in 1..370) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            val id = winner(ranges, MonthDay.of(c), endExclusive)?.slideshowId
            if (id == slideshowId && prev != slideshowId) return i
            prev = id
        }
        return null
    }

    /** [md] moved by [days] within a non-leap year, wrapping (Feb 29 behaves like Feb 28). */
    fun plusDays(md: MonthDay, days: Int): MonthDay {
        val d = ((dayOfYear(md) + days) % 365 + 365) % 365
        val m = CUM.indexOfLast { it <= d }
        return MonthDay(m + 1, d - CUM[m] + 1)
    }

    /** The last day a range plays, for display and picking (stored ends are exclusive by default). */
    fun lastDay(start: MonthDay, storedEnd: MonthDay, endExclusive: Boolean): MonthDay =
        if (start == storedEnd || !endExclusive) storedEnd else plusDays(storedEnd, -1)

    /** Inverse of [lastDay]: what to store for a range the user picked as [start]..[last]. */
    fun storedEnd(start: MonthDay, last: MonthDay, endExclusive: Boolean): MonthDay {
        if (last == start || !endExclusive) return last
        val e = plusDays(last, 1)
        return if (e == start) last else e // a full-year range can't be stored exclusively; lose one day
    }

    /** "Sep 1 – Nov 7", or "Dec 25" for a single day. */
    fun rangeText(start: MonthDay, storedEnd: MonthDay, endExclusive: Boolean): String {
        val last = lastDay(start, storedEnd, endExclusive)
        return if (last == start) "$start" else "$start – $last"
    }

    fun startOfDay(c: Calendar) {
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    }

    /** Hour window that may wrap midnight, e.g. 22 -> 7. */
    fun inHourWindow(hour: Int, start: Int, end: Int) =
        if (start == end) false else if (start < end) hour in start until end else hour >= start || hour < end
}
