package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shop and alert screens list the four core shops in this order, before event shops. */
class CoreShopsTest {

    @Test fun `core shops are listed seeds, tools, eggs, decors`() {
        assertEquals(listOf("Seeds", "Tools", "Eggs", "Decors"), CoreShops.LABELED.map { it.first })
        assertEquals(listOf("seed", "tool", "egg", "decor"), CoreShops.LABELED.map { it.second })
    }

    @Test fun `keys are exactly the core shop types`() {
        assertEquals(setOf("seed", "tool", "egg", "decor"), CoreShops.KEYS)
    }
}
