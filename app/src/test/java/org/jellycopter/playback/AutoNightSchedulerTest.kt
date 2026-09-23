package org.jellycopter.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class AutoNightSchedulerTest {

    @Test
    fun testMidnightRolloverQuietHours() {
        val start = LocalTime.of(22, 30)
        val end = LocalTime.of(7, 0)

        // Inside night interval before midnight
        assertTrue("22:30 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(22, 30), start, end))
        assertTrue("23:15 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(23, 15), start, end))
        assertTrue("23:59 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(23, 59), start, end))

        // Inside night interval after midnight
        assertTrue("00:00 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(0, 0), start, end))
        assertTrue("03:30 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(3, 30), start, end))
        assertTrue("06:59 should be night time", AutoNightScheduler.isNightTime(LocalTime.of(6, 59), start, end))

        // Outside night interval (day time)
        assertFalse("07:00 should be day time", AutoNightScheduler.isNightTime(LocalTime.of(7, 0), start, end))
        assertFalse("12:00 should be day time", AutoNightScheduler.isNightTime(LocalTime.of(12, 0), start, end))
        assertFalse("22:29 should be day time", AutoNightScheduler.isNightTime(LocalTime.of(22, 29), start, end))
    }

    @Test
    fun testSameDayInterval() {
        val start = LocalTime.of(9, 0)
        val end = LocalTime.of(17, 0)

        assertFalse("08:59 is outside", AutoNightScheduler.isNightTime(LocalTime.of(8, 59), start, end))
        assertTrue("09:00 is inside", AutoNightScheduler.isNightTime(LocalTime.of(9, 0), start, end))
        assertTrue("13:00 is inside", AutoNightScheduler.isNightTime(LocalTime.of(13, 0), start, end))
        assertFalse("17:00 is outside", AutoNightScheduler.isNightTime(LocalTime.of(17, 0), start, end))
    }

    @Test
    fun testAutoDisabledState() {
        val scheduler = AutoNightScheduler(
            initialAutoEnabled = false,
            initialStartTime = LocalTime.of(22, 30),
            initialEndTime = LocalTime.of(7, 0)
        )

        // Even though 23:00 is within quiet hours, autoEnabled=false disables it
        assertFalse(scheduler.evaluate(LocalTime.of(23, 0)))

        scheduler.autoEnabled = true
        assertTrue(scheduler.evaluate(LocalTime.of(23, 0)))
    }
}
