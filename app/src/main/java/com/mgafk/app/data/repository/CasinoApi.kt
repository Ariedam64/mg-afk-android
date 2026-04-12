package com.mgafk.app.data.repository

import com.mgafk.app.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import com.mgafk.app.data.AppJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ── Response models ──

@Serializable
data class CasinoBalanceResponse(val balance: Long)

@Serializable
data class DepositRequestResponse(
    val depositId: Long,
    val command: String,
    val amount: Long,
    val createdAt: String,
    val expiresAt: String,
)

@Serializable
data class DepositStatusResponse(val deposit: DepositInfo?)

@Serializable
data class DepositInfo(
    val id: Long,
    val amount: Long,
    val receivedAmount: Long = 0,
    val refundedAmount: Long = 0,
    val status: String, // pending | confirmed | expired | cancelled
    val createdAt: String,
    val expiresAt: String,
    val confirmedAt: String? = null,
)

@Serializable
data class DepositCancelResponse(
    val cancelled: Boolean,
    val depositId: Long,
    val refunded: Long = 0,
)

@Serializable
data class DepositConfigResponse(
    val account: DepositAccount,
    val limits: DepositLimits,
)

@Serializable
data class DepositAccount(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String,
)

@Serializable
data class DepositLimits(
    val maxDeposit: Long,
    val dailyDepositLimit: Long,
    val depositExpiryMinutes: Int,
)

@Serializable
data class WithdrawResponse(
    val success: Boolean,
    val withdrawId: Long,
    val position: Int,
    val message: String,
)

@Serializable
data class WithdrawStatusResponse(
    val status: String, // "pending" | "completed" | "failed"
    val position: Int = 0,
)

@Serializable
data class TransactionHistoryResponse(val transactions: List<Transaction>)

@Serializable
data class Transaction(
    val id: Long,
    val from_player: String? = null,
    val to_player: String? = null,
    val ref_id: String? = null,
    val amount: Long,
    val type: String, // deposit | withdraw | bet | win | transfer
    val created_at: String,
)

@Serializable
data class CoinflipResponse(
    val game: String,
    val result: String, // "heads" | "tails"
    val choice: String,
    val won: Boolean,
    val bet: Long,
    val payout: Long,
    val newBalance: Long,
)

@Serializable
data class SlotsMachineResult(
    val reels: List<String>,
    val payline: String, // "3-of-a-kind" | "2-of-a-kind" | "none"
    val multiplier: Double,
    val won: Boolean,
    val payout: Long,
)

@Serializable
data class SlotsResponse(
    val game: String,
    val machines: List<SlotsMachineResult>,
    val totalBet: Long,
    val totalPayout: Long,
    val won: Boolean,
    val newBalance: Long,
)

@Serializable
data class MinesStartResponse(
    val game: String,
    val gridSize: Int,
    val mineCount: Int,
    val bet: Long,
    val newBalance: Long,
    val nextMultiplier: Double,
)

@Serializable
data class MinesRevealResponse(
    val game: String,
    val result: String, // "safe" | "mine"
    val position: Int,
    val revealed: List<Int> = emptyList(),
    val mines: List<Int> = emptyList(),
    val currentMultiplier: Double = 0.0,
    val currentPayout: Long = 0,
    val nextMultiplier: Double = 0.0,
    val safeRemaining: Int = 0,
    val won: Boolean? = null,
    val allRevealed: Boolean = false,
    val payout: Long = 0,
    val multiplier: Double = 0.0,
    val newBalance: Long = 0,
)

@Serializable
data class MinesCashoutResponse(
    val game: String,
    val won: Boolean,
    val multiplier: Double,
    val bet: Long,
    val payout: Long,
    val newBalance: Long,
    val mines: List<Int> = emptyList(),
    val revealed: List<Int> = emptyList(),
)

@Serializable
data class DiceResponse(
    val game: String,
    val roll: Int,
    val target: Int,
    val direction: String,
    val winChance: Int,
    val multiplier: Double,
    val won: Boolean,
    val bet: Long,
    val payout: Long,
    val newBalance: Long,
)

@Serializable
data class CrashStartResponse(
    val game: String,
    val bet: Long,
    val newBalance: Long,
    val growthRate: Double,
)

@Serializable
data class CrashStatusResponse(
    val game: String,
    val status: String, // "running" | "crashed"
    val multiplier: Double,
    val elapsed: Long = 0,
    val crashPoint: Double = 0.0,
    val won: Boolean = false,
    val payout: Long = 0,
)

@Serializable
data class CrashCashoutResponse(
    val game: String,
    val won: Boolean,
    val multiplier: Double,
    val crashPoint: Double,
    val bet: Long = 0,
    val payout: Long = 0,
    val newBalance: Long = 0,
)

@Serializable
data class BlackjackCard(
    val rank: String,
    val suit: String,
)

@Serializable
data class BlackjackPlayerHand(
    val cards: List<BlackjackCard>,
    val value: Int,
    val blackjack: Boolean = false,
)

@Serializable
data class BlackjackDealerHand(
    val cards: List<BlackjackCard>,
    val value: Int = 0,
    val visibleValue: Int = 0,
    val blackjack: Boolean = false,
)

@Serializable
data class BlackjackResponse(
    val game: String,
    val status: String, // "playing" | "done"
    val result: String? = null, // "blackjack" | "win" | "lose" | "push" | "bust" | "dealer_bust" | "dealer_blackjack"
    val player: BlackjackPlayerHand,
    val dealer: BlackjackDealerHand,
    val bet: Long = 0,
    val payout: Long = 0,
    val newBalance: Long = 0,
    val canDouble: Boolean = false,
)

@Serializable
data class ForfeitResponse(
    val game: String = "",
    val forfeited: Boolean = true,
)

// ── Egg Hatch models ──

@Serializable
data class EggHatchAbility(
    val key: String,
    val name: String,
    val chance: Double = 0.0,
    val bonus: Double = 0.0,
)

@Serializable
data class EggHatchPet(
    val pet: String,
    val petSprite: String,
    val petRarity: String,
    val abilities: List<EggHatchAbility> = emptyList(),
    val bonusAbilities: List<EggHatchAbility> = emptyList(),
    val mutation: String = "Normal",
    val mutationMultiplier: Int = 1,
    val maxSTR: Int = 0,
    val baseSTR: Int = 0,
    val abilityMult: Double = 0.0,
    val strFactor: Double = 0.0,
    val payout: Long = 0,
)

@Serializable
data class EggHatchResponse(
    val game: String,
    val eggType: String,
    val eggSprite: String,
    val eggRarity: String,
    val eggPrice: Long,
    val count: Int,
    val hatches: List<EggHatchPet>,
    val totalBet: Long,
    val totalPayout: Long,
    val newBalance: Long,
)

@Serializable
data class EggListAbility(
    val key: String,
    val name: String,
    val chance: Double = 0.0,
    val bonus: Double = 0.0,
)

@Serializable
data class EggListPet(
    val name: String,
    val weight: Int = 0,
    val chance: String = "",
    val baseMult: Double = 0.0,
    val rarity: String = "",
    val sprite: String = "",
    val abilities: List<EggListAbility> = emptyList(),
)

@Serializable
data class EggInfo(
    val key: String,
    val name: String,
    val price: Long,
    val sprite: String,
    val rarity: String,
    val pets: List<EggListPet> = emptyList(),
)

@Serializable
data class EggHatchConfig(
    val goldChance: Double = 0.02,
    val rainbowChance: Double = 0.002,
    val goldMultiplier: Int = 50,
    val rainbowMultiplier: Int = 100,
    val evMultiplier: Int = 1,
    val maxHatches: Int = 100,
    val strRange: EggStrRange = EggStrRange(),
    val strFactorRange: EggStrFactorRange = EggStrFactorRange(),
)

@Serializable
data class EggStrRange(val min: Int = 80, val max: Int = 100)

@Serializable
data class EggStrFactorRange(val min: Double = 0.95, val max: Double = 1.05)

@Serializable
data class EggListResponse(
    val eggs: List<EggInfo>,
    val config: EggHatchConfig = EggHatchConfig(),
)

@Serializable
data class CasinoErrorResponse(val error: String? = null, val balance: Long? = null)

// ── API client ──

object CasinoApi {

    private const val TAG = "CasinoApi"
    private const val BASE_URL = "https://ariesmod-api.ariedam.fr"
    private val JSON_MEDIA = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = AppJson.default

    /** Fetch casino balance */
    suspend fun getBalance(apiKey: String): Result<Long> = safeCall(apiKey, "getBalance") {
        val request = get("/deposits/balance", apiKey)
        val body = execute(request)
        json.decodeFromString<CasinoBalanceResponse>(body).balance
    }

    /** Fetch deposit config (public, no auth needed) */
    suspend fun getDepositConfig(): Result<DepositConfigResponse> = try {
        val request = Request.Builder().url("$BASE_URL/deposits/config").build()
        val body = execute(request)
        Result.success(json.decodeFromString<DepositConfigResponse>(body))
    } catch (e: Exception) {
        AppLog.e(TAG, "[getDepositConfig] Error: ${e.message}", e)
        Result.failure(e)
    }

    /** Create a deposit request */
    suspend fun requestDeposit(apiKey: String, amount: Long): Result<DepositRequestResponse> =
        safeCall(apiKey, "requestDeposit") {
            val request = post("/deposits/request", apiKey, """{"amount":$amount}""")
            val body = execute(request)
            json.decodeFromString<DepositRequestResponse>(body)
        }

    /** Poll deposit status */
    suspend fun getDepositStatus(apiKey: String): Result<DepositInfo?> =
        safeCall(apiKey, "getDepositStatus") {
            val request = get("/deposits/status", apiKey)
            val body = execute(request)
            json.decodeFromString<DepositStatusResponse>(body).deposit
        }

    /** Cancel pending deposit */
    suspend fun cancelDeposit(apiKey: String): Result<DepositCancelResponse> =
        safeCall(apiKey, "cancelDeposit") {
            val request = post("/deposits/cancel", apiKey, null)
            val body = execute(request)
            json.decodeFromString<DepositCancelResponse>(body)
        }

    /** Withdraw breads */
    suspend fun withdraw(apiKey: String, amount: Long): Result<WithdrawResponse> =
        safeCall(apiKey, "withdraw") {
            val request = post("/deposits/withdraw", apiKey, """{"amount":$amount}""")
            val body = execute(request)
            json.decodeFromString<WithdrawResponse>(body)
        }

    /** Fetch transaction history */
    suspend fun getHistory(apiKey: String, limit: Int = 20): Result<List<Transaction>> =
        safeCall(apiKey, "getHistory") {
            val request = get("/deposits/history?limit=$limit", apiKey)
            val body = execute(request)
            json.decodeFromString<TransactionHistoryResponse>(body).transactions
        }

    /** Poll withdraw status */
    suspend fun getWithdrawStatus(apiKey: String, withdrawId: Long): Result<WithdrawStatusResponse> =
        safeCall(apiKey, "withdrawStatus") {
            val request = get("/deposits/withdraw/status/$withdrawId", apiKey)
            val body = execute(request)
            json.decodeFromString<WithdrawStatusResponse>(body)
        }

    // ── Games ──

    /** Play dice */
    suspend fun playDice(apiKey: String, amount: Long, target: Int, direction: String): Result<DiceResponse> =
        safeCall(apiKey, "dice") {
            val request = post("/games/dice", apiKey, """{"amount":$amount,"target":$target,"direction":"$direction"}""")
            val body = execute(request)
            json.decodeFromString<DiceResponse>(body)
        }

    /** Start crash game */
    suspend fun startCrash(apiKey: String, amount: Long): Result<CrashStartResponse> =
        safeCall(apiKey, "crashStart") {
            val request = post("/games/crash/start", apiKey, """{"amount":$amount}""")
            val body = execute(request)
            json.decodeFromString<CrashStartResponse>(body)
        }

    /** Get crash game status */
    suspend fun getCrashStatus(apiKey: String): Result<CrashStatusResponse> =
        safeCall(apiKey, "crashStatus") {
            val request = get("/games/crash/status", apiKey)
            val body = execute(request)
            json.decodeFromString<CrashStatusResponse>(body)
        }

    /** Forfeit crash game */
    suspend fun forfeitCrash(apiKey: String): Result<ForfeitResponse> =
        safeCall(apiKey, "crashForfeit") {
            val request = post("/games/crash/forfeit", apiKey, null)
            val body = execute(request)
            json.decodeFromString<ForfeitResponse>(body)
        }

    /** Cashout crash game */
    suspend fun cashoutCrash(apiKey: String): Result<CrashCashoutResponse> =
        safeCall(apiKey, "crashCashout") {
            val request = post("/games/crash/cashout", apiKey, null)
            val body = execute(request)
            json.decodeFromString<CrashCashoutResponse>(body)
        }

    /** Start blackjack game */
    suspend fun startBlackjack(apiKey: String, amount: Long): Result<BlackjackResponse> =
        safeCall(apiKey, "blackjackStart") {
            val request = post("/games/blackjack/start", apiKey, """{"amount":$amount}""")
            val body = execute(request)
            json.decodeFromString<BlackjackResponse>(body)
        }

    /** Forfeit blackjack game */
    suspend fun forfeitBlackjack(apiKey: String): Result<ForfeitResponse> =
        safeCall(apiKey, "blackjackForfeit") {
            val request = post("/games/blackjack/forfeit", apiKey, null)
            val body = execute(request)
            json.decodeFromString<ForfeitResponse>(body)
        }

    /** Blackjack hit */
    suspend fun blackjackHit(apiKey: String): Result<BlackjackResponse> =
        safeCall(apiKey, "blackjackHit") {
            val request = post("/games/blackjack/hit", apiKey, null)
            val body = execute(request)
            json.decodeFromString<BlackjackResponse>(body)
        }

    /** Blackjack stand */
    suspend fun blackjackStand(apiKey: String): Result<BlackjackResponse> =
        safeCall(apiKey, "blackjackStand") {
            val request = post("/games/blackjack/stand", apiKey, null)
            val body = execute(request)
            json.decodeFromString<BlackjackResponse>(body)
        }

    /** Blackjack double */
    suspend fun blackjackDouble(apiKey: String): Result<BlackjackResponse> =
        safeCall(apiKey, "blackjackDouble") {
            val request = post("/games/blackjack/double", apiKey, null)
            val body = execute(request)
            json.decodeFromString<BlackjackResponse>(body)
        }

    /** Play coinflip */
    suspend fun playCoinflip(apiKey: String, amount: Long, choice: String): Result<CoinflipResponse> =
        safeCall(apiKey, "coinflip") {
            val request = post("/games/coinflip", apiKey, """{"amount":$amount,"choice":"$choice"}""")
            val body = execute(request)
            json.decodeFromString<CoinflipResponse>(body)
        }

    /** Play slots */
    suspend fun playSlots(apiKey: String, amount: Long, machines: Int = 1): Result<SlotsResponse> =
        safeCall(apiKey, "slots") {
            val request = post("/games/slots", apiKey, """{"amount":$amount,"machines":$machines}""")
            val body = execute(request)
            json.decodeFromString<SlotsResponse>(body)
        }

    /** Start mines game */
    suspend fun startMines(apiKey: String, amount: Long, mineCount: Int): Result<MinesStartResponse> =
        safeCall(apiKey, "minesStart") {
            val request = post("/games/mines/start", apiKey, """{"amount":$amount,"mineCount":$mineCount}""")
            val body = execute(request)
            json.decodeFromString<MinesStartResponse>(body)
        }

    /** Forfeit mines game */
    suspend fun forfeitMines(apiKey: String): Result<ForfeitResponse> =
        safeCall(apiKey, "minesForfeit") {
            val request = post("/games/mines/forfeit", apiKey, null)
            val body = execute(request)
            json.decodeFromString<ForfeitResponse>(body)
        }

    /** Reveal a cell */
    suspend fun revealMines(apiKey: String, position: Int): Result<MinesRevealResponse> =
        safeCall(apiKey, "minesReveal") {
            val request = post("/games/mines/reveal", apiKey, """{"position":$position}""")
            val body = execute(request)
            json.decodeFromString<MinesRevealResponse>(body)
        }

    /** Cashout mines */
    suspend fun cashoutMines(apiKey: String): Result<MinesCashoutResponse> =
        safeCall(apiKey, "minesCashout") {
            val request = post("/games/mines/cashout", apiKey, null)
            val body = execute(request)
            json.decodeFromString<MinesCashoutResponse>(body)
        }

    // ── Egg Hatch ──

    /** Fetch all egg types, their pets, and config (no auth needed) */
    suspend fun getEggs(): Result<EggListResponse> = try {
        val request = Request.Builder().url("$BASE_URL/games/egg-hatch/eggs").build()
        val body = execute(request)
        Result.success(json.decodeFromString<EggListResponse>(body))
    } catch (e: Exception) {
        AppLog.e(TAG, "[getEggs] Error: ${e.message}", e)
        Result.failure(e)
    }

    /** Hatch one or more eggs */
    suspend fun playEggHatch(apiKey: String, eggType: String, count: Int = 1): Result<EggHatchResponse> =
        safeCall(apiKey, "eggHatch") {
            val request = post("/games/egg-hatch", apiKey, """{"eggType":"$eggType","count":$count}""")
            val body = execute(request)
            json.decodeFromString<EggHatchResponse>(body)
        }

    // ── Internal helpers ──

    private fun get(path: String, apiKey: String): Request =
        Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", "Bearer $apiKey")
            .build()

    private fun post(path: String, apiKey: String, jsonBody: String?): Request {
        val body = jsonBody?.toRequestBody(JSON_MEDIA)
            ?: "".toRequestBody(JSON_MEDIA)
        return Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        AppLog.d(TAG, "[${request.method}] ${request.url} → ${response.code}")
        if (!response.isSuccessful) {
            val errorMsg = try {
                json.decodeFromString<CasinoErrorResponse>(body).error ?: "HTTP ${response.code}"
            } catch (_: Exception) {
                "HTTP ${response.code}: $body"
            }
            throw CasinoApiException(response.code, errorMsg)
        }
        body
    }

    private suspend fun <T> safeCall(apiKey: String, tag: String, block: suspend () -> T): Result<T> {
        return try {
            if (apiKey.isBlank()) return Result.failure(CasinoApiException(401, "No API key"))
            Result.success(block())
        } catch (e: CasinoApiException) {
            AppLog.e(TAG, "[$tag] API error ${e.code}: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "[$tag] Error: ${e.message}", e)
            Result.failure(e)
        }
    }
}

class CasinoApiException(val code: Int, message: String) : Exception(message)
