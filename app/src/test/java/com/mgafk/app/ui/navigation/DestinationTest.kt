package com.mgafk.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BR-23: Storage is a main tab of the bottom bar again, right after Pets, instead of being
 * reached through More. No existing tab is removed.
 */
class DestinationTest {

    @Test fun `the bottom bar has six tabs with Storage right after Pets`() {
        assertEquals(
            listOf(
                Destination.HOME,
                Destination.GARDEN,
                Destination.PETS,
                Destination.STORAGE,
                Destination.SHOPS,
                Destination.MORE,
            ),
            Destination.bottomBar,
        )
    }

    @Test fun `Storage leaves the More list and Room stays first there`() {
        assertFalse(Destination.STORAGE in Destination.moreItems)
        assertTrue(Destination.ROOM in Destination.moreItems)
        assertEquals(Destination.ROOM, Destination.moreItems.first())
    }

    @Test fun `Storage opens without a back arrow to More`() {
        assertNull(Destination.STORAGE.parent)
        assertEquals(Destination.MORE, Destination.ROOM.parent)
    }

    @Test fun `Storage still needs a connection`() {
        assertTrue(Destination.STORAGE.requiresConnection)
    }
}
