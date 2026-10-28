package com.mgafk.app.ui.screens.storage

import com.mgafk.app.ui.theme.OnStatusError
import com.mgafk.app.ui.theme.OnStatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.GridOf

// ── Sell all pets dialog ──

@Composable
internal fun SellAllPetsDialog(
    pets: List<InventoryPetItem>,
    apiReady: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    data class Row4(val pet: InventoryPetItem, val name: String, val price: Long, val dust: Long)
    val petsWithPrices = remember(pets, apiReady) {
        pets.map { pet ->
            val entry = MgApi.findPet(pet.petSpecies)
            val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
            val price = PriceCalculator.calculatePetSellPrice(pet.petSpecies, pet.xp, pet.targetScale, pet.mutations) ?: 0L
            val dust = PriceCalculator.calculatePetDustValue(pet.petSpecies, pet.sourceEggId, pet.xp, pet.targetScale, pet.mutations) ?: 0L
            Row4(pet, name, price, dust)
        }
    }
    val totalValue = remember(petsWithPrices) { petsWithPrices.sumOf { it.price } }
    val totalDust = remember(petsWithPrices) { petsWithPrices.sumOf { it.dust } }

    var confirmed by remember { mutableStateOf(false) }

    if (confirmed) {
        // Show result
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(AppTheme.shapes.mediumShape)
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pets Sold!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${pets.size} pets", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = MgApi.coinBagUrl, size = 20.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatFull(totalValue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
                if (totalDust > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SpriteImage(url = MgApi.magicDustUrl, size = 18.dp, contentDescription = "dust")
                        Text(
                            PriceCalculator.formatFull(totalDust),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Nice!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusConnected)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sell All Unlocked Pets",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${pets.size} pets", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(12.dp))

            // Pet grid (scrollable, same style as inventory but with price)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                GridOf(pets.size) { i ->
                    PetTileWithPrice(pets[i], apiReady, petsWithPrices[i].price, petsWithPrices[i].dust)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = MgApi.coinBagUrl, size = 16.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatFull(totalValue),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }
            if (totalDust > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dust", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SpriteImage(url = MgApi.magicDustUrl, size = 14.dp, contentDescription = "dust")
                        Text(
                            PriceCalculator.formatFull(totalDust),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Button(
                    onClick = {
                        onConfirm()
                        confirmed = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Sell All", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusError)
                }
            }
        }
    }
}

// ── Sell all crops dialog ──

@Composable
internal fun SellAllCropsDialog(
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    playerCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val produceWithPrices = remember(produce, apiReady, playerCount) {
        produce.map { item ->
            val entry = MgApi.findItem(item.species)
            val name = entry?.name?.removeSuffix(" Seed") ?: item.species
            val price = PriceCalculator.calculateCropSellPrice(item.species, item.size, item.mutations, playerCount) ?: 0L
            Triple(item, name, price)
        }
    }
    val totalValue = remember(produceWithPrices) { produceWithPrices.sumOf { it.third } }
    val friendsBonus = PriceCalculator.friendsMultiplier(playerCount)

    var confirmed by remember { mutableStateOf(false) }

    if (confirmed) {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .clip(AppTheme.shapes.mediumShape)
                    .background(SurfaceCard)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Crops Sold!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${produce.size} crops", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = MgApi.coinBagUrl, size = 20.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatFull(totalValue),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Nice!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusConnected)
                }
            }
        }
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Sell All Crops", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${produce.size} crops", fontSize = 12.sp, color = TextSecondary)
                if (friendsBonus > 1.0) {
                    Text(
                        "x%.1f friends".format(friendsBonus),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusConnected,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crop list (scrollable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                produceWithPrices.forEach { (item, name, price) ->
                    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
                    val color = rarityColor(entry?.rarity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppTheme.shapes.smallShape)
                            .background(SurfaceDark)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            SpriteImage(category = "plants", name = item.species, size = 22.dp, contentDescription = name, mutations = item.mutations)
                            Text(name, fontSize = 10.sp, color = TextPrimary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            if (item.mutations.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    sortMutations(item.mutations).take(3).forEach { mutation ->
                                        SpriteImage(url = mutationSpriteUrl(mutation), size = 10.dp, contentDescription = mutation)
                                    }
                                }
                            }
                        }
                        if (price > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                SpriteImage(url = MgApi.coinBagUrl, size = 10.dp, contentDescription = "coins")
                                Text(
                                    PriceCalculator.formatFull(price),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpriteImage(url = MgApi.coinBagUrl, size = 16.dp, contentDescription = "coins")
                    Text(
                        PriceCalculator.formatFull(totalValue),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Button(
                    onClick = {
                        onConfirm()
                        confirmed = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text("Sell All", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnStatusError)
                }
            }
        }
    }
}
