package com.tvart.slideshow

import com.tvart.slideshow.data.MonthDay
import com.tvart.slideshow.data.SchedRange
import com.tvart.slideshow.data.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SchedulerTest {
    private fun r(id: Long, p: Int, sm: Int, sd: Int, em: Int, ed: Int) =
        SchedRange(id, p, MonthDay(sm, sd), MonthDay(em, ed))

    private val CHRISTMAS = 1L; private val VAL = 2L; private val SPRING = 3L
    private val ANIME = 4L; private val SUMMER = 5L; private val HALLOWEEN = 6L; private val BDAY = 7L

    private val seasons = listOf(
        r(CHRISTMAS, 1, 11, 8, 1, 8),
        r(VAL, 1, 1, 8, 3, 1),
        r(SPRING, 1, 3, 1, 6, 1),
        r(ANIME, 1, 6, 1, 7, 8),
        r(SUMMER, 1, 7, 8, 9, 1),
        r(HALLOWEEN, 1, 9, 1, 11, 8),
    )

    private fun w(m: Int, d: Int, list: List<SchedRange> = seasons, excl: Boolean = true) =
        Scheduler.winner(list, MonthDay(m, d), excl)?.slideshowId

    @Test fun seasonsCoverYearWithEndExclusive() {
        assertEquals(HALLOWEEN, w(11, 7))
        assertEquals(CHRISTMAS, w(11, 8))
        assertEquals(CHRISTMAS, w(12, 31))
        assertEquals(CHRISTMAS, w(1, 1))
        assertEquals(CHRISTMAS, w(1, 7))
        assertEquals(VAL, w(1, 8))
        assertEquals(VAL, w(2, 29))
        assertEquals(SPRING, w(3, 1))
        assertEquals(ANIME, w(6, 1))
        assertEquals(SUMMER, w(7, 8))
        assertEquals(HALLOWEEN, w(9, 1))
    }

    @Test fun endInclusiveShorterRangeWinsOnOverlapDay() {
        // On Nov 8 both Halloween (Sep 1..Nov 8, 69 days) and Christmas (Nov 8..Jan 8, 62 days) match; Christmas is shorter.
        assertEquals(CHRISTMAS, w(11, 8, excl = false))
        assertEquals(HALLOWEEN, w(11, 7, excl = false))
    }

    @Test fun specialDayBeatsSeasonAndGapFallsToDefault() {
        val list = seasons + r(BDAY, 2, 10, 29, 10, 29)
        assertEquals(BDAY, w(10, 29, list))
        assertEquals(HALLOWEEN, w(10, 30, list))
        assertNull(w(5, 5, listOf(r(CHRISTMAS, 1, 11, 8, 1, 8))))
    }

    @Test fun nextSwitchFromOct29() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 29, 15, 0) }
        val s = Scheduler.nextSwitch(seasons, c, true)!!
        assertEquals(CHRISTMAS, s.slideshowId)
        assertEquals(10, s.daysUntil)
        assertEquals(Calendar.NOVEMBER, s.date.get(Calendar.MONTH))
        assertEquals(8, s.date.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun countdownToChristmasSpecialDay() {
        val list = seasons + r(99, 2, 12, 25, 12, 25)
        val c = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 29, 9, 0) }
        assertEquals(57, Scheduler.nextStartOf(99, list, c, true))
    }

    @Test fun lengths() {
        assertEquals(61, Scheduler.lengthDays(seasons[0], true)) // Nov 8 -> Jan 8
        assertEquals(1, Scheduler.lengthDays(r(1, 2, 12, 25, 12, 25), true))
    }

    @Test fun lastDayDisplayAndStorageRoundTrip() {
        val sep1 = MonthDay(9, 1); val nov8 = MonthDay(11, 8)
        assertEquals("Sep 1 – Nov 7", Scheduler.rangeText(sep1, nov8, true))
        assertEquals("Nov 8 – Jan 7", Scheduler.rangeText(nov8, MonthDay(1, 8), true))
        assertEquals("Dec 25", Scheduler.rangeText(MonthDay(12, 25), MonthDay(12, 25), true))
        assertEquals(nov8, Scheduler.storedEnd(sep1, MonthDay(11, 7), true))
        assertEquals(MonthDay(1, 1), Scheduler.storedEnd(MonthDay(11, 8), MonthDay(12, 31), true))
        assertEquals(MonthDay(3, 1), Scheduler.storedEnd(MonthDay(1, 8), MonthDay(2, 28), true))
        assertEquals(MonthDay(12, 25), Scheduler.storedEnd(MonthDay(12, 25), MonthDay(12, 25), true))
        assertEquals(MonthDay(2, 28), Scheduler.lastDay(MonthDay(1, 8), MonthDay(3, 1), true))
    }

    @Test fun hourWindow() {
        assert(Scheduler.inHourWindow(23, 22, 7))
        assert(Scheduler.inHourWindow(3, 22, 7))
        assert(!Scheduler.inHourWindow(7, 22, 7))
        assert(!Scheduler.inHourWindow(12, 22, 7))
    }
}
