package com.mgafk.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One notifier serves every session, and a weather change is reported by each of them, so the
 * same change must only alert once. The catch is that the guard has to remember every weather
 * it sees, whether or not that weather had an alert configured.
 */
class WeatherAlertTrackerTest {

    private val tracker = WeatherAlertTracker()

    @Test fun `a change to a new weather is worth alerting`() {
        assertTrue(tracker.isNewWeather("Rain", previousWeather = "Clear Skies"))
    }

    @Test fun `the same change reported by a second session is not`() {
        tracker.isNewWeather("Rain", previousWeather = "Clear Skies")

        assertFalse(tracker.isNewWeather("Rain", previousWeather = "Clear Skies"))
    }

    @Test fun `an update that did not change the weather is not`() {
        assertFalse(tracker.isNewWeather("Rain", previousWeather = "Rain"))
    }

    @Test fun `an empty weather is not`() {
        assertFalse(tracker.isNewWeather("", previousWeather = "Rain"))
    }

    /**
     * The bug this guards. Amber Moon returns every few hours, and the weathers in between are
     * usually ones the player has no alert for. A guard that only remembered the weathers it
     * actually alerted on stayed pinned to "Amber Moon" for good, so the first one alerted and
     * every one after it was silently swallowed.
     */
    @Test fun `a weather that comes back alerts again`() {
        assertTrue(tracker.isNewWeather("Amber Moon", previousWeather = "Clear Skies"))
        assertTrue(tracker.isNewWeather("Clear Skies", previousWeather = "Amber Moon"))

        assertTrue(
            "coming back hours later is a new event",
            tracker.isNewWeather("Amber Moon", previousWeather = "Clear Skies"),
        )
    }

    /** The same, with a weather nobody would enable in between: it must still be recorded. */
    @Test fun `an uninteresting weather in between still counts as seen`() {
        assertTrue(tracker.isNewWeather("Amber Moon", previousWeather = "Clear Skies"))
        assertTrue(tracker.isNewWeather("Rain", previousWeather = "Amber Moon"))
        assertFalse("Rain has not changed since", tracker.isNewWeather("Rain", previousWeather = "Amber Moon"))
        assertTrue(tracker.isNewWeather("Amber Moon", previousWeather = "Rain"))
    }
}
