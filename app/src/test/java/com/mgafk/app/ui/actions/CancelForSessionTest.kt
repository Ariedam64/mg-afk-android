package com.mgafk.app.ui.actions

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelForSessionTest {

    @Test
    fun `only the named session's rollbacks are cancelled`() {
        val mine = Job()
        val other = Job()
        val prefixTwin = Job()
        val jobs = mutableMapOf<String, Job>("a:trough:add:c1" to mine, "b:feed:p1:c2" to other, "ab:feed:p1:c3" to prefixTwin)

        jobs.cancelForSession("a")

        assertTrue(mine.isCancelled)
        assertFalse(other.isCancelled)
        assertFalse(prefixTwin.isCancelled)
        assertEquals(setOf("b:feed:p1:c2", "ab:feed:p1:c3"), jobs.keys)
    }
}
