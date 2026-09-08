package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile maps come straight off the wire, shaped as in a real capture: keyed by tile index,
 * holding plants, decor, eggs and crystals side by side.
 */
class CrystalParserTest {

    private fun tiles(json: String): JsonObject = AppJson.default.parseToJsonElement(json).jsonObject

    private val dirtTiles = tiles(
        """
        {
          "12": {"objectType": "plant", "species": "Carrot"},
          "34": {"objectType": "decor", "decorId": "MiniWizardTower", "rotation": 0},
          "40": {"objectType": "crystal", "crystalType": "XP", "remainingActiveSeconds": 14388}
        }
        """.trimIndent()
    )

    // Both Rain Wards sat on the bottom boardwalk edge in the capture, one of them fused.
    private val boardwalkTiles = tiles(
        """
        {
          "3": {"objectType": "decor", "decorId": "StoneCaribou", "rotation": 0},
          "60": {"objectType": "crystal", "crystalType": "RainWard", "remainingActiveSeconds": 28790},
          "61": {"objectType": "crystal", "crystalType": "RainWard", "remainingActiveSeconds": 14388}
        }
        """.trimIndent()
    )

    private val crystals = CrystalParser.parse(dirtTiles, boardwalkTiles)

    @Test fun `it reads crystals from both tile maps`() {
        assertEquals(3, crystals.size)
        assertEquals(1, crystals.count { it.tileType == GardenTileType.Dirt })
        assertEquals(2, crystals.count { it.tileType == GardenTileType.Boardwalk })
    }

    @Test fun `it keeps the tile a crystal stands on`() {
        val fused = crystals.first { it.remainingSeconds == 28790 }
        assertEquals(GardenTileType.Boardwalk, fused.tileType)
        assertEquals(60, fused.localTileIndex)
        assertEquals(CrystalType.RainWard, fused.type)
    }

    @Test fun `it reads a crystal on the dirt`() {
        val onDirt = crystals.first { it.tileType == GardenTileType.Dirt }
        assertEquals(CrystalType.XP, onDirt.type)
        assertEquals(40, onDirt.localTileIndex)
        assertEquals(14388, onDirt.remainingSeconds)
    }

    @Test fun `plants and decor are left alone`() {
        assertTrue(crystals.none { it.localTileIndex == 12 && it.tileType == GardenTileType.Dirt })
        assertTrue(crystals.none { it.localTileIndex == 34 && it.tileType == GardenTileType.Dirt })
    }

    @Test fun `a garden without crystals reads as empty`() {
        assertTrue(CrystalParser.parse(tiles("""{"1": {"objectType": "plant"}}"""), null).isEmpty())
        assertTrue(CrystalParser.parse(null, null).isEmpty())
    }

    /** A crystal type this build does not know about is skipped rather than guessed at. */
    @Test fun `an unknown crystal type is ignored`() {
        val unknown = tiles("""{"5": {"objectType": "crystal", "crystalType": "Mystery", "remainingActiveSeconds": 10}}""")
        assertTrue(CrystalParser.parse(unknown, null).isEmpty())
    }
}
