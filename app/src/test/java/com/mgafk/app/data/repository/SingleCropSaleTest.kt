package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryProduceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleCropSaleTest {

    private val carrotA = InventoryProduceItem(id = "c1", species = "Carrot")
    private val carrotB = InventoryProduceItem(id = "c2", species = "Carrot")
    private val tomato = InventoryProduceItem(id = "t1", species = "Tomato")
    private val pepper = InventoryProduceItem(id = "p1", species = "Pepper")

    @Test
    fun `locks every other unlocked crop and leaves already locked ones alone`() {
        val plan = SingleCropSale.plan(listOf(carrotA, tomato, pepper), locked = setOf("p1"), targetId = "c1")!!
        assertEquals(emptyList<String>(), plan.unlock)
        assertEquals(listOf("t1"), plan.tempLock)
        assertEquals(emptyList<String>(), plan.relock)
    }

    @Test
    fun `lifting the target species lock still keeps its siblings out of the sale`() {
        val plan = SingleCropSale.plan(listOf(carrotA, carrotB, tomato), locked = setOf("Carrot"), targetId = "c1")!!
        assertEquals(listOf("Carrot"), plan.unlock)
        assertEquals(listOf("c2", "t1"), plan.tempLock)
        assertEquals(listOf("Carrot"), plan.relock)
    }

    @Test
    fun `crops locked through another species stay covered by that lock`() {
        val plan = SingleCropSale.plan(listOf(carrotA, tomato), locked = setOf("Tomato"), targetId = "c1")!!
        assertEquals(emptyList<String>(), plan.tempLock)
    }

    @Test
    fun `a target locked by its own id is unlocked and not locked again once sold`() {
        val plan = SingleCropSale.plan(listOf(carrotA, tomato), locked = setOf("c1"), targetId = "c1")!!
        assertEquals(listOf("c1"), plan.unlock)
        assertEquals(listOf("t1"), plan.tempLock)
        assertEquals(emptyList<String>(), plan.relock)
    }

    @Test
    fun `a sibling locked by its own id needs no temporary lock`() {
        val plan = SingleCropSale.plan(listOf(carrotA, carrotB), locked = setOf("Carrot", "c2"), targetId = "c1")!!
        assertEquals(emptyList<String>(), plan.tempLock)
    }

    @Test
    fun `no plan when the crop is no longer in the inventory`() {
        assertNull(SingleCropSale.plan(listOf(tomato), locked = emptySet(), targetId = "c1"))
    }
}
