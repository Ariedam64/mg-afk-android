package com.mgafk.app.ui

import com.mgafk.app.data.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.data.repository.CasinoApi
import com.mgafk.app.data.repository.CasinoApiException
import com.mgafk.app.data.repository.DepositConfigResponse
import com.mgafk.app.data.repository.EggHatchConfig
import com.mgafk.app.data.repository.EggInfo
import com.mgafk.app.data.repository.Transaction
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlackjackUiState(
    val active: Boolean = false,
    val response: com.mgafk.app.data.repository.BlackjackResponse? = null,
    val initialBet: Long = 0, // Original per-hand bet chosen by the player — unaffected by Double/Split
    val loading: Boolean = false,
    val error: String? = null,
)

data class CrashUiState(
    val active: Boolean = false,
    val bet: Long = 0,
    val growthRate: Double = 0.00015,
    val startTime: Long = 0,
    val crashed: Boolean = false,
    val cashedOut: Boolean = false,
    val crashPoint: Double = 0.0,
    val multiplier: Double = 1.0,
    val won: Boolean = false,
    val payout: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

data class MinesUiState(
    val active: Boolean = false,
    val bet: Long = 0,
    val mineCount: Int = 5,
    val revealed: Set<Int> = emptySet(),
    val mines: List<Int> = emptyList(),
    val currentMultiplier: Double = 0.0,
    val currentPayout: Long = 0,
    val nextMultiplier: Double = 0.0,
    val safeRemaining: Int = 0,
    val gameOver: Boolean = false,
    val won: Boolean? = null,
    val payout: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Shown when a game start returns 409 (active game already exists).
 * [game] is "crash" | "blackjack" | "mines".
 * [pendingAmount] + [pendingExtra] hold the original start params so we can retry.
 */
data class GameConflict(
    val game: String = "",
    val pendingAmount: Long = 0,
    val pendingExtra: Int = 0,
    val loading: Boolean = false,
)

data class WithdrawUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val withdrawId: Long? = null,
    val position: Int = 0,
    val status: String = "",
    val message: String = "",
)

data class DepositUiState(
    val active: Boolean = false,
    val depositId: Long? = null,
    val command: String = "",
    val amount: Long = 0,
    val receivedAmount: Long = 0,
    val refundedAmount: Long = 0,
    val status: String = "",
    val expiresAt: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

data class CasinoUiState(
    val casinoBalance: Long? = null,
    val casinoBalanceLoading: Boolean = false,
    val depositConfig: DepositConfigResponse? = null,
    val depositConfigLoading: Boolean = false,
    val deposit: DepositUiState = DepositUiState(),
    val withdraw: WithdrawUiState = WithdrawUiState(),
    val transactions: List<Transaction> = emptyList(),
    val transactionsLoading: Boolean = false,
    // Coinflip
    val coinflipResult: com.mgafk.app.data.repository.CoinflipResponse? = null,
    val coinflipLoading: Boolean = false,
    val coinflipError: String? = null,
    // Slots
    val slotsResult: com.mgafk.app.data.repository.SlotsResponse? = null,
    val slotsLoading: Boolean = false,
    val slotsError: String? = null,
    // Dice
    val diceResult: com.mgafk.app.data.repository.DiceResponse? = null,
    val diceLoading: Boolean = false,
    val diceError: String? = null,
    // Crash
    val crash: CrashUiState = CrashUiState(),
    // Blackjack
    val blackjack: BlackjackUiState = BlackjackUiState(),
    // Mines
    val mines: MinesUiState = MinesUiState(),
    // Egg Hatch
    val eggHatchResult: com.mgafk.app.data.repository.EggHatchResponse? = null,
    val eggHatchLoading: Boolean = false,
    val eggHatchError: String? = null,
    val eggs: List<EggInfo> = emptyList(),
    val eggConfig: EggHatchConfig = EggHatchConfig(),
    val eggsLoading: Boolean = false,
    // Active game conflict (409)
    val gameConflict: GameConflict? = null,
)

class CasinoViewModel : ViewModel() {

    private companion object { const val TAG = "CasinoViewModel" }

    private val _state = MutableStateFlow(CasinoUiState())
    val state: StateFlow<CasinoUiState> = _state.asStateFlow()

    private var apiKey: String = ""

    fun setApiKey(key: String) {
        apiKey = key
        if (key.isNotBlank()) fetchCasinoBalance()
    }

    // ---- Balance ----

    fun fetchCasinoBalance() {
        _state.update { it.copy(casinoBalanceLoading = true) }
        viewModelScope.launch {
            CasinoApi.getBalance(apiKey)
                .onSuccess { balance ->
                    _state.update { it.copy(casinoBalance = balance, casinoBalanceLoading = false) }
                }
                .onFailure { e ->
                    AppLog.e(TAG, "[Casino] Balance failed: ${e.message}")
                    _state.update { it.copy(casinoBalanceLoading = false) }
                }
        }
    }

    // ---- Deposit ----

    fun fetchDepositConfig() {
        _state.update { it.copy(depositConfigLoading = true) }
        viewModelScope.launch {
            CasinoApi.getDepositConfig()
                .onSuccess { config ->
                    _state.update { it.copy(depositConfig = config, depositConfigLoading = false) }
                }
                .onFailure {
                    AppLog.e(TAG, "[DepositConfig] Failed: ${it.message}")
                    _state.update { it.copy(depositConfigLoading = false) }
                }
        }
    }

    private var depositPollJob: Job? = null

    fun requestDeposit(amount: Long) {
        _state.update { it.copy(deposit = it.deposit.copy(loading = true, error = null)) }
        viewModelScope.launch {
            CasinoApi.requestDeposit(apiKey, amount)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(deposit = DepositUiState(
                            active = true,
                            depositId = resp.depositId,
                            command = resp.command,
                            amount = resp.amount,
                            status = "pending",
                            expiresAt = resp.expiresAt,
                            loading = false,
                        ))
                    }
                    startDepositPolling()
                }
                .onFailure { e ->
                    _state.update { it.copy(deposit = it.deposit.copy(loading = false, error = e.message)) }
                }
        }
    }

    private fun startDepositPolling() {
        depositPollJob?.cancel()
        depositPollJob = viewModelScope.launch {
            AppLog.d(TAG, "[Deposit] Polling started")
            var consecutiveErrors = 0
            while (true) {
                delay(if (consecutiveErrors > 0) 5_000 else 3_000)
                val dep = _state.value.deposit
                if (!dep.active || dep.status != "pending") break

                // Stop polling if local timer shows expiry passed (covers long network outage)
                val expired = try { Instant.parse(dep.expiresAt).isBefore(Instant.now()) } catch (_: Exception) { false }
                if (expired) {
                    AppLog.d(TAG, "[Deposit] Local expiry reached, doing final status check")
                    CasinoApi.getDepositStatus(apiKey)
                        .onSuccess { info ->
                            if (info != null) {
                                _state.update { it.copy(deposit = it.deposit.copy(status = info.status, receivedAmount = info.receivedAmount, refundedAmount = info.refundedAmount)) }
                                if (info.status == "confirmed") { fetchCasinoBalance(); fetchTransactions() }
                            } else {
                                _state.update { it.copy(deposit = it.deposit.copy(status = "expired")) }
                            }
                        }
                        .onFailure {
                            _state.update { it.copy(deposit = it.deposit.copy(status = "expired")) }
                        }
                    break
                }

                CasinoApi.getDepositStatus(apiKey)
                    .onSuccess { info ->
                        consecutiveErrors = 0
                        if (info == null) {
                            _state.update { it.copy(deposit = DepositUiState()) }
                            return@launch
                        }
                        _state.update { it.copy(deposit = it.deposit.copy(status = info.status, receivedAmount = info.receivedAmount, refundedAmount = info.refundedAmount)) }
                        when (info.status) {
                            "confirmed" -> {
                                fetchCasinoBalance()
                                fetchTransactions()
                                return@launch
                            }
                            "expired", "cancelled" -> return@launch
                        }
                    }
                    .onFailure { e ->
                        consecutiveErrors++
                        AppLog.d(TAG, "[Deposit] Poll failed ($consecutiveErrors): ${e.message}")
                        // Keep polling — don't stop on transient network errors
                    }
            }
        }
    }

    fun refreshDeposit() {
        viewModelScope.launch {
            CasinoApi.getDepositStatus(apiKey)
                .onSuccess { info ->
                    if (info == null) {
                        _state.update { it.copy(deposit = DepositUiState()) }
                        return@launch
                    }
                    _state.update {
                        it.copy(deposit = it.deposit.copy(
                            status = info.status,
                            receivedAmount = info.receivedAmount,
                            refundedAmount = info.refundedAmount,
                        ))
                    }
                    if (info.status == "confirmed") {
                        fetchCasinoBalance()
                        fetchTransactions()
                    }
                }
                .onFailure { e ->
                    AppLog.e(TAG, "[Deposit] Refresh failed: ${e.message}")
                    _state.update { it.copy(deposit = it.deposit.copy(error = "Refresh failed: ${e.message}")) }
                }
        }
    }

    fun cancelDeposit() {
        viewModelScope.launch {
            CasinoApi.cancelDeposit(apiKey)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(deposit = it.deposit.copy(
                            status = "cancelled",
                            refundedAmount = resp.refunded,
                        ))
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(deposit = it.deposit.copy(error = e.message)) }
                }
        }
        depositPollJob?.cancel()
    }

    fun resetDeposit() {
        depositPollJob?.cancel()
        _state.update { it.copy(deposit = DepositUiState()) }
    }

    // ---- Withdraw ----

    private var withdrawPollJob: Job? = null

    fun requestWithdraw(amount: Long) {
        _state.update { it.copy(withdraw = WithdrawUiState(loading = true)) }
        viewModelScope.launch {
            CasinoApi.withdraw(apiKey, amount)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(withdraw = WithdrawUiState(
                            withdrawId = resp.withdrawId,
                            position = resp.position,
                            status = "pending",
                            message = resp.message,
                        ))
                    }
                    startWithdrawPolling(resp.withdrawId)
                }
                .onFailure { e ->
                    _state.update { it.copy(withdraw = WithdrawUiState(error = e.message)) }
                }
        }
    }

    private fun startWithdrawPolling(withdrawId: Long) {
        withdrawPollJob?.cancel()
        withdrawPollJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                CasinoApi.getWithdrawStatus(apiKey, withdrawId)
                    .onSuccess { resp ->
                        _state.update { it.copy(withdraw = it.withdraw.copy(status = resp.status, position = resp.position)) }
                        when (resp.status) {
                            "completed", "failed" -> {
                                fetchCasinoBalance()
                                fetchTransactions()
                                return@launch
                            }
                        }
                    }
                    .onFailure { e ->
                        AppLog.d(TAG, "[Withdraw] Poll failed: ${e.message}")
                        // Keep polling — don't stop on transient network errors
                    }
            }
        }
    }

    fun resetWithdraw() {
        withdrawPollJob?.cancel()
        _state.update { it.copy(withdraw = WithdrawUiState()) }
    }

    // ---- Transactions ----

    fun fetchTransactions() {
        _state.update { it.copy(transactionsLoading = true) }
        viewModelScope.launch {
            CasinoApi.getHistory(apiKey, limit = 50)
                .onSuccess { txs ->
                    _state.update { it.copy(transactions = txs, transactionsLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(transactionsLoading = false) }
                }
        }
    }

    // ---- Dice ----

    fun playDice(amount: Long, target: Int, direction: String) {
        _state.update { it.copy(diceLoading = true, diceError = null, diceResult = null) }
        viewModelScope.launch {
            CasinoApi.playDice(apiKey, amount, target, direction)
                .onSuccess { resp ->
                    _state.update { it.copy(diceResult = resp, diceLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(diceLoading = false, diceError = e.message) }
                }
        }
    }

    fun applyDiceResult() {
        val result = _state.value.diceResult ?: return
        _state.update { it.copy(casinoBalance = result.newBalance) }
        fetchTransactions()
    }

    fun resetDice() {
        _state.update { it.copy(diceResult = null, diceError = null, diceLoading = false) }
    }

    // ---- Crash ----

    private var crashPollJob: Job? = null

    fun startCrash(amount: Long) {
        _state.update { it.copy(crash = CrashUiState(loading = true)) }
        viewModelScope.launch {
            CasinoApi.startCrash(apiKey, amount)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            casinoBalance = resp.newBalance,
                            crash = CrashUiState(
                                active = true,
                                bet = resp.bet,
                                growthRate = resp.growthRate,
                                startTime = System.currentTimeMillis(),
                            ),
                        )
                    }
                    startCrashPolling()
                }
                .onFailure { e ->
                    if (e is CasinoApiException && e.code == 409) {
                        _state.update {
                            it.copy(
                                crash = CrashUiState(),
                                gameConflict = GameConflict(game = "crash", pendingAmount = amount),
                            )
                        }
                    } else {
                        _state.update { it.copy(crash = CrashUiState(error = e.message)) }
                    }
                }
        }
    }

    private fun startCrashPolling() {
        crashPollJob?.cancel()
        crashPollJob = viewModelScope.launch {
            var consecutiveErrors = 0
            while (true) {
                delay(if (consecutiveErrors > 2) 2_000 else 500)
                val current = _state.value.crash
                if (!current.active || current.crashed || current.cashedOut) break

                // Safety: if polling has failed 30+ times in a row (~60s of errors),
                // force-end the game to prevent infinite multiplier
                if (consecutiveErrors >= 30) {
                    AppLog.e(TAG, "[Crash] Too many consecutive errors ($consecutiveErrors), forcing game end")
                    _state.update {
                        it.copy(crash = it.crash.copy(
                            crashed = true,
                            won = false,
                            payout = 0,
                            error = "Connection lost — game ended",
                        ))
                    }
                    break
                }

                CasinoApi.getCrashStatus(apiKey)
                    .onSuccess { resp ->
                        consecutiveErrors = 0
                        if (resp.status == "crashed") {
                            _state.update {
                                it.copy(crash = it.crash.copy(
                                    crashed = true,
                                    crashPoint = resp.crashPoint,
                                    multiplier = resp.crashPoint,
                                    won = false,
                                    payout = 0,
                                ))
                            }
                            fetchTransactions()
                        }
                    }
                    .onFailure { e ->
                        if (e is CasinoApiException && e.code == 404) {
                            _state.update {
                                it.copy(crash = it.crash.copy(
                                    crashed = true,
                                    won = false,
                                    payout = 0,
                                ))
                            }
                        } else {
                            consecutiveErrors++
                            AppLog.d(TAG, "[Crash] Poll failed ($consecutiveErrors): ${e.message}")
                        }
                    }
            }
        }
    }

    fun cashoutCrash() {
        val current = _state.value.crash
        if (!current.active || current.crashed || current.cashedOut) return
        crashPollJob?.cancel()
        _state.update { it.copy(crash = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.cashoutCrash(apiKey)
                .onSuccess { resp ->
                    if (resp.won) {
                        _state.update {
                            it.copy(
                                casinoBalance = resp.newBalance,
                                crash = it.crash.copy(
                                    loading = false,
                                    cashedOut = true,
                                    won = true,
                                    multiplier = resp.multiplier,
                                    crashPoint = resp.crashPoint,
                                    payout = resp.payout,
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(crash = it.crash.copy(
                                loading = false,
                                crashed = true,
                                won = false,
                                crashPoint = resp.crashPoint,
                                multiplier = resp.crashPoint,
                                payout = 0,
                            ))
                        }
                    }
                    fetchTransactions()
                }
                .onFailure { e ->
                    // If cashout fails with 404/400, the game is already over server-side
                    val forceEnd = e is CasinoApiException && e.code in listOf(400, 404)
                    if (forceEnd) {
                        _state.update {
                            it.copy(crash = it.crash.copy(
                                loading = false,
                                crashed = true,
                                won = false,
                                payout = 0,
                                error = "Cashout failed: ${e.message}",
                            ))
                        }
                    } else {
                        // Network error — keep game alive so user can retry cashout
                        _state.update { it.copy(crash = it.crash.copy(loading = false, error = e.message)) }
                        startCrashPolling() // Restart polling since we cancelled it
                    }
                }
        }
    }

    fun resetCrash() {
        crashPollJob?.cancel()
        _state.update { it.copy(crash = CrashUiState()) }
    }

    // ---- Game conflict (409) ----

    fun dismissConflict() {
        _state.update { it.copy(gameConflict = null) }
    }

    fun forfeitAndRetry() {
        val conflict = _state.value.gameConflict ?: return
        _state.update { it.copy(gameConflict = conflict.copy(loading = true)) }
        viewModelScope.launch {
            val forfeitResult = when (conflict.game) {
                "crash" -> CasinoApi.forfeitCrash(apiKey)
                "blackjack" -> CasinoApi.forfeitBlackjack(apiKey)
                "mines" -> CasinoApi.forfeitMines(apiKey)
                else -> Result.failure(Exception("Unknown game"))
            }
            forfeitResult
                .onSuccess {
                    _state.update { it.copy(gameConflict = null) }
                    fetchCasinoBalance()
                    when (conflict.game) {
                        "crash" -> startCrash(conflict.pendingAmount)
                        "blackjack" -> startBlackjack(conflict.pendingAmount)
                        "mines" -> startMines(conflict.pendingAmount, conflict.pendingExtra)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(gameConflict = null) }
                    when (conflict.game) {
                        "crash" -> _state.update { it.copy(crash = CrashUiState(error = "Forfeit failed: ${e.message}")) }
                        "blackjack" -> _state.update { it.copy(blackjack = BlackjackUiState(error = "Forfeit failed: ${e.message}")) }
                        "mines" -> _state.update { it.copy(mines = MinesUiState(error = "Forfeit failed: ${e.message}")) }
                    }
                }
        }
    }

    // ---- Blackjack ----

    fun startBlackjack(amount: Long) {
        _state.update { it.copy(blackjack = BlackjackUiState(loading = true, initialBet = amount)) }
        viewModelScope.launch {
            CasinoApi.startBlackjack(apiKey, amount)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            casinoBalance = resp.newBalance,
                            blackjack = BlackjackUiState(active = true, response = resp, initialBet = amount),
                        )
                    }
                    if (resp.status == "done") fetchTransactions()
                }
                .onFailure { e ->
                    if (e is CasinoApiException && e.code == 409) {
                        _state.update {
                            it.copy(
                                blackjack = BlackjackUiState(),
                                gameConflict = GameConflict(game = "blackjack", pendingAmount = amount),
                            )
                        }
                    } else {
                        _state.update { it.copy(blackjack = BlackjackUiState(error = e.message)) }
                    }
                }
        }
    }

    fun blackjackHit() {
        val current = _state.value.blackjack
        if (!current.active || current.loading) return
        _state.update { it.copy(blackjack = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.blackjackHit(apiKey)
                .onSuccess { resp ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, response = resp)) }
                    if (resp.status == "done") {
                        if (resp.newBalance > 0) _state.update { it.copy(casinoBalance = resp.newBalance) }
                        fetchTransactions()
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun blackjackStand() {
        val current = _state.value.blackjack
        if (!current.active || current.loading) return
        _state.update { it.copy(blackjack = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.blackjackStand(apiKey)
                .onSuccess { resp ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, response = resp)) }
                    if (resp.newBalance > 0) _state.update { it.copy(casinoBalance = resp.newBalance) }
                    fetchTransactions()
                }
                .onFailure { e ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun blackjackDouble() {
        val current = _state.value.blackjack
        if (!current.active || current.loading) return
        _state.update { it.copy(blackjack = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.blackjackDouble(apiKey)
                .onSuccess { resp ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, response = resp)) }
                    if (resp.newBalance > 0) _state.update { it.copy(casinoBalance = resp.newBalance) }
                    fetchTransactions()
                }
                .onFailure { e ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun blackjackSplit() {
        val current = _state.value.blackjack
        if (!current.active || current.loading) return
        val previousBet = current.response?.bet ?: 0L
        _state.update { it.copy(blackjack = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.blackjackSplit(apiKey)
                .onSuccess { resp ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, response = resp)) }
                    // Server debits a second bet on split. Reflect it locally if the
                    // playing response doesn't include newBalance.
                    if (resp.newBalance > 0) {
                        _state.update { it.copy(casinoBalance = resp.newBalance) }
                    } else if (previousBet > 0) {
                        _state.update { s ->
                            val bal = s.casinoBalance ?: return@update s
                            s.copy(casinoBalance = bal - previousBet)
                        }
                    }
                    if (resp.status == "done") fetchTransactions()
                }
                .onFailure { e ->
                    _state.update { it.copy(blackjack = it.blackjack.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun resetBlackjack() {
        _state.update { it.copy(blackjack = BlackjackUiState()) }
    }

    // ---- Coinflip ----

    fun playCoinflip(amount: Long, choice: String) {
        _state.update { it.copy(coinflipLoading = true, coinflipError = null, coinflipResult = null) }
        viewModelScope.launch {
            CasinoApi.playCoinflip(apiKey, amount, choice)
                .onSuccess { resp ->
                    _state.update { it.copy(coinflipResult = resp, coinflipLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(coinflipLoading = false, coinflipError = e.message) }
                }
        }
    }

    fun applyCoinflipResult() {
        val result = _state.value.coinflipResult ?: return
        _state.update { it.copy(casinoBalance = result.newBalance) }
        fetchTransactions()
    }

    fun resetCoinflip() {
        _state.update { it.copy(coinflipResult = null, coinflipError = null, coinflipLoading = false) }
    }

    // ---- Slots ----

    fun playSlots(amount: Long, machines: Int = 1) {
        _state.update { it.copy(slotsLoading = true, slotsError = null, slotsResult = null) }
        viewModelScope.launch {
            CasinoApi.playSlots(apiKey, amount, machines)
                .onSuccess { resp ->
                    _state.update { it.copy(slotsResult = resp, slotsLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(slotsLoading = false, slotsError = e.message) }
                }
        }
    }

    fun applySlotsResult() {
        val result = _state.value.slotsResult ?: return
        _state.update { it.copy(casinoBalance = result.newBalance) }
        fetchTransactions()
    }

    fun resetSlots() {
        _state.update { it.copy(slotsResult = null, slotsError = null, slotsLoading = false) }
    }

    // ---- Mines ----

    fun startMines(amount: Long, mineCount: Int) {
        _state.update { it.copy(mines = MinesUiState(loading = true)) }
        viewModelScope.launch {
            CasinoApi.startMines(apiKey, amount, mineCount)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            casinoBalance = resp.newBalance,
                            mines = MinesUiState(
                                active = true,
                                bet = resp.bet,
                                mineCount = resp.mineCount,
                                nextMultiplier = resp.nextMultiplier,
                                safeRemaining = resp.gridSize - resp.mineCount,
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CasinoApiException && e.code == 409) {
                        _state.update {
                            it.copy(
                                mines = MinesUiState(),
                                gameConflict = GameConflict(game = "mines", pendingAmount = amount, pendingExtra = mineCount),
                            )
                        }
                    } else {
                        _state.update { it.copy(mines = MinesUiState(error = e.message)) }
                    }
                }
        }
    }

    fun revealMines(position: Int) {
        val current = _state.value.mines
        if (!current.active || current.gameOver || current.loading) return
        _state.update { it.copy(mines = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.revealMines(apiKey, position)
                .onSuccess { resp ->
                    if (resp.result == "mine") {
                        _state.update {
                            it.copy(mines = it.mines.copy(
                                loading = false,
                                revealed = resp.revealed.toSet(),
                                mines = resp.mines,
                                gameOver = true,
                                won = false,
                                payout = 0,
                            ))
                        }
                        fetchTransactions()
                    } else if (resp.allRevealed) {
                        _state.update {
                            it.copy(
                                casinoBalance = resp.newBalance,
                                mines = it.mines.copy(
                                    loading = false,
                                    revealed = resp.revealed.toSet(),
                                    mines = resp.mines,
                                    gameOver = true,
                                    won = true,
                                    payout = resp.payout,
                                    currentMultiplier = resp.multiplier,
                                ),
                            )
                        }
                        fetchTransactions()
                    } else {
                        _state.update {
                            it.copy(mines = it.mines.copy(
                                loading = false,
                                revealed = resp.revealed.toSet(),
                                currentMultiplier = resp.currentMultiplier,
                                currentPayout = resp.currentPayout,
                                nextMultiplier = resp.nextMultiplier,
                                safeRemaining = resp.safeRemaining,
                            ))
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(mines = it.mines.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun cashoutMines() {
        val current = _state.value.mines
        if (!current.active || current.gameOver || current.revealed.isEmpty()) return
        _state.update { it.copy(mines = current.copy(loading = true)) }
        viewModelScope.launch {
            CasinoApi.cashoutMines(apiKey)
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            casinoBalance = resp.newBalance,
                            mines = it.mines.copy(
                                loading = false,
                                mines = resp.mines,
                                revealed = resp.revealed.toSet(),
                                gameOver = true,
                                won = true,
                                payout = resp.payout,
                                currentMultiplier = resp.multiplier,
                            ),
                        )
                    }
                    fetchTransactions()
                }
                .onFailure { e ->
                    _state.update { it.copy(mines = it.mines.copy(loading = false, error = e.message)) }
                }
        }
    }

    fun resetMines() {
        _state.update { it.copy(mines = MinesUiState()) }
    }

    // ---- Egg Hatch ----

    fun fetchEggs() {
        if (_state.value.eggs.isNotEmpty() || _state.value.eggsLoading) return
        _state.update { it.copy(eggsLoading = true) }
        viewModelScope.launch {
            CasinoApi.getEggs()
                .onSuccess { resp ->
                    _state.update { it.copy(eggs = resp.eggs, eggConfig = resp.config, eggsLoading = false) }
                }
                .onFailure { e ->
                    AppLog.e(TAG, "[Eggs] Fetch failed: ${e.message}")
                    _state.update { it.copy(eggsLoading = false) }
                }
        }
    }

    fun playEggHatch(eggType: String, count: Int) {
        _state.update { it.copy(eggHatchLoading = true, eggHatchError = null, eggHatchResult = null) }
        viewModelScope.launch {
            CasinoApi.playEggHatch(apiKey, eggType, count)
                .onSuccess { resp ->
                    _state.update { it.copy(eggHatchResult = resp, eggHatchLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(eggHatchLoading = false, eggHatchError = e.message) }
                }
        }
    }

    fun applyEggHatchResult() {
        val result = _state.value.eggHatchResult ?: return
        _state.update { it.copy(casinoBalance = result.newBalance) }
        fetchTransactions()
    }

    fun resetEggHatch() {
        _state.update { it.copy(eggHatchResult = null, eggHatchError = null, eggHatchLoading = false) }
    }
}
