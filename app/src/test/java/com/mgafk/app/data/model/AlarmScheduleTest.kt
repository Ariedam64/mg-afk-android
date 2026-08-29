package com.mgafk.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** ISO days: 1=Mon .. 7=Sun. The dates below are a real week: 2026-08-24 is a Monday. */
class AlarmScheduleTest {

    private val monday = 24
    private val saturday = 29
    private val sunday = 30

    private fun at(dayOfMonth: Int, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDateTime.of(2026, 8, dayOfMonth, hour, minute)

    private fun window(
        start: Int,
        end: Int,
        days: Set<Int>,
        enabled: Boolean = true,
    ) = AlarmSchedule(
        startMinute = start * 60,
        endMinute = end * 60,
        activeDays = days,
        enabled = enabled,
    )

    // ── Single window ──

    @Test fun disabledWindow_neverSilences() {
        val w = window(22, 23, setOf(1), enabled = false)
        assertFalse(w.isSilentAt(at(monday, 22, 30)))
    }

    @Test fun sameDayWindow_silencesInsideOnly() {
        val w = window(9, 17, setOf(1))
        assertFalse(w.isSilentAt(at(monday, 8, 59)))
        assertTrue(w.isSilentAt(at(monday, 9)))
        assertTrue(w.isSilentAt(at(monday, 16, 59)))
        assertFalse(w.isSilentAt(at(monday, 17)))
    }

    @Test fun overnightWindow_carriesIntoTheNextMorning() {
        // Sat 01:00 -> 10:00 does not wrap; Sat 22:00 -> Sun 06:00 does.
        val w = window(22, 6, setOf(6))
        assertTrue(w.isSilentAt(at(saturday, 23)))
        assertTrue(w.isSilentAt(at(sunday, 5, 59)))
        assertFalse(w.isSilentAt(at(sunday, 6)))
        // Sunday night is not covered - only Saturday is an active start day.
        assertFalse(w.isSilentAt(at(sunday, 23)))
    }

    @Test fun windowWithNoActiveDays_neverSilences() {
        val w = window(0, 23, emptySet())
        assertFalse(w.isSilentAt(at(monday, 12)))
    }

    // ── Several windows ──

    @Test fun emptyList_neverSilences() {
        assertFalse(emptyList<AlarmSchedule>().isSilentAt(at(monday, 3)))
    }

    @Test fun windowsWithDifferentHours_eachApplyToTheirOwnDays() {
        // The case a single window cannot express: work nights vs weekend lie-in.
        val workNights = window(22, 6, setOf(1, 2, 3, 4))
        val weekendSleep = window(1, 10, setOf(6, 7))
        val schedules = listOf(workNights, weekendSleep)

        assertTrue(schedules.isSilentAt(at(monday, 23)))      // work night
        assertFalse(schedules.isSilentAt(at(monday, 9)))      // awake on a work day
        assertTrue(schedules.isSilentAt(at(saturday, 9)))     // weekend lie-in
        assertFalse(schedules.isSilentAt(at(saturday, 23)))   // no work-night rule on Saturday
    }

    @Test fun oneEnabledWindowIsEnough() {
        val off = window(0, 23, setOf(1), enabled = false)
        val on = window(9, 17, setOf(1))
        assertTrue(listOf(off, on).isSilentAt(at(monday, 12)))
        assertFalse(listOf(off).isSilentAt(at(monday, 12)))
    }

    // ── Migration off the pre-multi-window setting ──

    @Test fun migration_carriesTheOldSingleWindowOver() {
        val legacy = window(22, 7, setOf(1, 2, 3))
        val migrated = AppSettings(alarmSchedule = legacy).migrated()

        assertEquals(1, migrated.alarmSchedules.size)
        val carried = migrated.alarmSchedules.first()
        assertEquals(legacy.startMinute, carried.startMinute)
        assertEquals(legacy.endMinute, carried.endMinute)
        assertEquals(legacy.activeDays, carried.activeDays)
        assertTrue(carried.enabled)
        assertEquals("Schedule 1", carried.label)
        assertNull(migrated.alarmSchedule)
    }

    @Test fun migration_doesNotOverwriteWindowsTheUserAlreadyHas() {
        val existing = window(9, 17, setOf(5))
        val migrated = AppSettings(
            alarmSchedules = listOf(existing),
            alarmSchedule = window(22, 7, setOf(1)),
        ).migrated()

        assertEquals(listOf(existing), migrated.alarmSchedules)
        assertNull(migrated.alarmSchedule)
    }

    @Test fun migration_isANoOpOnSettingsWithoutTheOldField() {
        val settings = AppSettings(alarmSchedules = listOf(window(9, 17, setOf(5))))
        assertEquals(settings, settings.migrated())
    }
}
