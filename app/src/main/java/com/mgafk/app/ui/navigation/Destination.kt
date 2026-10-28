package com.mgafk.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every screen of the app. The five with [inBottomBar] sit in the floating bar; the rest
 * are listed on More and open with a back arrow that returns there.
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
    val requiresConnection: Boolean = false,
    val inBottomBar: Boolean = false,
    /** One line under the label on the More screen. */
    val description: String = "",
) {
    HOME("Home", Icons.Rounded.Home, inBottomBar = true),
    GARDEN("Garden", Icons.Rounded.Grass, requiresConnection = true, inBottomBar = true),
    PETS("Pets", Icons.Rounded.Pets, requiresConnection = true, inBottomBar = true),
    SHOPS("Shops", Icons.Rounded.ShoppingCart, requiresConnection = true, inBottomBar = true),
    MORE("More", Icons.Rounded.MoreHoriz, inBottomBar = true),

    ROOM("Room", Icons.Outlined.MeetingRoom, requiresConnection = true, description = "Players and chat"),
    STORAGE("Storage", Icons.Outlined.Inventory2, requiresConnection = true, description = "Inventory, silo, hutch, trough"),
    SOCIAL("Social", Icons.Outlined.People, description = "Browse and join public rooms"),
    ALERTS("Alerts", Icons.Outlined.Notifications, description = "Notifications and alarms"),
    CUSTOMIZE("Customize", Icons.Outlined.Palette, description = "Colors, theme, corners, text size"),
    SETTINGS("Settings", Icons.Outlined.Settings, description = "Background, reconnection, alarm"),
    REPORT_BUG("Report a bug", Icons.Outlined.Feedback, description = "Send a report with logs"),
    DEBUG("Debug", Icons.Outlined.BugReport, description = "WebSocket and service logs");

    /** Screens opened from More get a back arrow. */
    val parent: Destination? get() = if (inBottomBar) null else MORE

    companion object {
        val bottomBar: List<Destination> = entries.filter { it.inBottomBar }
        val moreItems: List<Destination> = entries.filter { !it.inBottomBar }
    }
}
