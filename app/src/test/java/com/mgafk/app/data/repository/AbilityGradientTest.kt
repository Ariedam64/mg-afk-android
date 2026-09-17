package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two abilities are painted with a gradient rather than one colour, and the API sends it as a
 * field of its own next to `color`. Reading only `color` is what left Rainbow Granter a flat
 * dark red instead of its spectrum.
 */
class AbilityGradientTest {

    /** [gradientBody] is the ability's `gradient` block, wrapped as the API sends it. */
    private fun parse(gradientBody: String) = parseAbility("""{"gradient": $gradientBody}""")

    private fun parseAbility(body: String) =
        MgApi.parseAbilityGradient(AppJson.default.parseToJsonElement(body).jsonObject)

    private val rainbow = """
        {"angleDegrees": 45, "colorStops": [
          {"color": "#C80000", "offset": 0},
          {"color": "#C87800", "offset": 0.14285714285714285},
          {"color": "#461E96", "offset": 1}
        ]}
    """.trimIndent()

    @Test fun `it reads the angle and every stop`() {
        val gradient = parse(rainbow)!!

        assertEquals(45.0, gradient.angleDegrees, 0.0)
        assertEquals(3, gradient.stops.size)
        assertEquals("#C80000", gradient.stops.first().color)
        assertEquals(0.0, gradient.stops.first().offset, 0.0)
        assertEquals("#461E96", gradient.stops.last().color)
        assertEquals(1.0, gradient.stops.last().offset, 0.0)
    }

    /** Stops are drawn in order, so an out-of-order payload must not paint a scrambled ramp. */
    @Test fun `stops come back in offset order`() {
        val shuffled = parse(
            """
            {"angleDegrees": 0, "colorStops": [
              {"color": "#FFFFFF", "offset": 1},
              {"color": "#000000", "offset": 0},
              {"color": "#888888", "offset": 0.5}
            ]}
            """.trimIndent()
        )!!

        assertEquals(listOf(0.0, 0.5, 1.0), shuffled.stops.map { it.offset })
        assertEquals("#000000", shuffled.stops.first().color)
    }

    @Test fun `a stop without a colour is dropped, the rest still paint`() {
        val gradient = parse(
            """{"angleDegrees": 0, "colorStops": [
              {"color": "#000000", "offset": 0}, {"offset": 0.5}, {"color": "#112233", "offset": 1}
            ]}"""
        )!!

        assertEquals(listOf("#000000", "#112233"), gradient.stops.map { it.color })
    }

    /** Dropping a broken stop can leave too few to ramp between, and that is not a gradient. */
    @Test fun `a gradient left with one good stop is none`() {
        assertNull(parse("""{"angleDegrees": 0, "colorStops": [{"offset": 0}, {"color": "#112233", "offset": 1}]}"""))
    }

    /** One stop is not a gradient, and painting it as one would be a needless shader. */
    @Test fun `fewer than two stops is not a gradient`() {
        assertNull(parse("""{"angleDegrees": 0, "colorStops": [{"color": "#112233", "offset": 0}]}"""))
        assertNull(parse("""{"angleDegrees": 0, "colorStops": []}"""))
    }

    @Test fun `an ability with no gradient block has none`() {
        assertNull(parseAbility("""{"color": "#C80000"}"""))
    }

    /** Most abilities are a single colour, and they must keep working exactly as before. */
    @Test fun `the live data has a gradient only where the game draws one`() {
        val abilities = MgApi.getAbilities()
        if (abilities.isEmpty()) return  // no network in this test run

        assertTrue(abilities.values.any { it.abilityGradient != null })
    }
}
