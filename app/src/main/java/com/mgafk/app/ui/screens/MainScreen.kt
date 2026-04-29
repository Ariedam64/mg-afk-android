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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.mgafk.app.data.repository.PriceCalculator
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
import com.mgafk.app.ui.components.CardCollapseState
import com.mgafk.app.ui.components.LocalCardCollapseState
import com.mgafk.app.ui.screens.alerts.AlertsCards
import com.mgafk.app.ui.screens.debug.DebugCards
import com.mgafk.app.ui.screens.settings.SettingsCards
import com.mgafk.app.ui.screens.connection.ConnectionCard
import com.mgafk.app.ui.screens.room.ChatCard
import com.mgafk.app.ui.screens.room.PlayersCard
import com.mgafk.app.ui.screens.logs.AbilityLogsCard
import com.mgafk.app.ui.screens.minigames.BalanceCard
import com.mgafk.app.ui.screens.minigames.CasinoLoginGate
import com.mgafk.app.ui.screens.minigames.BlackjackGame
import com.mgafk.app.ui.screens.minigames.CoinFlipGame
import com.mgafk.app.ui.screens.minigames.CrashGame
import com.mgafk.app.ui.screens.minigames.DiceGame
import com.mgafk.app.ui.screens.minigames.EggHatchGame
import com.mgafk.app.ui.screens.minigames.MinesGame
import com.mgafk.app.ui.screens.minigames.SlotsGame
import com.mgafk.app.ui.screens.minigames.GameConflictDialog
import com.mgafk.app.ui.screens.minigames.GamesGrid
import com.mgafk.app.ui.screens.minigames.HistoryCard
import com.mgafk.app.ui.screens.minigames.WalletCard
import com.mgafk.app.ui.screens.garden.EggsCard
import com.mgafk.app.ui.screens.garden.GardenCard
import com.mgafk.app.ui.screens.storage.DecorShedCard
import com.mgafk.app.ui.screens.storage.FeedingTroughCard
import com.mgafk.app.ui.screens.storage.InventoryCard
import com.mgafk.app.ui.screens.storage.PetHutchCard
import com.mgafk.app.ui.screens.storage.SeedSiloCard
import com.mgafk.app.ui.screens.pets.ActivePetsCard
import com.mgafk.app.ui.screens.pets.PetTeamCard
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
    SOCIAL("Social", Icons.Outlined.People),
    MINI_GAMES("Mini Games", Icons.Outlined.SportsEsports),
    ALERTS("Alerts", Icons.Outlined.Notifications),
    SETTINGS("Settings", Icons.Outlined.Settings),
    DEBUG("Debug", Icons.Outlined.BugReport),
}

// ── Main Screen ──

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    casinoViewModel: com.mgafk.app.ui.CasinoViewModel,
    onLoginRequest: (sessionId: String) -> Unit,
    onCasinoLoginRequest: (sessionId: String) -> Unit = {},
    onPlayRequest: (sessionId: String, cookie: String, room: String, gameUrl: String) -> Unit = { _, _, _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    val casinoState by casinoViewModel.state.collectAsState()
    val session = state.activeSession

    // Sync casino API key when active session changes
    LaunchedEffect(session.casinoApiKey) {
        if (session.casinoApiKey.isNotBlank()) {
            casinoViewModel.setApiKey(session.casinoApiKey)
        }
    }
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

    val cardCollapseState = remember(state.collapsedCards) {
        CardCollapseState(
            collapsedCards = state.collapsedCards,
            onExpandedChange = { key, expanded -> viewModel.setCardExpanded(key, expanded) },
        )
    }

    CompositionLocalProvider(LocalCardCollapseState provides cardCollapseState) {
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
                    showDebugMenu = state.settings.showDebugMenu,
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
                                        casinoViewModel = casinoViewModel,
                                        casinoState = casinoState,
                                        onLoginRequest = onLoginRequest,
                                        onCasinoLoginRequest = onCasinoLoginRequest,
                                        onPlayRequest = onPlayRequest,
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
    } // CompositionLocalProvider
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
    showDebugMenu: Boolean,
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

        // Mini Games, Alerts, Settings & Debug — pinned at bottom
        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))
        DrawerItem(
            icon = NavSection.SOCIAL.icon,
            label = NavSection.SOCIAL.label,
            selected = selected == NavSection.SOCIAL,
            enabled = true,
            onClick = { onSelect(NavSection.SOCIAL) },
        )
        DrawerItem(
            icon = NavSection.MINI_GAMES.icon,
            label = NavSection.MINI_GAMES.label,
            selected = selected == NavSection.MINI_GAMES,
            enabled = true,
            onClick = { onSelect(NavSection.MINI_GAMES) },
        )
        DrawerItem(
            icon = NavSection.ALERTS.icon,
            label = NavSection.ALERTS.label,
            selected = selected == NavSection.ALERTS,
            enabled = true,
            onClick = { onSelect(NavSection.ALERTS) },
        )
        DrawerItem(
            icon = NavSection.SETTINGS.icon,
            label = NavSection.SETTINGS.label,
            selected = selected == NavSection.SETTINGS,
            enabled = true,
            onClick = { onSelect(NavSection.SETTINGS) },
        )
        if (showDebugMenu) {
            DrawerItem(
                icon = NavSection.DEBUG.icon,
                label = NavSection.DEBUG.label,
                selected = selected == NavSection.DEBUG,
                enabled = true,
                onClick = { onSelect(NavSection.DEBUG) },
            )
        }
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
            .statusBarsPadding()
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
    casinoViewModel: com.mgafk.app.ui.CasinoViewModel,
    casinoState: com.mgafk.app.ui.CasinoUiState,
    onLoginRequest: (sessionId: String) -> Unit,
    onCasinoLoginRequest: (sessionId: String) -> Unit = {},
    onPlayRequest: (sessionId: String, cookie: String, room: String, gameUrl: String) -> Unit = { _, _, _, _ -> },
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
            PlayInGameCard(
                canPlay = session.cookie.isNotBlank() && session.room.isNotBlank(),
                injectGemini = state.settings.injectGeminiMod,
                onToggleInjectGemini = { enabled ->
                    viewModel.updateSettings { it.copy(injectGeminiMod = enabled) }
                },
                onPlay = {
                    onPlayRequest(session.id, session.cookie, session.room, session.gameUrl)
                },
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
            SectionTip(
                visible = state.showGardenTip,
                text = "Tap any crop or egg to view its details and perform actions like watering, harvesting, potting and hatching.",
                onDismiss = { viewModel.dismissGardenTip() },
            )
            GardenCard(
                plants = session.garden,
                apiReady = state.apiReady,
                onHarvest = { slot, slotIndex -> viewModel.harvestCrop(session.id, slot, slotIndex) },
                onWater = { slot -> viewModel.waterPlant(session.id, slot) },
                onPot = { slot -> viewModel.potPlant(session.id, slot) },
                onCleanse = { tileId, slotIdx -> viewModel.cropCleanse(session.id, tileId, slotIdx) },
                wateringCans = session.inventory.tools.find { it.toolId == "WateringCan" }?.quantity ?: 0,
                planterPots = session.inventory.tools.find { it.toolId == "PlanterPot" }?.quantity ?: 0,
                cropCleansers = session.inventory.tools.find { it.toolId == "CropCleanser" }?.quantity ?: 0,
            )
            EggsCard(
                eggs = session.gardenEggs,
                apiReady = state.apiReady,
                onHatch = { slot -> viewModel.hatchEgg(session.id, slot) },
                lastHatchedPet = session.lastHatchedPet,
                lastHatchedEggId = session.lastHatchedEggId,
                onDismissHatchedPet = { viewModel.clearHatchedPet(session.id) },
            )
        }
        NavSection.PETS -> {
            ActivePetsCard(
                pets = session.pets,
                produce = session.inventory.produce,
                inventoryPets = session.inventory.pets,
                hutchPets = session.petHutch,
                apiReady = state.apiReady,
                showTip = state.showPetTip,
                onDismissTip = { viewModel.dismissPetTip() },
                onFeedPet = { petItemId, cropItemIds ->
                    viewModel.feedPet(session.id, petItemId, cropItemIds)
                },
                onSwapPet = { activePetId, targetPetId, isInHutch ->
                    viewModel.swapPet(session.id, activePetId, targetPetId, isInHutch)
                },
                onEquipPet = { targetPetId, isInHutch ->
                    viewModel.equipPet(session.id, targetPetId, isInHutch)
                },
                onUnequipPet = { petId ->
                    viewModel.unequipPet(session.id, petId)
                },
            )
            PetTeamCard(
                teams = state.petTeams,
                activePets = session.pets,
                inventoryPets = session.inventory.pets,
                hutchPets = session.petHutch,
                activeTeamId = remember(session.pets, state.petTeams) {
                    viewModel.detectActiveTeamId(session.id)
                },
                apiReady = state.apiReady,
                onCreate = { team -> viewModel.createPetTeam(team) },
                onUpdate = { team -> viewModel.updatePetTeam(team) },
                onDelete = { teamId -> viewModel.deletePetTeam(teamId) },
                onReorder = { from, to -> viewModel.reorderPetTeams(from, to) },
                onActivate = { team -> viewModel.activateTeam(session.id, team) },
                showTip = state.showTeamTip,
                onDismissTip = { viewModel.dismissTeamTip() },
            )
            AbilityLogsCard(logs = session.logs, apiReady = state.apiReady, onClear = { viewModel.clearLogs(session.id) })
        }
        NavSection.SHOPS -> {
            ShopsCards(
                shops = session.shops,
                apiReady = state.apiReady,
                purchaseMode = state.settings.purchaseMode,
                purchaseError = state.purchaseError,
                showTip = state.showShopTip,
                onDismissTip = { viewModel.dismissShopTip() },
                onBuy = { shopType, itemName -> viewModel.purchaseShopItem(session.id, shopType, itemName) },
                onBuyAll = { shopType, itemName -> viewModel.purchaseAllShopItem(session.id, shopType, itemName) },
            )
        }
        NavSection.STORAGE -> {
            SectionTip(
                visible = state.showStorageTip,
                text = "Tap any item to view its details, lock/unlock it, or perform actions like planting, selling and more.",
                onDismiss = { viewModel.dismissStorageTip() },
            )
            val inv = session.inventory
            val totalInventoryItems = inv.seeds.size + inv.eggs.size + inv.produce.size +
                inv.plants.size + inv.pets.size + inv.tools.size + inv.decors.size
            val hutchMax = PriceCalculator.calculateHutchCapacity(session.hutchCapacityLevel)
            val seedSiloSpecies = remember(session.seedSilo) { session.seedSilo.map { it.species }.toSet() }
            val decorShedIds = remember(session.decorShed) { session.decorShed.map { it.decorId }.toSet() }
            val invSeedSpecies = remember(inv.seeds) { inv.seeds.map { it.species }.toSet() }
            val invDecorIds = remember(inv.decors) { inv.decors.map { it.decorId }.toSet() }

            InventoryCard(
                inventory = session.inventory,
                apiReady = state.apiReady,
                freePlantTiles = session.freePlantTiles,
                favoritedItemIds = session.favoritedItemIds,
                petHutchCount = session.petHutch.size,
                petHutchMax = hutchMax,
                seedSiloCount = session.seedSilo.size,
                seedSiloSpecies = seedSiloSpecies,
                decorShedCount = session.decorShed.size,
                decorShedDecorIds = decorShedIds,
                onPlantSeed = { species -> viewModel.plantSeed(session.id, species) },
                onGrowEgg = { eggId -> viewModel.growEgg(session.id, eggId) },
                onPlantGardenPlant = { itemId -> viewModel.plantGardenPlant(session.id, itemId) },
                onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
                onSellPet = { itemId -> viewModel.sellPet(session.id, itemId) },
                onSellAllUnlockedPets = { itemIds -> viewModel.sellAllUnlockedPets(session.id, itemIds) },
                onSellAllCrops = { viewModel.sellAllCrops(session.id) },
                onSellCrop = { itemId -> viewModel.sellSingleCrop(session.id, itemId) },
                onMovePetToHutch = { petId -> viewModel.movePetToHutch(session.id, petId) },
                onMoveSeedToSilo = { species -> viewModel.moveSeedToSilo(session.id, species) },
                onMoveDecorToShed = { decorId -> viewModel.moveDecorToShed(session.id, decorId) },
                playerCount = session.players,
            )
            SeedSiloCard(seeds = session.seedSilo, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
                inventorySeedSpecies = invSeedSpecies,
                inventoryItemCount = totalInventoryItems,
                onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
                onMoveToInventory = { species -> viewModel.moveSeedFromSilo(session.id, species) })
            DecorShedCard(decors = session.decorShed, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
                inventoryDecorIds = invDecorIds,
                inventoryItemCount = totalInventoryItems,
                onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
                onMoveToInventory = { decorId -> viewModel.moveDecorFromShed(session.id, decorId) })
            PetHutchCard(pets = session.petHutch, apiReady = state.apiReady, favoritedItemIds = session.favoritedItemIds,
                magicDust = session.magicDust,
                capacityLevel = session.hutchCapacityLevel,
                inventoryItemCount = totalInventoryItems,
                onToggleLock = { itemId -> viewModel.toggleLockItem(session.id, itemId) },
                onSellPet = { itemId -> viewModel.sellPet(session.id, itemId) },
                onUpgrade = { viewModel.upgradePetHutch(session.id) },
                onMoveToInventory = { petId -> viewModel.movePetFromHutch(session.id, petId) })
            FeedingTroughCard(
                crops = session.feedingTrough,
                produce = session.inventory.produce,
                apiReady = state.apiReady,
                showTip = state.showTroughTip,
                onDismissTip = { viewModel.dismissTroughTip() },
                onAddItems = { items ->
                    viewModel.putItemsInFeedingTrough(session.id, items)
                },
                onRemoveItem = { itemId ->
                    viewModel.removeItemFromFeedingTrough(session.id, itemId)
                },
            )
        }
        NavSection.SOCIAL -> {
            com.mgafk.app.ui.screens.social.PublicRoomsCard(
                rooms = state.publicRooms,
                loading = state.publicRoomsLoading,
                currentRoomId = session.roomId.ifBlank { session.room },
                isConnected = session.status == SessionStatus.CONNECTED,
                onRefresh = { viewModel.fetchPublicRooms() },
                onJoin = { roomId -> viewModel.joinPublicRoom(session.id, roomId) },
            )
        }
        NavSection.MINI_GAMES -> {
            val casinoConnected = session.casinoApiKey.isNotBlank()
            var currentGame by remember { mutableStateOf<String?>(null) }

            // Login gate — blocks everything until authenticated
            if (!casinoConnected) {
                CasinoLoginGate(onLogin = { onCasinoLoginRequest(session.id) })
            } else if (currentGame != null) {
                // ── Game screen ──
                when (currentGame) {
                    "coinflip" -> CoinFlipGame(
                        casinoBalance = casinoState.casinoBalance,
                        result = casinoState.coinflipResult,
                        loading = casinoState.coinflipLoading,
                        error = casinoState.coinflipError,
                        onPlay = { amount, choice -> casinoViewModel.playCoinflip(amount, choice) },
                        onReset = { casinoViewModel.resetCoinflip() },
                        onBack = {
                            casinoViewModel.resetCoinflip()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                        onResultShown = { casinoViewModel.applyCoinflipResult() },
                    )
                    "slots" -> SlotsGame(
                        casinoBalance = casinoState.casinoBalance,
                        result = casinoState.slotsResult,
                        loading = casinoState.slotsLoading,
                        error = casinoState.slotsError,
                        onPlay = { amount, machines -> casinoViewModel.playSlots(amount, machines) },
                        onReset = { casinoViewModel.resetSlots() },
                        onBack = {
                            casinoViewModel.resetSlots()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                        onResultShown = { casinoViewModel.applySlotsResult() },
                    )
                    "dice" -> DiceGame(
                        casinoBalance = casinoState.casinoBalance,
                        result = casinoState.diceResult,
                        loading = casinoState.diceLoading,
                        error = casinoState.diceError,
                        onPlay = { amount, target, direction -> casinoViewModel.playDice(amount, target, direction) },
                        onReset = { casinoViewModel.resetDice() },
                        onBack = {
                            casinoViewModel.resetDice()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                        onResultShown = { casinoViewModel.applyDiceResult() },
                    )
                    "crash" -> {
                        val myPlayer = session.playersList.find { it.id == session.playerId }
                        CrashGame(
                            casinoBalance = casinoState.casinoBalance,
                            state = casinoState.crash,
                            playerSnapshot = myPlayer,
                            gameVersion = session.gameVersion,
                            gameHost = session.gameUrl,
                            onStart = { amount -> casinoViewModel.startCrash(amount) },
                            onCashout = { casinoViewModel.cashoutCrash() },
                            onReset = { casinoViewModel.resetCrash() },
                            onBack = {
                                casinoViewModel.resetCrash()
                                casinoViewModel.fetchCasinoBalance()
                                currentGame = null
                            },
                        )
                    }
                    "blackjack" -> BlackjackGame(
                        casinoBalance = casinoState.casinoBalance,
                        state = casinoState.blackjack,
                        onStart = { amount -> casinoViewModel.startBlackjack(amount) },
                        onHit = { casinoViewModel.blackjackHit() },
                        onStand = { casinoViewModel.blackjackStand() },
                        onDouble = { casinoViewModel.blackjackDouble() },
                        onSplit = { casinoViewModel.blackjackSplit() },
                        onReset = { casinoViewModel.resetBlackjack() },
                        onBack = {
                            casinoViewModel.resetBlackjack()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                    )
                    "mines" -> MinesGame(
                        casinoBalance = casinoState.casinoBalance,
                        state = casinoState.mines,
                        onStart = { amount, mineCount -> casinoViewModel.startMines(amount, mineCount) },
                        onReveal = { position -> casinoViewModel.revealMines(position) },
                        onCashout = { casinoViewModel.cashoutMines() },
                        onReset = { casinoViewModel.resetMines() },
                        onBack = {
                            casinoViewModel.resetMines()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                    )
                    "egghatch" -> EggHatchGame(
                        casinoBalance = casinoState.casinoBalance,
                        eggs = casinoState.eggs,
                        eggConfig = casinoState.eggConfig,
                        eggsLoading = casinoState.eggsLoading,
                        result = casinoState.eggHatchResult,
                        loading = casinoState.eggHatchLoading,
                        error = casinoState.eggHatchError,
                        onFetchEggs = { casinoViewModel.fetchEggs() },
                        onPlay = { eggType, count -> casinoViewModel.playEggHatch(eggType, count) },
                        onReset = { casinoViewModel.resetEggHatch() },
                        onBack = {
                            casinoViewModel.resetEggHatch()
                            casinoViewModel.fetchCasinoBalance()
                            currentGame = null
                        },
                        onResultShown = { casinoViewModel.applyEggHatchResult() },
                    )
                }

                // Game conflict dialog (409 — active game)
                val conflict = casinoState.gameConflict
                if (conflict != null) {
                    GameConflictDialog(
                        conflict = conflict,
                        onForfeit = { casinoViewModel.forfeitAndRetry() },
                        onDismiss = { casinoViewModel.dismissConflict() },
                    )
                }
            } else {
                // ── Main mini games hub ──
                var walletMode by remember { mutableStateOf<String?>(null) }

                BalanceCard(
                    gameBalance = state.currencyBalance,
                    gameBalanceLoading = state.currencyBalanceLoading,
                    gameBalanceError = state.currencyBalanceError,
                    casinoBalance = casinoState.casinoBalance,
                    casinoBalanceLoading = casinoState.casinoBalanceLoading,
                    casinoConnected = true,
                    onRefreshGameBalance = { viewModel.fetchCurrencyBalance(session.id) },
                    onRefreshCasinoBalance = { casinoViewModel.fetchCasinoBalance() },
                    onConnectCasino = {},
                    onDeposit = {
                        casinoViewModel.fetchDepositConfig()
                        walletMode = "deposit"
                    },
                    onWithdraw = {
                        viewModel.fetchCurrencyBalance(session.id)
                        walletMode = "withdraw"
                    },
                )

                // Show WalletCard only when deposit/withdraw is active
                if (walletMode != null || casinoState.deposit.active || casinoState.withdraw.status.isNotEmpty()) {
                    WalletCard(
                        deposit = casinoState.deposit,
                        depositConfig = casinoState.depositConfig,
                        depositConfigLoading = casinoState.depositConfigLoading,
                        withdraw = casinoState.withdraw,
                        onRequestDeposit = { amount -> casinoViewModel.requestDeposit(amount) },
                        onCancelDeposit = { casinoViewModel.cancelDeposit() },
                        onRefreshDeposit = { casinoViewModel.refreshDeposit() },
                        onResetDeposit = { casinoViewModel.resetDeposit(); walletMode = null },
                        onRequestWithdraw = { amount -> casinoViewModel.requestWithdraw(amount) },
                        onResetWithdraw = { casinoViewModel.resetWithdraw(); walletMode = null },
                        gameBalance = state.currencyBalance,
                        casinoBalance = casinoState.casinoBalance,
                        gameBalanceLoading = state.currencyBalanceLoading,
                        onRefreshGameBalance = { viewModel.fetchCurrencyBalance(session.id) },
                        initialMode = walletMode,
                    )
                }

                GamesGrid(onGameClick = { gameId -> currentGame = gameId })
                HistoryCard(
                    transactions = casinoState.transactions,
                    loading = casinoState.transactionsLoading,
                    onLoad = { casinoViewModel.fetchTransactions() },
                )
            }
        }
        NavSection.ALERTS -> {
            AlertsCards(
                alerts = state.alerts,
                apiReady = state.apiReady,
                onToggle = { key, enabled ->
                    viewModel.updateAlerts { config ->
                        val items = config.items.toMutableMap()
                        // Preserve any existing mode — only flip the enabled flag.
                        val current = items[key] ?: AlertItem()
                        items[key] = current.copy(enabled = enabled)
                        config.copy(items = items)
                    }
                },
                onSectionModeChange = { section, mode ->
                    viewModel.updateAlerts { config ->
                        config.copy(sectionModes = config.sectionModes + (section.key to mode))
                    }
                },
                onItemModeChange = { key, mode ->
                    viewModel.updateAlerts { config ->
                        val items = config.items.toMutableMap()
                        val current = items[key] ?: AlertItem()
                        items[key] = current.copy(mode = mode)
                        config.copy(items = items)
                    }
                },
                onCollapseChange = { key, collapsed ->
                    viewModel.updateAlerts { config ->
                        config.copy(collapsed = config.collapsed + (key to collapsed))
                    }
                },
                onPetThresholdChange = { threshold ->
                    viewModel.updateAlerts { config ->
                        config.copy(petHungerThreshold = threshold)
                    }
                },
            )
        }
        NavSection.SETTINGS -> {
            SettingsCards(
                settings = state.settings,
                availableStorages = session.availableStorages,
                onUpdate = { newSettings -> viewModel.updateSettings { newSettings } },
            )
        }
        NavSection.DEBUG -> {
            DebugCards(
                session = session,
                serviceLogs = state.serviceLogs,
                onTestAlert = { mode -> viewModel.testAlert(mode) },
                onClearWsLogs = { viewModel.clearWsLogs(session.id) },
                onClearServiceLogs = { viewModel.clearServiceLogs() },
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

// ── Play in game (opens WebView with cookie + Gemini userscript injected) ──

@Composable
private fun PlayInGameCard(
    canPlay: Boolean,
    injectGemini: Boolean,
    onToggleInjectGemini: (Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    com.mgafk.app.ui.components.AppCard(
        title = "Play in game",
        collapsible = true,
        persistKey = "dashboard_play",
    ) {
        Text(
            "Open the game in-app with your session cookie. AFK pauses while you play.",
            fontSize = 11.sp,
            color = TextMuted,
            lineHeight = 15.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Inject Gemini toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceBorder.copy(alpha = 0.2f))
                .clickable { onToggleInjectGemini(!injectGemini) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Inject Gemini mod",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    if (injectGemini) "Latest release auto-downloaded from GitHub."
                    else "Vanilla game, no userscript.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 13.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material3.Switch(
                checked = injectGemini,
                onCheckedChange = onToggleInjectGemini,
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Accent),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        androidx.compose.material3.Button(
            onClick = onPlay,
            enabled = canPlay,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = StatusConnected,
                disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                if (canPlay) (if (injectGemini) "Play with Gemini" else "Play vanilla")
                else "Set cookie & room first",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (canPlay) androidx.compose.ui.graphics.Color.White
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
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

// ── Section tip (dismissable, shown once) ──

@Composable
private fun SectionTip(visible: Boolean, text: String, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Accent.copy(alpha = 0.1f))
                .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text,
                    fontSize = 11.sp,
                    color = Accent,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent,
                    modifier = Modifier.clickable { onDismiss() })
            }
        }
    }
}
