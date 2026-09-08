package com.mgafk.app.data.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every ability the game writes to its activity log should produce a line here.
 *
 * An unhandled action falls through to null and the entry silently vanishes from the log, which
 * is how the Thunder abilities went missing: they were added to families the formatter already
 * knew, so nothing broke, the lines just stopped appearing.
 *
 * The list is the set of ability ids the game's own activityLogs schema accepts, read off
 * bundle 1118. Abilities that never log (continuous boosts like DawnBoost or HungerBoost, and
 * the capture abilities) are deliberately absent.
 */
class AbilityFormatterCoverageTest {

    /** Parameters wide enough to satisfy any one family; extra keys are ignored. */
    private val everyParam = mapOf(
        "coinsFound" to "120",
        "speciesId" to "Carrot",
        "hungerRestoreAmount" to "500",
        "harvestedCropSpecies" to "Apple",
        "extraPetSpecies" to "Worm",
        "growSlotSpecies" to "Tomato",
        "sellPrice" to "900",
        "strengthIncrease" to "3",
        "bonusXp" to "650",
        "petsAffectedCount" to "2",
        "eggsAffectedCount" to "1",
        "secondsReduced" to "600",
        "numPlantsAffected" to "4",
        "sizeIncrease" to "7",
        "mutation" to "Thunderstruck",
        "eggId" to "CommonEgg",
        "cropsRefundedCount" to "3",
        "bonusCoins" to "50",
        "bonusDust" to "250",
        "targetPetSpecies" to "Bunny",
        "targetPetId" to "pet-2",
        "petId" to "pet-1",
    )

    private fun describe(action: String): String? =
        AbilityFormatter.format(AbilityLog(action = action, params = everyParam))

    private val loggedAbilities = listOf(
        // Coin Finder, including the two weather variants and the fourth tier
        "CoinFinderI", "CoinFinderII", "CoinFinderIII", "CoinFinderIV",
        "SnowyCoinFinder", "DawnCoinFinder", "ThunderCoinFinder",
        "DustBoost",
        "SeedFinderI", "SeedFinderII", "SeedFinderIII", "SeedFinderIV",
        "HungerRestore", "HungerRestoreII", "HungerRestoreIII", "SnowyHungerRestore", "Rebirth",
        "DoubleHarvest", "DoubleHatch", "DoubleHatchII",
        "ProduceRefund", "ProduceEater",
        "SellBoostI", "SellBoostII", "SellBoostIII", "SellBoostIV",
        // XP boosts: one per weather now
        "PetXpBoost", "PetXpBoostII", "PetXpBoostIII",
        "SnowyPetXpBoost", "DawnXpBoost", "ThunderXpBoost", "AmberXpBoost",
        "PetRefund", "PetRefundII",
        "PetAgeBoost", "PetAgeBoostII", "PetAgeBoostIII",
        "EggGrowthBoost", "EggGrowthBoostII", "EggGrowthBoostII_NEW",
        "SnowyEggGrowthBoost", "ThunderEggGrowthBoost", "AmberEggGrowthBoost",
        "PetHatchSizeBoost", "PetHatchSizeBoostII", "PetHatchSizeBoostIII",
        "ProduceScaleBoost", "ProduceScaleBoostII", "ProduceScaleBoostIII", "SnowyCropSizeBoost",
        "PlantGrowthBoost", "PlantGrowthBoostII", "PlantGrowthBoostIII",
        "SnowyPlantGrowthBoost", "DawnPlantGrowthBoost", "AmberPlantGrowthBoost", "ThunderPlantGrowthBoost",
        "GoldGranter", "RainbowGranter", "RainDance", "SnowGranter", "FrostGranter",
        "DawnlitGranter", "AmberlitGranter", "ThunderstruckGranter",
    )

    @Test fun `every logged ability produces a description`() {
        val unhandled = loggedAbilities.filter { describe(it) == null }

        assertTrue("no description for: $unhandled", unhandled.isEmpty())
    }

    @Test fun `no description is left blank`() {
        val blank = loggedAbilities.filter { describe(it).orEmpty().isBlank() }

        assertTrue("blank description for: $blank", blank.isEmpty())
    }

    // ── The families the weather variants join ──

    @Test fun `a weather XP boost reads like its plain counterpart`() {
        assertEquals(describe("PetXpBoost"), describe("ThunderXpBoost"))
        assertEquals(describe("PetXpBoost"), describe("AmberXpBoost"))
    }

    @Test fun `a weather egg boost reads like its plain counterpart`() {
        assertEquals(describe("EggGrowthBoost"), describe("ThunderEggGrowthBoost"))
    }

    @Test fun `a weather plant boost reads like its plain counterpart`() {
        assertEquals(describe("PlantGrowthBoost"), describe("ThunderPlantGrowthBoost"))
    }

    @Test fun `the thunder granter reads like the other granters`() {
        assertEquals(describe("GoldGranter"), describe("ThunderstruckGranter"))
    }

    // ── The two genuinely new shapes ──

    @Test fun `dust boost reports the dust it found`() {
        val line = assertNotNull(describe("DustBoost")).let { describe("DustBoost")!! }

        assertTrue(line, line.contains("250"))
    }

    @Test fun `rebirth reports the hunger it restored`() {
        val line = describe("Rebirth")!!

        assertTrue(line, line.contains("500"))
    }

    // ── What must stay silent ──

    @Test fun `an unknown action has no description`() {
        assertNull(describe("SomethingTheGameAddedYesterday"))
    }

    /** These are filtered upstream by Constants.isAbilityName and never reach the formatter. */
    @Test fun `the kisser abilities are not formatted here`() {
        assertNull(describe("MoonKisser"))
        assertNull(describe("DawnKisser"))
    }

    private fun assertEquals(expected: String?, actual: String?) =
        org.junit.Assert.assertEquals(expected, actual)
}
