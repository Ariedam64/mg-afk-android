package com.mgafk.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Auto-stock runs on every inventory change, which the game sends constantly. A move the server
 * refuses leaves the item exactly where it was, so the next change asks for the same move again,
 * and the app ends up hammering the server with a request that will never succeed.
 *
 * This tries each move once. A move only comes round again when the item has left the inventory
 * and come back, which is the one case where retrying can mean something new.
 */
class AutoStockTrackerTest {

    private val tracker = AutoStockTracker()

    private val session = "s1"

    @Test fun `a move is worth sending the first time`() {
        assertEquals(listOf("SeedSilo:Carrot"), tracker.pending(session, listOf("SeedSilo:Carrot")))
    }

    /** The heart of it: the same unfinished move is not sent twice. */
    @Test fun `the same move is not sent again while it is still pending`() {
        tracker.pending(session, listOf("SeedSilo:Carrot"))

        assertEquals(emptyList<String>(), tracker.pending(session, listOf("SeedSilo:Carrot")))
        assertEquals(emptyList<String>(), tracker.pending(session, listOf("SeedSilo:Carrot")))
    }

    @Test fun `a new move alongside a stuck one still goes out`() {
        tracker.pending(session, listOf("SeedSilo:Carrot"))

        assertEquals(
            listOf("ToolShack:WateringCan"),
            tracker.pending(session, listOf("SeedSilo:Carrot", "ToolShack:WateringCan")),
        )
    }

    /**
     * A move that succeeds takes the item out of the inventory, so it stops being a candidate.
     * Should the item come back, that is a new situation and worth another attempt.
     */
    @Test fun `a move is tried again once the item has come and gone`() {
        tracker.pending(session, listOf("SeedSilo:Carrot"))
        tracker.pending(session, emptyList())

        assertEquals(listOf("SeedSilo:Carrot"), tracker.pending(session, listOf("SeedSilo:Carrot")))
    }

    @Test fun `sessions do not hold each other back`() {
        tracker.pending(session, listOf("SeedSilo:Carrot"))

        assertEquals(listOf("SeedSilo:Carrot"), tracker.pending("s2", listOf("SeedSilo:Carrot")))
    }

    /** A reconnect rebuilds the state from scratch, so what was stuck deserves another go. */
    @Test fun `forgetting a session clears what it had tried`() {
        tracker.pending(session, listOf("SeedSilo:Carrot"))
        tracker.forget(session)

        assertEquals(listOf("SeedSilo:Carrot"), tracker.pending(session, listOf("SeedSilo:Carrot")))
    }

    @Test fun `nothing to move is nothing to send`() {
        assertEquals(emptyList<String>(), tracker.pending(session, emptyList()))
    }
}
