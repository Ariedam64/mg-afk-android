package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BR-67: the weather potions added in game version 2198 must be known without MG API.
 * The bundle (tools-DlkITWDy.js) grants Thunderstruck, Thundercharged, Dawncharged (DawnboundPotion)
 * and Ambercharged (AmberboundPotion); there is no "DawnchargedPotion".
 */
class MutationPotionsKnownTest {

    private fun fallback(toolId: String) = MutationPotions.grantedMutation(toolId) { null }

    @Test fun `ThunderstruckPotion grants Thunderstruck without the catalogue`() {
        assertEquals("Thunderstruck", fallback("ThunderstruckPotion"))
    }

    @Test fun `ThunderchargedPotion grants Thundercharged without the catalogue`() {
        assertEquals("Thundercharged", fallback("ThunderchargedPotion"))
    }

    @Test fun `DawnboundPotion grants Dawncharged without the catalogue`() {
        assertEquals("Dawncharged", fallback("DawnboundPotion"))
    }

    @Test fun `AmberboundPotion grants Ambercharged without the catalogue`() {
        assertEquals("Ambercharged", fallback("AmberboundPotion"))
    }

    @Test fun `the older potions keep their fallback`() {
        assertEquals("Wet", fallback("WetPotion"))
        assertEquals("Ambershine", fallback("AmberlitPotion"))
        assertEquals("Rainbow", fallback("RainbowPotion"))
    }
}
