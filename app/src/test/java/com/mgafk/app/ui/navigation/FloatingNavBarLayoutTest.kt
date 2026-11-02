package com.mgafk.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** Six tabs, the selected one with its label, fit on a 360 dp wide phone (BR-23). */
class FloatingNavBarLayoutTest {

    @Test fun `the selected label has room for Storage on a 360 dp screen`() {
        val room = navBarSelectedLabelRoom(360.dp, Destination.bottomBar.size)
        assertTrue("only $room left for the label", room >= 60.dp)
    }
}
