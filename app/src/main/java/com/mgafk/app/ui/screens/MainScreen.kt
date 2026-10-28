package com.mgafk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState
import com.mgafk.app.ui.components.CardCollapseState
import com.mgafk.app.ui.components.LocalCardCollapseState
import com.mgafk.app.ui.components.ScreenTitle
import com.mgafk.app.ui.navigation.Destination
import com.mgafk.app.ui.navigation.FloatingNavBar
import com.mgafk.app.ui.navigation.FloatingNavBarClearance
import com.mgafk.app.ui.screens.alerts.AlertsScreen
import com.mgafk.app.ui.screens.bugreport.BugReportScreen
import com.mgafk.app.ui.screens.customize.CustomizeScreen
import com.mgafk.app.ui.screens.debug.DebugScreen
import com.mgafk.app.ui.screens.garden.GardenScreen
import com.mgafk.app.ui.screens.home.HomeScreen
import com.mgafk.app.ui.screens.more.MoreScreen
import com.mgafk.app.ui.screens.pets.PetsScreen
import com.mgafk.app.ui.screens.room.RoomScreen
import com.mgafk.app.ui.screens.settings.SettingsScreen
import com.mgafk.app.ui.screens.shops.ShopsScreen
import com.mgafk.app.ui.screens.social.SocialScreen
import com.mgafk.app.ui.screens.storage.StorageScreen
import com.mgafk.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The app shell: one screen at a time above the floating bottom bar.
 *
 * Every screen stays composed once built and the hidden ones are slid off to the side, so
 * switching keeps scroll positions and half-typed input. They are built one by one behind
 * the loading overlay the first time, which keeps the spinner animating on slow phones.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginRequest: (sessionId: String) -> Unit,
    onPlayRequest: (sessionId: String, cookie: String, room: String, gameUrl: String) -> Unit = { _, _, _, _ -> },
) {
    val state by viewModel.state.collectAsState()
    val session = state.activeSession
    val connected = session.status == SessionStatus.CONNECTED

    var currentName by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val current = Destination.valueOf(currentName)
    val navigate: (Destination) -> Unit = { currentName = it.name }

    // A screen that needs the game falls back to Home when the session drops.
    LaunchedEffect(connected, current) {
        if (current.requiresConnection && !connected) navigate(Destination.HOME)
    }
    BackHandler(enabled = current != Destination.HOME) {
        navigate(current.parent ?: Destination.HOME)
    }

    var built by remember { mutableStateOf(emptySet<Destination>()) }
    var allBuilt by remember { mutableStateOf(false) }
    var loadingStep by remember { mutableStateOf("") }
    LaunchedEffect(state.loadingStep) {
        if (state.loadingStep.isNotBlank()) loadingStep = state.loadingStep
    }
    LaunchedEffect(state.apiReady) {
        if (state.apiReady) {
            Destination.entries.forEach { destination ->
                loadingStep = "Preparing ${destination.label}…"
                built = built + destination
                delay(120) // yield frames so the spinner keeps turning
            }
            loadingStep = ""
            allBuilt = true
        }
    }

    val cardCollapseState = remember(state.collapsedCards) {
        CardCollapseState(
            collapsedCards = state.collapsedCards,
            onExpandedChange = { key, expanded -> viewModel.setCardExpanded(key, expanded) },
        )
    }

    CompositionLocalProvider(LocalCardCollapseState provides cardCollapseState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .clipToBounds(),
        ) {
            Destination.entries.forEach { destination ->
                if (destination in built) {
                    val visible = destination == current
                    val offset by animateFloatAsState(
                        targetValue = if (visible) 0f else 1f,
                        animationSpec = tween(250),
                        label = "screen_${destination.name}",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = offset * size.width
                                alpha = 1f - offset
                            },
                    ) {
                        ScreenFrame(destination) {
                            ScreenContent(destination, state, viewModel, visible, navigate, onLoginRequest, onPlayRequest)
                        }
                    }
                }
            }

            FloatingNavBar(
                selected = current,
                connected = connected,
                onSelect = navigate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )

            AnimatedVisibility(
                visible = !allBuilt,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(400)),
            ) {
                LoadingOverlay(step = loadingStep)
            }
        }
    }
}

/** Scrolling column with the theme's gutters, room for the bottom bar, and a title. */
@Composable
private fun ScreenFrame(destination: Destination, content: @Composable () -> Unit) {
    val spacing = AppTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = spacing.screen, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.gap),
    ) {
        content()
        Spacer(Modifier.navigationBarsPadding().height(FloatingNavBarClearance))
    }
}

@Composable
private fun ScreenContent(
    destination: Destination,
    state: UiState,
    viewModel: MainViewModel,
    visible: Boolean,
    navigate: (Destination) -> Unit,
    onLoginRequest: (sessionId: String) -> Unit,
    onPlayRequest: (sessionId: String, cookie: String, room: String, gameUrl: String) -> Unit,
) {
    if (destination != Destination.HOME) {
        ScreenTitle(
            title = destination.label,
            onBack = destination.parent?.let { parent -> { navigate(parent) } },
        )
    }
    when (destination) {
        Destination.HOME -> HomeScreen(state, viewModel, navigate, onLoginRequest, onPlayRequest)
        Destination.GARDEN -> GardenScreen(state, viewModel)
        Destination.PETS -> PetsScreen(state, viewModel)
        Destination.SHOPS -> ShopsScreen(state, viewModel)
        Destination.MORE -> MoreScreen(
            connected = state.activeSession.status == SessionStatus.CONNECTED,
            showDebug = state.settings.showDebugMenu,
            updateAvailable = state.updateAvailable,
            onNavigate = navigate,
        )
        Destination.ROOM -> RoomScreen(state, viewModel)
        Destination.STORAGE -> StorageScreen(state, viewModel)
        Destination.SOCIAL -> SocialScreen(state, viewModel)
        Destination.ALERTS -> AlertsScreen(state, viewModel)
        Destination.CUSTOMIZE -> CustomizeScreen(
            appearance = state.settings.appearance,
            onChange = viewModel::updateAppearance,
        )
        Destination.SETTINGS -> SettingsScreen(state, viewModel)
        Destination.REPORT_BUG -> BugReportScreen(state, viewModel, isVisible = visible)
        Destination.DEBUG -> DebugScreen(state, viewModel)
    }
}

@Composable
private fun LoadingOverlay(step: String) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("MG AFK", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = colors.accent, letterSpacing = (-0.5).sp)
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = colors.accent, trackColor = colors.surfaceHigh, strokeWidth = 3.dp)
            if (step.isNotBlank()) Text(step, fontSize = 13.sp, color = colors.textMuted)
        }
    }
}
