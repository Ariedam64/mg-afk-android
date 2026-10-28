package com.mgafk.app.ui.screens.shops

import androidx.compose.runtime.Composable
import com.mgafk.app.data.model.Tip
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** Shop stock and buying. */
@Composable
fun ShopsScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    ShopsCards(
        shops = session.shops,
        session = session,
        apiReady = state.apiReady,
        purchaseMode = state.settings.purchaseMode,
        purchaseError = state.purchaseError,
        showTip = Tip.SHOP in state.visibleTips,
        onDismissTip = { viewModel.dismissTip(Tip.SHOP) },
        onBuy = { shopType, itemName -> viewModel.shops.purchaseShopItem(session.id, shopType, itemName) },
        onBuyAll = { shopType, itemName -> viewModel.shops.purchaseAllShopItem(session.id, shopType, itemName) },
    )
}
