package com.mgafk.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertItem
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AlertSection
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.screens.alerts.AlertsCards
import com.mgafk.app.ui.screens.connection.ConnectionCard
import com.mgafk.app.ui.screens.room.ChatCard
import com.mgafk.app.ui.screens.room.PlayersCard
import com.mgafk.app.ui.screens.logs.AbilityLogsCard
import com.mgafk.app.ui.screens.garden.EggsCard
import com.mgafk.app.ui.screens.garden.GardenCard
import com.mgafk.app.ui.screens.storage.DecorShedCard
import com.mgafk.app.ui.screens.storage.FeedingTroughCard
import com.mgafk.app.ui.screens.storage.InventoryCard
import com.mgafk.app.ui.screens.storage.PetHutchCard
import com.mgafk.app.ui.screens.storage.SeedSiloCard
import com.mgafk.app.ui.screens.pets.PetHungerCard
import com.mgafk.app.ui.screens.shops.ShopsCards
import com.mgafk.app.ui.screens.status.LiveStatusCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.BgDark
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.StatusIdle
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Navigation sections ──

enum class NavSection(
    val label: String,
    val icon: ImageVector,
    val requiresConnection: Boolean = false,
) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    ROOM("Room", Icons.Outlined.MeetingRoom, requiresConnection = true),
    PETS("Pets", Icons.Outlined.Pets, requiresConnection = true),
    STORAGE("Storage", Icons.Outlined.Inventory2, requiresConnection = true),
    GARDEN("Garden", Icons.Outlined.Grass, requiresConnection = true),
    SHOPS("Shops", Icons.Outlined.ShoppingCart, requiresConnection = true),
    ALERTS("Alerts", Icons.Outlined.Notifications),
}

// ── Main Screen ──

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginRequest: (sessionId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val session = state.activeSession
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentSection by rememberSaveable { mutableStateOf(NavSection.DASHBOARD.name) }
    val selected = NavSection.valueOf(currentSection)

    // Compose sections one by one after API loads, behind the loading overlay
    var readySections by remember { mutableStateOf(emptySet<NavSection>()) }
    var allReady by remember { mutableStateOf(false) }
    var loadingStep by remember { mutableStateOf("") }

    // Track ViewModel loading steps
    LaunchedEffect(state.loadingStep) {
        if (state.loadingStep.isNotBlank()) loadingStep = state.loadingStep
    }

    // After API ready, compose sections one by one behind the overlay
    LaunchedEffect(state.apiReady) {
        if (state.apiReady) {
            NavSection.entries.forEach { section ->
                loadingStep = "Preparing ${section.label}…"
                readySections = readySections + section
                delay(150) // yield frames so spinner keeps animating
            }
            loadingStep = ""
            allReady = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = allReady,
            drawerContent = {
                DrawerContent(
                    selected = selected,
                    connected = session.connected,
                    playerName = session.playerName,
                    updateAvailable = state.updateAvailable,
                    onSelect = { section ->
                        currentSection = section.name
                        scope.launch { drawerState.snapTo(DrawerValue.Closed) }
                    },
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
            ) {
                // ── Top bar ──
                TopBar(
                    sectionLabel = selected.label,
                    onMenuClick = { scope.launch { drawerState.open() } },
                )

                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                // ── Sections: always laid out at full size, slid off-screen when hidden ──
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    NavSection.entries.forEach { section ->
                        if (section in readySections) {
                            val isVisible = section == selected
                            val slide by animateFloatAsState(
                                targetValue = if (isVisible) 0f else 1f,
                                animationSpec = tween(300),
                                label = "slide_${section.name}",
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = slide * size.width }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    SectionContent(
                                        section = section,
                                        session = session,
                                        state = state,
                                        viewModel = viewModel,
                                        onLoginRequest = onLoginRequest,
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Loading overlay — visible until ALL sections are composed ──
        AnimatedVisibility(
            visible = !allReady,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(400)),
        ) {
            LoadingOverlay(step = loadingStep)
        }
    }
}

// ── Loading overlay ──

@Composable
private fun LoadingOverlay(step: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "MG AFK",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                letterSpacing = (-0.5).sp,
            )

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Accent,
                strokeWidth = 3.dp,
            )

            if (step.isNotBlank()) {
                Text(
                    text = step,
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

// ── Drawer content ──

@Composable
private fun DrawerContent(
    selected: NavSection,
    connected: Boolean,
    playerName: String,
    updateAvailable: com.mgafk.app.data.repository.AppRelease?,
    onSelect: (NavSection) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalDrawerSheet(
        drawerContainerColor = SurfaceDark,
        modifier = Modifier.width(260.dp),
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                text = "MG AFK",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                letterSpacing = (-0.5).sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${com.mgafk.app.BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    color = TextMuted,
                )
                if (updateAvailable != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update ${updateAvailable.tagName}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusConnected,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusConnected.copy(alpha = 0.12f))
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(updateAvailable.downloadUrl),
                                )
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Dashboard (standalone)
        DrawerItem(
            icon = NavSection.DASHBOARD.icon,
            label = NavSection.DASHBOARD.label,
            selected = selected == NavSection.DASHBOARD,
            enabled = true,
            onClick = { onSelect(NavSection.DASHBOARD) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Active session sub-category
        val sessionLabel = if (connected && playerName.isNotBlank()) playerName else "Session"
        Text(
            text = sessionLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (connected) Accent.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.5f),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // Session-dependent items (Pets, Shops)
        NavSection.entries
            .filter { it.requiresConnection }
            .forEach { section ->
                val isSelected = section == selected
                DrawerItem(
                    icon = section.icon,
                    label = section.label,
                    selected = isSelected,
                    enabled = connected,
                    onClick = { if (connected) onSelect(section) },
                )
            }

        Spacer(modifier = Modifier.weight(1f))

        // Alerts — pinned at bottom, separated
        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))
        DrawerItem(
            icon = NavSection.ALERTS.icon,
            label = NavSection.ALERTS.label,
            selected = selected == NavSection.ALERTS,
            enabled = true,
            onClick = { onSelect(NavSection.ALERTS) },
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) Accent.copy(alpha = 0.12f) else SurfaceDark
    val contentColor = when {
        !enabled -> TextMuted.copy(alpha = 0.4f)
        selected -> Accent
        else -> TextPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

// ── Top bar ──

@Composable
private fun TopBar(sectionLabel: String, onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = sectionLabel,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            letterSpacing = (-0.3).sp,
        )
    }
}

// ── Section content router ──

@Composable
private fun SectionContent(
    section: NavSection,
    session: Session,
    state: com.mgafk.app.ui.UiState,
    viewModel: MainViewModel,
    onLoginRequest: (sessionId: String) -> Unit,
) {
    when (section) {
        NavSection.DASHBOARD -> {
            // ── Session selector (chips) ──
            SessionChips(
                sessions = state.sessions,
                activeId = state.activeSessionId,
                onSelect = { viewModel.selectSession(it) },
                onAdd = { viewModel.addSession() },
            )

            ConnectionCard(
                session = session,
                onCookieChange = { viewModel.updateSession(session.id) { s -> s.copy(cookie = it) } },
                onRoomChange = { viewModel.updateSession(session.id) { s -> s.copy(room = it) } },
                onConnect = { viewModel.connect(session.id) },
                onDisconnect = { viewModel.disconnect(session.id) },
                onLogin = { onLoginRequest(session.id) },
                onLogout = { viewModel.clearToken(session.id) },
            )
            LiveStatusCard(session = session)

            // ── Remove session ──
            RemoveSessionButton(
                sessionName = session.name,
                onRemove = { viewModel.removeSession(session.id) },
            )
        }
        NavSection.ROOM -> {
            PlayersCard(
                players = session.playersList,
                gameVersion = session.gameVersion,
                gameHost = session.gameUrl,
            )
            ChatCard(
                messages = session.chatMessages,
                players = session.playersList,
                gameVersion = session.gameVersion,
                gameHost = session.gameUrl,
                onSend = { message -> viewModel.sendChat(session.id, message) },
            )
        }
        NavSection.GARDEN -> {
            GardenCard(plants = session.garden, apiReady = state.apiReady)
            EggsCard(eggs = session.gardenEggs, apiReady = state.apiReady)
        }
        NavSection.PETS -> {
            PetHungerCard(pets = session.pets)
            AbilityLogsCard(logs = session.logs, apiReady = state.apiReady, onClear = { viewModel.clearLogs(session.id) })
        }
        NavSection.SHOPS -> {
            ShopsCards(
                shops = session.shops,
                apiReady = state.apiReady,
                purchaseError = state.purchaseError,
                showTip = state.showShopTip,
                onDismissTip = { viewModel.dismissShopTip() },
                onBuy = { shopType, itemName -> viewModel.purchaseShopItem(session.id, shopType, itemName) },
                onBuyAll = { shopType, itemName -> viewModel.purchaseAllShopItem(session.id, shopType, itemName) },
            )
        }
        NavSection.STORAGE -> {
            InventoryCard(inventory = session.inventory, apiReady = state.apiReady)
            SeedSiloCard(seeds = session.seedSilo, apiReady = state.apiReady)
            DecorShedCard(decors = session.decorShed, apiReady = state.apiReady)
            PetHutchCard(pets = session.petHutch, apiReady = state.apiReady)
            FeedingTroughCard(crops = session.feedingTrough, apiReady = state.apiReady)
        }
        NavSection.ALERTS -> {
            AlertsCards(
                alerts = state.alerts,
                apiReady = state.apiReady,
                onToggle = { key, enabled ->
                    viewModel.updateAlerts { config ->
                        val items = config.items.toMutableMap()
                        items[key] = AlertItem(enabled = enabled)
                        config.copy(items = items)
                    }
                },
                onSectionModeChange = { section, mode ->
                    viewModel.updateAlerts { config ->
                        config.copy(sectionModes = config.sectionModes + (section.key to mode))
                    }
                },
                onTestAlert = { mode -> viewModel.testAlert(mode) },
                onCollapseChange = { key, collapsed ->
                    viewModel.updateAlerts { config ->
                        config.copy(collapsed = config.collapsed + (key to collapsed))
                    }
                },
            )
        }
    }
}

// ── Session chips ──

@Composable
private fun SessionChips(
    sessions: List<Session>,
    activeId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEach { s ->
            val isActive = s.id == activeId
            val statusColor = when (s.status) {
                SessionStatus.CONNECTED -> StatusConnected
                SessionStatus.CONNECTING -> StatusConnecting
                SessionStatus.ERROR -> StatusError
                SessionStatus.IDLE -> StatusIdle
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isActive) Modifier.background(Accent.copy(alpha = 0.15f))
                            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        else Modifier.background(SurfaceCard)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                    )
                    .clickable { onSelect(s.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = s.name,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) TextPrimary else TextMuted,
                )
            }
        }

        // Add session chip
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(SurfaceBorder.copy(alpha = 0.5f))
                .clickable { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add session",
                tint = Accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Remove session button ──

@Composable
private fun RemoveSessionButton(
    sessionName: String,
    onRemove: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StatusError.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Remove session",
            tint = StatusError.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Remove $sessionName",
            fontSize = 13.sp,
            color = StatusError.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Remove session") },
            text = { Text("Remove \"$sessionName\"? This will disconnect and delete all its data.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onRemove()
                }) {
                    Text("Remove", color = StatusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
        )
    }
}
