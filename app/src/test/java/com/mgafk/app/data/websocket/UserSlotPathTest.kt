package com.mgafk.app.data.websocket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deciding whether a patch touched our own player slot.
 *
 * The ability log is read out of that slot, and the app only went looking when a patch named the
 * log explicitly. Captured traffic says the server never does: it replaces the whole slot every
 * frame, as a remove and an add on the slot itself, with the new log entries inside. So the app
 * never looked, and the log stayed empty however much the pets did.
 */
class UserSlotPathTest {

    @Test fun `the slot itself counts`() {
        assertTrue(touchesUserSlot("/child/data/userSlots/0", 0))
    }

    @Test fun `anything inside the slot counts`() {
        assertTrue(touchesUserSlot("/child/data/userSlots/0/data/activityLogs", 0))
        assertTrue(touchesUserSlot("/child/data/userSlots/0/data/activityLogs/-", 0))
        assertTrue(touchesUserSlot("/child/data/userSlots/0/data/inventory/items/3", 0))
    }

    @Test fun `another player's slot does not`() {
        assertFalse(touchesUserSlot("/child/data/userSlots/1", 0))
        assertFalse(touchesUserSlot("/child/data/userSlots/2/data/activityLogs", 0))
    }

    /**
     * The trap in matching on a prefix: slot 1 and slot 10 share their first characters, so a
     * plain startsWith would have every frame of a ten player room look like ours.
     */
    @Test fun `a slot whose number merely starts the same does not`() {
        assertFalse(touchesUserSlot("/child/data/userSlots/10", 1))
        assertFalse(touchesUserSlot("/child/data/userSlots/10/data/activityLogs", 1))
        assertFalse(touchesUserSlot("/child/data/userSlots/23", 2))
    }

    @Test fun `paths elsewhere in the tree do not`() {
        assertFalse(touchesUserSlot("/child/data/shops/seed/inventory/2", 0))
        assertFalse(touchesUserSlot("/child/data/currentTime", 0))
        assertFalse(touchesUserSlot("/data/players/0/name", 0))
    }

    @Test fun `a shorter path that stops above the slots does not`() {
        assertFalse(touchesUserSlot("/child/data/userSlots", 0))
        assertFalse(touchesUserSlot("/child/data", 0))
    }
}
