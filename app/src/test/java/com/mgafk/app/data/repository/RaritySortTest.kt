package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** Inventory, storage and team lists put the rarest items first and anything unranked last. */
class RaritySortTest {

    @Test fun `rarer comes first`() {
        val sorted = listOf("Common", "Celestial", "Rare", "Mythic", "Divine", "Uncommon", "Legendary")
            .sortedBy { RaritySort.index(it) }
        assertEquals(listOf("Celestial", "Divine", "Mythic", "Legendary", "Rare", "Uncommon", "Common"), sorted)
    }

    @Test fun `the older Mythical spelling ranks right after Mythic`() {
        assertEquals(RaritySort.index("Mythic") + 1, RaritySort.index("Mythical"))
        assertEquals(RaritySort.index("Legendary") - 1, RaritySort.index("Mythical"))
    }

    @Test fun `case does not matter`() {
        assertEquals(RaritySort.index("Rare"), RaritySort.index("rare"))
        assertEquals(RaritySort.index("Celestial"), RaritySort.index("CELESTIAL"))
    }

    @Test fun `unknown and missing rarities go last, together`() {
        val last = RaritySort.index("Common") + 1
        assertEquals(last, RaritySort.index(null))
        assertEquals(last, RaritySort.index("Exotic"))
    }
}
