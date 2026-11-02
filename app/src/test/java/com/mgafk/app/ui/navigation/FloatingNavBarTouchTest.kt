package com.mgafk.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taps on the floating bottom bar.
 *
 * The bar is drawn over the screens, which scroll behind it. Only its tabs were clickable: a tap
 * between two icons, or just above or below one, went through the pill to whatever was behind
 * it. On Pets that is a team card, so missing a tab activated another team (BR-24: "I often
 * misclick different teams when trying to click on other menu tabs").
 */
class FloatingNavBarTouchTest {

    private val layout = FloatingNavBarLayout

    /** A 400 dp wide phone, Pets selected: five tabs, the third one wide with its label. */
    private val screenWidth = 400f
    private val itemWidths = listOf(
        layout.itemWidth(selected = false),
        layout.itemWidth(selected = false),
        layout.itemWidth(selected = true, labelWidth = 34f),
        layout.itemWidth(selected = false),
        layout.itemWidth(selected = false),
    )

    private fun touchAt(x: Float, y: Float) = layout.touchAt(x, y, screenWidth, itemWidths)

    private val middle = layout.HEIGHT / 2

    /** With these widths the first tab spans 24..70 dp and the second 88..134 dp. */
    @Test fun `a tap between two tabs does not reach the screen behind`() {
        assertNotEquals(NavBarTouch.Behind, touchAt(79f, middle))
    }

    /** The tabs are 48 dp high in a 64 dp pill: 8 dp of pill above each of them. */
    @Test fun `a tap on the pill just above a tab does not reach the screen behind`() {
        assertNotEquals(NavBarTouch.Behind, touchAt(47f, 4f))
    }

    @Test fun `no point of the pill lets a tap through`() {
        val leaks = buildList {
            var x = layout.SIDE_MARGIN
            while (x <= screenWidth - layout.SIDE_MARGIN) {
                var y = 0f
                while (y <= layout.HEIGHT) {
                    if (touchAt(x, y) == NavBarTouch.Behind) add(x to y)
                    y += 2f
                }
                x += 2f
            }
        }
        assertTrue("taps go through the pill at ${leaks.take(5)} (${leaks.size} points)", leaks.isEmpty())
    }

    // ── What must keep working ──

    @Test fun `a tap on an icon still selects its tab`() {
        assertEquals(NavBarTouch.Tab(0), touchAt(47f, middle))
        assertEquals(NavBarTouch.Tab(1), touchAt(111f, middle))
    }

    /** Content above the bar stays reachable. */
    @Test fun `a tap well above the bar reaches the screen`() {
        assertEquals(NavBarTouch.Behind, touchAt(79f, -50f))
    }
}
