package com.mgafk.app.ui.screens.garden

import com.mgafk.app.data.model.GardenPlantSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtering a garden of patch plants.
 *
 * A Clover tile holds several crops at once, and only some of them carry the mutation being
 * hunted. Keeping the whole tile means going through every slot by hand to find the two that
 * match, which is the complaint this addresses: a filtered tile now shows only the crops that
 * match it.
 */
class GardenFilterTest {

    private fun crop(species: String, size: Int, vararg mutations: String) = ResolvedPlant(
        snapshot = GardenPlantSnapshot(
            tileId = 1,
            slotIndex = 0,
            species = species,
            size = size,
            mutations = mutations.toList(),
        ),
        rarity = "Common",
        cropSprite = null,
        displayName = species,
        sellPrice = 100L,
    )

    private fun patch(vararg crops: ResolvedPlant) = GardenEntry.MultiSlotPlant(
        tileId = 1,
        rarity = "Common",
        displayName = "Clover",
        cropSprite = null,
        crops = crops.toList(),
    )

    private val amber = crop("Clover", 80, "Ambershine")
    private val plain = crop("Clover", 60)
    private val gold = crop("Clover", 90, "Gold")

    private fun filter(
        entries: List<GardenEntry>,
        rarity: String? = null,
        mutations: Set<String> = emptySet(),
        minSize: Double = 0.0,
        query: String = "",
    ) = GardenFilter.apply(entries, rarity, mutations, minSize, query)

    @Test fun `a patch keeps only the crops that match the mutation`() {
        val result = filter(listOf(patch(amber, plain, gold)), mutations = setOf("Ambershine"))

        val kept = result.single() as GardenEntry.MultiSlotPlant
        assertEquals(listOf(amber), kept.crops)
    }

    @Test fun `a patch with nothing matching drops out entirely`() {
        val result = filter(listOf(patch(plain, gold)), mutations = setOf("Ambershine"))

        assertTrue(result.isEmpty())
    }

    /** The tile's value has to follow, or the total counts crops the filter just hid. */
    @Test fun `a narrowed patch is worth only what it still shows`() {
        val result = filter(listOf(patch(amber, plain, gold)), mutations = setOf("Ambershine"))

        assertEquals(100L, result.single().totalValue)
    }

    @Test fun `size filters the crops of a patch one by one`() {
        val result = filter(listOf(patch(amber, plain, gold)), minSize = 70.0)

        val kept = result.single() as GardenEntry.MultiSlotPlant
        assertEquals(listOf(amber, gold), kept.crops)
    }

    /** Mutations combine with AND, as they do for a single crop. */
    @Test fun `a crop must carry every mutation asked for`() {
        val both = crop("Clover", 80, "Ambershine", "Gold")
        val result = filter(listOf(patch(amber, both)), mutations = setOf("Ambershine", "Gold"))

        assertEquals(listOf(both), (result.single() as GardenEntry.MultiSlotPlant).crops)
    }

    @Test fun `with no filter every crop of a patch stays`() {
        val whole = patch(amber, plain, gold)

        assertEquals(listOf(whole), filter(listOf(whole)))
    }

    // ── Tile-level filters still apply to the whole entry ──

    @Test fun `rarity is a property of the tile, not of its crops`() {
        val result = filter(listOf(patch(amber, plain)), rarity = "Legendary")

        assertTrue(result.isEmpty())
    }

    @Test fun `the search matches the plant name`() {
        val result = filter(listOf(patch(amber)), query = "clov")

        assertEquals(1, result.size)
        assertTrue(filter(listOf(patch(amber)), query = "carrot").isEmpty())
    }

    // ── Single crops keep behaving as they did ──

    @Test fun `a single crop is kept or dropped whole`() {
        val single = GardenEntry.SingleCrop(amber)

        assertEquals(listOf(single), filter(listOf(single), mutations = setOf("Ambershine")))
        assertTrue(filter(listOf(single), mutations = setOf("Gold")).isEmpty())
    }

    @Test fun `a single crop below the size floor drops out`() {
        val single = GardenEntry.SingleCrop(plain)

        assertTrue(filter(listOf(single), minSize = 70.0).isEmpty())
    }
}
