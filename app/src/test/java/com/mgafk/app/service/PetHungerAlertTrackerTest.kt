package com.mgafk.app.service

import com.mgafk.app.service.PetHungerAlertTracker.PetHungerAlert
import com.mgafk.app.service.PetHungerAlertTracker.PetHungerReading
import com.mgafk.app.service.PetHungerAlertTracker.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The game pushes a pet update every few seconds, so "is this pet below the threshold right
 * now" would notify continuously. The tracker turns that level into edges: one alert when a
 * pet crosses below the threshold, one when it runs out, and nothing in between.
 */
class PetHungerAlertTrackerTest {

    private val tracker = PetHungerAlertTracker()
    private val threshold = 5f

    private fun check(vararg readings: Pair<String, Float>, session: String = "s1") =
        tracker.newlyHungry(
            sessionId = session,
            readings = readings.map { PetHungerReading(it.first, it.second) },
            threshold = threshold,
        )

    @Test fun `a pet crossing below the threshold alerts once`() {
        assertEquals(emptyList<Any>(), check("pet1" to 40f))

        assertEquals(listOf(PetHungerAlert("pet1", Stage.LOW)), check("pet1" to 4f))

        // The next updates carry the same standing condition and must stay quiet.
        assertEquals(emptyList<Any>(), check("pet1" to 3f))
        assertEquals(emptyList<Any>(), check("pet1" to 2f))
    }

    @Test fun `a pet running out alerts a second and last time`() {
        check("pet1" to 4f)

        assertEquals(listOf(PetHungerAlert("pet1", Stage.EMPTY)), check("pet1" to 0f))

        // It stays at zero until it is fed, and that is not news anymore.
        assertEquals(emptyList<Any>(), check("pet1" to 0f))
        assertEquals(emptyList<Any>(), check("pet1" to 0f))
    }

    @Test fun `a pet dropping straight to zero alerts once, at zero`() {
        assertEquals(emptyList<Any>(), check("pet1" to 40f))

        // One update, one alert: the crossing and the emptying are the same event here.
        assertEquals(listOf(PetHungerAlert("pet1", Stage.EMPTY)), check("pet1" to 0f))
    }

    @Test fun `feeding a pet back above the threshold arms it again`() {
        check("pet1" to 4f)
        check("pet1" to 0f)

        assertEquals(emptyList<Any>(), check("pet1" to 80f))

        // A new hunger cycle gets its own two alerts.
        assertEquals(listOf(PetHungerAlert("pet1", Stage.LOW)), check("pet1" to 4f))
        assertEquals(listOf(PetHungerAlert("pet1", Stage.EMPTY)), check("pet1" to 0f))
    }

    @Test fun `each pet is tracked on its own`() {
        assertEquals(listOf(PetHungerAlert("pet1", Stage.LOW)), check("pet1" to 4f, "pet2" to 40f))

        assertEquals(listOf(PetHungerAlert("pet2", Stage.LOW)), check("pet1" to 3f, "pet2" to 4f))
    }

    /**
     * The regression that made this spam: one notifier serves every session, and each session's
     * update carries only its own pets. Session state must not be reset by a sibling's update.
     */
    @Test fun `a session update leaves the other sessions alone`() {
        assertEquals(listOf(PetHungerAlert("pet1", Stage.LOW)), check("pet1" to 4f, session = "s1"))
        assertEquals(listOf(PetHungerAlert("pet2", Stage.LOW)), check("pet2" to 4f, session = "s2"))

        assertEquals(emptyList<Any>(), check("pet1" to 4f, session = "s1"))
        assertEquals(emptyList<Any>(), check("pet2" to 4f, session = "s2"))
    }

    /**
     * A pet can leave the list without being fed (stored in the hutch, sold). Dropping its
     * state on absence would re-alert the moment it comes back still hungry.
     */
    @Test fun `a pet missing from an update keeps its state`() {
        assertEquals(
            listOf(PetHungerAlert("pet1", Stage.LOW), PetHungerAlert("pet2", Stage.LOW)),
            check("pet1" to 4f, "pet2" to 4f),
        )

        // pet1 sits out this update, so nothing is known about it - not "it is fine now".
        assertEquals(emptyList<Any>(), check("pet2" to 3f))
        assertEquals(emptyList<Any>(), check("pet1" to 3f, "pet2" to 3f))
    }
}
