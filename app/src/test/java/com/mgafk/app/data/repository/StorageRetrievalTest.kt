package com.mgafk.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BR-52: retrieve from the Tool Shack, wait for the inventory to show it, then use it. */
class StorageRetrievalTest {

    @Test fun `use runs once when the item arrives`() = runBlocking {
        val arrived = MutableStateFlow(false)
        var retrieved = 0
        var used = 0

        val result = StorageRetrieval.retrieveThenUse(
            retrieve = { retrieved++; arrived.value = true },
            arrived = arrived,
            timeoutMs = 1_000L,
            use = { used++ },
        )

        assertTrue(result)
        assertEquals(1, retrieved)
        assertEquals(1, used)
    }

    @Test fun `nothing is used when the item never arrives in time`() = runBlocking {
        val arrived = MutableStateFlow(false)
        var retrieved = 0
        var used = 0

        val result = StorageRetrieval.retrieveThenUse(
            retrieve = { retrieved++ },
            arrived = arrived,
            timeoutMs = 50L,
            use = { used++ },
        )

        assertFalse(result)
        assertEquals(1, retrieved)
        assertEquals(0, used)
    }

    @Test fun `a failing retrieval returns false without using`() = runBlocking {
        val arrived = MutableStateFlow(true)
        var used = 0
        var error: Throwable? = null

        val result = StorageRetrieval.retrieveThenUse(
            retrieve = { throw IllegalStateException("socket closed") },
            arrived = arrived,
            timeoutMs = 50L,
            use = { used++ },
            onError = { error = it },
        )

        assertFalse(result)
        assertEquals(0, used)
        assertTrue(error is IllegalStateException)
    }
}
