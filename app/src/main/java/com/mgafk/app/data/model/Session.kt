package com.mgafk.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Session 1",
    val autoName: Boolean = true,
    val cookie: String = "",
    val room: String = "",
    val gameUrl: String = "magicgarden.gg",
    val reconnect: ReconnectConfig = ReconnectConfig(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: SessionStatus = SessionStatus.IDLE,
    val error: String = "",
    val reconnectCountdown: String = "",
    val players: Int = 0,
    val connectedAt: Long = 0,
    val playerId: String = "",
    val playerName: String = "",
    val roomId: String = "",
    val weather: String = "",
    val pets: List<PetSnapshot> = emptyList(),
    val logs: List<AbilityLog> = emptyList(),
    val shops: List<ShopSnapshot> = emptyList(),
    val garden: List<GardenPlantSnapshot> = emptyList(),
    val gardenEggs: List<GardenEggSnapshot> = emptyList(),
    val inventory: InventorySnapshot = InventorySnapshot(),
    val seedSilo: List<InventorySeedItem> = emptyList(),
    val decorShed: List<InventoryDecorItem> = emptyList(),
    val petHutch: List<InventoryPetItem> = emptyList(),
    val feedingTrough: List<InventoryEggItem> = emptyList(),
)

@Serializable
enum class SessionStatus { IDLE, CONNECTING, CONNECTED, ERROR }

@Serializable
data class ReconnectConfig(
    val unknown: Boolean = true,
    val delays: ReconnectDelays = ReconnectDelays(),
    val codes: Map<Int, Boolean> = mapOf(
        4100 to true, 4200 to true, 4250 to true, 4300 to true,
        4310 to true, 4400 to true, 4500 to true, 4700 to true,
        4710 to true, 4800 to true,
    ),
)

@Serializable
data class ReconnectDelays(
    val supersededMs: Long = 30000,
    val otherMs: Long = 1500,
)

/** Serializable snapshot of a pet for Session persistence */
@Serializable
data class PetSnapshot(
    val id: String = "",
    val name: String = "",
    val species: String = "",
    val hunger: Double = 0.0,
    val index: Int = 0,
    val mutations: List<String> = emptyList(),
)

@Serializable
data class AbilityLog(
    val timestamp: Long = 0,
    val action: String = "",
    val petName: String = "",
    val petSpecies: String = "",
    val petMutations: List<String> = emptyList(),
    val slotIndex: Int = 0,
)

/** Inventory snapshot */
@Serializable
data class InventorySnapshot(
    val seeds: List<InventorySeedItem> = emptyList(),
    val eggs: List<InventoryEggItem> = emptyList(),
    val produce: List<InventoryProduceItem> = emptyList(),
    val pets: List<InventoryPetItem> = emptyList(),
    val tools: List<InventoryToolItem> = emptyList(),
    val decors: List<InventoryDecorItem> = emptyList(),
)

@Serializable
data class InventorySeedItem(
    val species: String = "",
    val quantity: Int = 0,
)

@Serializable
data class InventoryEggItem(
    val eggId: String = "",
    val quantity: Int = 0,
)

@Serializable
data class InventoryProduceItem(
    val species: String = "",
    val targetScale: Double = 0.0,
    val mutations: List<String> = emptyList(),
)

@Serializable
data class InventoryPetItem(
    val id: String = "",
    val petSpecies: String = "",
    val name: String? = null,
    val xp: Double = 0.0,
    val targetScale: Double = 0.0,
    val mutations: List<String> = emptyList(),
    val abilities: List<String> = emptyList(),
)

@Serializable
data class InventoryToolItem(
    val toolId: String = "",
    val quantity: Int = 0,
)

@Serializable
data class InventoryDecorItem(
    val decorId: String = "",
    val quantity: Int = 0,
)

/** Serializable snapshot of a garden egg for Session persistence */
@Serializable
data class GardenEggSnapshot(
    val tileId: Int = 0,
    val eggId: String = "",
    val plantedAt: Long = 0,
    val maturedAt: Long = 0,
)

/** Serializable snapshot of a garden plant for Session persistence */
@Serializable
data class GardenPlantSnapshot(
    val tileId: Int = 0,
    val species: String = "",
    val targetScale: Double = 0.0,
    val mutations: List<String> = emptyList(),
)

/** Serializable snapshot of a shop for Session persistence */
@Serializable
data class ShopSnapshot(
    val type: String = "",
    val itemNames: List<String> = emptyList(),
    val itemStocks: Map<String, Int> = emptyMap(),
    val secondsUntilRestock: Int = 0,
)
