package com.mgafk.app.ui.screens.shops

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.PurchaseMode
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.rarityBorder
import kotlinx.coroutines.delay

private val StatusError = Color(0xFFF87171)

// Game-authentic rarity colors (from Gemini theme definitions)
private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

private val SHOP_SECTIONS = listOf(
    "Seeds" to "seed",
    "Tools" to "tool",
    "Eggs" to "egg",
    "Decors" to "decor",
)

/** Emits one AppCard per shop category. Call inside a Column with spacedBy. */
@Composable
fun ShopsCards(
    shops: List<ShopSnapshot>,
    apiReady: Boolean = false,
    purchaseMode: PurchaseMode = PurchaseMode.HYBRID,
    purchaseError: String = "",
    showTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    onBuy: (shopType: String, itemName: String) -> Unit = { _, _ -> },
    onBuyAll: (shopType: String, itemName: String) -> Unit = { _, _ -> },
) {
    if (shops.isEmpty()) {
        AppCard(title = "Shops") {
            Text("No shop data yet.", fontSize = 12.sp, color = TextMuted)
        }
        return
    }

    // First-time tip
    AnimatedVisibility(
        visible = showTip,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Accent.copy(alpha = 0.1f))
                .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .clickable { onDismissTip() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val tipText = when (purchaseMode) {
                        PurchaseMode.SINGLE -> "Tap an item to buy x1."
                        PurchaseMode.BULK -> "Tap an item to buy all remaining stock."
                        PurchaseMode.HYBRID -> "Tap an item to buy x1. Hold to buy all remaining stock."
                    }
                    Text(
                        text = tipText,
                        fontSize = 11.sp,
                        color = Accent,
                        lineHeight = 15.sp,
                    )
                }
                Text(
                    text = "OK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    modifier = Modifier.clickable { onDismissTip() },
                )
            }
        }
    }

    // Purchase error banner
    AnimatedVisibility(
        visible = purchaseError.isNotBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StatusError.copy(alpha = 0.15f))
                .border(1.dp, StatusError.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = purchaseError,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = StatusError,
            )
        }
    }

    SHOP_SECTIONS.forEach { (label, key) ->
        val shop = shops.find { it.type == key }
        ShopCategoryCard(
            label = label,
            shop = shop,
            apiReady = apiReady,
            purchaseMode = purchaseMode,
            onBuy = { itemName -> onBuy(key, itemName) },
            onBuyAll = { itemName -> onBuyAll(key, itemName) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopCategoryCard(
    label: String,
    shop: ShopSnapshot?,
    apiReady: Boolean,
    purchaseMode: PurchaseMode,
    onBuy: (itemName: String) -> Unit,
    onBuyAll: (itemName: String) -> Unit,
) {
    val items = shop?.itemNames ?: emptyList()
    val restockSec = shop?.secondsUntilRestock ?: 0

    AppCard(
        title = label,
        trailing = { RestockTimer(restockSec) },
        collapsible = true,
        persistKey = "shops.${shop?.type ?: label}",
    ) {
        if (items.isEmpty()) {
            Text("Empty", fontSize = 11.sp, color = TextMuted)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { itemName ->
                    val initialStock = shop?.initialStocks?.get(itemName) ?: 0
                    val remaining = shop?.itemStocks?.get(itemName) ?: 0
                    ShopItemTile(
                        itemName = itemName,
                        stock = remaining,
                        inShop = initialStock > 0,
                        soldOut = remaining <= 0,
                        apiReady = apiReady,
                        purchaseMode = purchaseMode,
                        onBuy = { onBuy(itemName) },
                        onBuyAll = { onBuyAll(itemName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopItemTile(
    itemName: String,
    stock: Int,
    inShop: Boolean,
    soldOut: Boolean,
    apiReady: Boolean,
    purchaseMode: PurchaseMode,
    onBuy: () -> Unit,
    onBuyAll: () -> Unit,
) {
    val entry = remember(itemName, apiReady) { MgApi.findItem(itemName) }
    val displayName = entry?.name ?: itemName
    val spriteUrl = entry?.sprite
    val rarity = entry?.rarity
    val color = rarityColor(rarity)
    val tileAlpha = if (soldOut) 0.35f else 1f
    val borderAlpha = if (soldOut) 0.3f else 0.5f

    Box(
        modifier = Modifier.size(76.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (inShop) Modifier.rarityBorder(rarity = rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = borderAlpha)
                    else Modifier.border(1.5.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                )
                .background(SurfaceDark)
                .then(
                    if (!soldOut) when (purchaseMode) {
                        PurchaseMode.SINGLE -> Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onBuy() })
                        }
                        PurchaseMode.BULK -> Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onBuyAll() })
                        }
                        PurchaseMode.HYBRID -> Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onBuy() },
                                onLongPress = { onBuyAll() },
                            )
                        }
                    } else Modifier
                )
                .alpha(tileAlpha)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpriteImage(url = spriteUrl, size = 32.dp, contentDescription = displayName)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = displayName,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (soldOut) TextMuted else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp,
            )
        }

        // Stock badge — notification style, overlapping top-end corner
        if (stock > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$stock",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = SurfaceDark,
                    textAlign = TextAlign.Center,
                    lineHeight = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun RestockTimer(initialSeconds: Int) {
    var remaining by remember(initialSeconds) { mutableStateOf(initialSeconds) }
    LaunchedEffect(initialSeconds) {
        while (remaining > 0) { delay(1000); remaining-- }
    }

    val color = if (remaining <= 60) Accent else Accent.copy(alpha = 0.6f)

    Text(
        text = "%02d:%02d".format(remaining / 60, remaining % 60),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        color = color,
    )
}
