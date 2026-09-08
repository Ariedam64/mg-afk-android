package com.mgafk.app.service

/**
 * Decides which pets still deserve a hunger alert, so a pet is announced when it gets hungry
 * and when it runs out, not on every update.
 *
 * The game pushes a pet update every few seconds and hunger only ever drifts down, so a level
 * check ("below the threshold right now") fires continuously. This turns the level into edges:
 * one alert crossing below the threshold, one reaching zero, then silence until the pet is fed
 * back above the threshold and a new cycle starts.
 *
 * State is per session: one notifier serves every session, and each session's update carries
 * only its own pets, so a sibling's update must not reset it.
 */
internal class PetHungerAlertTracker {

    /** How far down a pet has gone in its current hunger cycle. Ordered: LOW comes before EMPTY. */
    enum class Stage { LOW, EMPTY }

    data class PetHungerReading(val petId: String, val percent: Float)

    data class PetHungerAlert(val petId: String, val stage: Stage)

    /** sessionId -> petId -> the stage already alerted for that pet. */
    private val alertedStages = mutableMapOf<String, MutableMap<String, Stage>>()

    /**
     * Records and returns the alerts to raise now, in [readings] order.
     *
     * Pets absent from [readings] keep their state: a pet can leave the list without being fed
     * (stored in the hutch, sold), and forgetting it would re-alert when it comes back hungry.
     *
     * @param readings each pet of that session with its current hunger, as a percentage of full.
     * @param threshold the percentage below which a pet counts as hungry.
     */
    fun newlyHungry(
        sessionId: String,
        readings: List<PetHungerReading>,
        threshold: Float,
    ): List<PetHungerAlert> {
        val stages = alertedStages.getOrPut(sessionId) { mutableMapOf() }
        val alerts = mutableListOf<PetHungerAlert>()

        for (reading in readings) {
            val stage = stageOf(reading.percent, threshold)
            if (stage == null) {
                // Fed back above the threshold: the cycle is over, arm it for the next one.
                stages.remove(reading.petId)
                continue
            }
            // A pet that drops straight to zero gets the one alert that matters, not both.
            val alreadyAlerted = stages[reading.petId]
            if (alreadyAlerted != null && alreadyAlerted >= stage) continue
            stages[reading.petId] = stage
            alerts += PetHungerAlert(reading.petId, stage)
        }
        return alerts
    }

    private fun stageOf(percent: Float, threshold: Float): Stage? = when {
        percent <= EMPTY_PERCENT -> Stage.EMPTY
        percent < threshold -> Stage.LOW
        else -> null
    }

    private companion object {
        /** At or below this the pet is out of food and stops working. */
        const val EMPTY_PERCENT = 0f
    }
}
