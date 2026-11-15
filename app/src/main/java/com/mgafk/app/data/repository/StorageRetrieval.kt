package com.mgafk.app.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes an item out of a storage, waits for the server to show it in the inventory, then uses it.
 * Pure coroutines, no Android, so the sequence is unit tested.
 */
object StorageRetrieval {

    /**
     * Calls [retrieve], waits up to [timeoutMs] for [arrived] to emit true, then calls [use] once
     * and returns true. Returns false without calling [use] when the item never arrives in time
     * or something throws; [onError] then hears about the exception. Cancellation propagates.
     */
    suspend fun retrieveThenUse(
        retrieve: () -> Unit,
        arrived: Flow<Boolean>,
        timeoutMs: Long,
        use: () -> Unit,
        onError: (Throwable) -> Unit = {},
    ): Boolean {
        return try {
            retrieve()
            val ok = withTimeoutOrNull(timeoutMs) { arrived.first { it } }
            if (ok == null) {
                false
            } else {
                use()
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
            false
        }
    }
}
