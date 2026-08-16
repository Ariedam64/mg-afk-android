package com.mgafk.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The consecutive-restock case is the one that was reported broken: the egg shop rolled
 * LegendaryEgg at 23 min, alerted, then rolled it again 15 min later and stayed silent.
 */
class ShopAlertTrackerTest {

    private val legendaryEgg = ShopAlertTracker.keyOf("egg", "LegendaryEgg")
    private val commonEgg = ShopAlertTracker.keyOf("egg", "CommonEgg")
    private val carrot = ShopAlertTracker.keyOf("seed", "Carrot")

    private val allEnabled: (String) -> Boolean = { true }

    @Test fun `stock alerts once`() {
        val tracker = ShopAlertTracker()

        assertEquals(listOf(legendaryEgg), tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled))
    }

    @Test fun `standing stock does not alert again`() {
        val tracker = ShopAlertTracker()
        tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled)

        assertEquals(emptyList<String>(), tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled))
    }

    @Test fun `same item rolled by two consecutive restocks alerts twice`() {
        val tracker = ShopAlertTracker()
        tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled)

        val second = tracker.newlyStocked(listOf(legendaryEgg), setOf("egg"), allEnabled)

        assertEquals(listOf(legendaryEgg), second)
    }

    @Test fun `a restock only clears the shop that restocked`() {
        val tracker = ShopAlertTracker()
        tracker.newlyStocked(listOf(legendaryEgg, carrot), noRestock, allEnabled)

        val afterEggRestock = tracker.newlyStocked(listOf(legendaryEgg, carrot), setOf("egg"), allEnabled)

        assertEquals(listOf(legendaryEgg), afterEggRestock)
    }

    @Test fun `item leaving the shop alerts again when it comes back`() {
        val tracker = ShopAlertTracker()
        tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled)
        tracker.newlyStocked(listOf(commonEgg), noRestock, allEnabled)

        assertEquals(listOf(legendaryEgg), tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled))
    }

    @Test fun `disabled items never alert`() {
        val tracker = ShopAlertTracker()

        val alerted = tracker.newlyStocked(listOf(legendaryEgg), noRestock) { it != legendaryEgg }

        assertEquals(emptyList<String>(), alerted)
    }

    @Test fun `enabling an item mid cycle alerts on the stock already standing`() {
        val tracker = ShopAlertTracker()
        tracker.newlyStocked(listOf(legendaryEgg), noRestock) { false }

        assertEquals(listOf(legendaryEgg), tracker.newlyStocked(listOf(legendaryEgg), noRestock, allEnabled))
    }

    @Test fun `a duplicated entry alerts once`() {
        val tracker = ShopAlertTracker()

        val alerted = tracker.newlyStocked(listOf(legendaryEgg, legendaryEgg), noRestock, allEnabled)

        assertEquals(listOf(legendaryEgg), alerted)
    }

    @Test fun `keys are the ones AlertConfig stores`() {
        assertEquals("shop:egg:LegendaryEgg", legendaryEgg)
    }

    private companion object {
        val noRestock = emptySet<String>()
    }
}
