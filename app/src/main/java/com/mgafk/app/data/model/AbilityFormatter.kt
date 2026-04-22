package com.mgafk.app.data.model

/**
 * Format pet ability activity logs into human-readable descriptions.
 *
 * Action ids and parameter shapes mirror the game's activityLogs schema in
 * QuinoaView (`action:jn([z(...)]),parameters:B({...})`). When pets aren't
 * given a custom name the `name` field is empty, so descriptions fall back
 * to the pet's species id (also surfaced in our params map by RoomClient).
 */
object AbilityFormatter {

    fun format(log: AbilityLog): String? {
        val p = log.params
        return when (log.action) {

            // ── Coin Finder ────────────────────────────────────────────────
            "CoinFinderI", "CoinFinderII", "CoinFinderIII", "SnowyCoinFinder" -> {
                val coins = p["coinsFound"] ?: return null
                "Found $coins coins"
            }

            // ── Seed Finder ────────────────────────────────────────────────
            "SeedFinderI", "SeedFinderII", "SeedFinderIII", "SeedFinderIV" -> {
                val species = p["speciesId"] ?: "Unknown"
                "Found 1× $species seed"
            }

            // ── Hunger Restore ─────────────────────────────────────────────
            "HungerRestore", "HungerRestoreII", "HungerRestoreIII", "SnowyHungerRestore" -> {
                val amount = p["hungerRestoreAmount"] ?: return null
                val isSelf = p["targetPetId"] != null && p["targetPetId"] == p["petId"]
                val target = if (isSelf) "itself" else targetName(p)
                "Restored $amount hunger to $target"
            }

            // ── Double Harvest / Hatch ─────────────────────────────────────
            "DoubleHarvest" -> {
                val crop = p["harvestedCropSpecies"] ?: "Unknown"
                "Double harvested $crop"
            }
            "DoubleHatch" -> {
                val species = p["extraPetSpecies"] ?: "Unknown"
                "Double hatched $species"
            }

            // ── Produce Eater ──────────────────────────────────────────────
            "ProduceEater" -> {
                val crop = p["growSlotSpecies"] ?: "Unknown"
                val price = p["sellPrice"] ?: "0"
                "Ate $crop for $price coins"
            }

            // ── Pet Hatch Size Boost ───────────────────────────────────────
            "PetHatchSizeBoost", "PetHatchSizeBoostII", "PetHatchSizeBoostIII" -> {
                val target = targetName(p)
                val raw = p["strengthIncrease"]
                val increase = raw?.toDoubleOrNull()?.toInt()?.toString() ?: (raw ?: "?")
                "Boosted $target's size by +$increase"
            }

            // ── Pet Age Boost ──────────────────────────────────────────────
            "PetAgeBoost", "PetAgeBoostII", "PetAgeBoostIII" -> {
                val target = targetName(p)
                val xp = p["bonusXp"] ?: "0"
                "Gave +$xp XP to $target"
            }

            // ── Pet Refund ─────────────────────────────────────────────────
            "PetRefund", "PetRefundII" -> {
                val eggId = p["eggId"] ?: "Unknown Egg"
                "Refunded 1× $eggId"
            }

            // ── Produce Refund ─────────────────────────────────────────────
            "ProduceRefund" -> {
                val count = p["cropsRefundedCount"] ?: "0"
                val label = if (count == "1") "crop" else "crops"
                "Refunded $count $label"
            }

            // ── Sell Boost ─────────────────────────────────────────────────
            "SellBoostI", "SellBoostII", "SellBoostIII", "SellBoostIV" -> {
                val bonus = p["bonusCoins"] ?: "0"
                "Gave +$bonus bonus coins"
            }

            // ── Mutation granters ──────────────────────────────────────────
            "GoldGranter", "RainbowGranter", "RainDance",
            "SnowGranter", "FrostGranter",
            "DawnlitGranter", "AmberlitGranter" -> {
                val mutation = p["mutation"] ?: "Unknown"
                val crop = p["growSlotSpecies"] ?: "Unknown"
                "Made $crop turn $mutation"
            }

            // ── Pet XP Boost ───────────────────────────────────────────────
            "PetXpBoost", "PetXpBoostII", "PetXpBoostIII", "SnowyPetXpBoost" -> {
                val xp = p["bonusXp"] ?: "0"
                val count = p["petsAffectedCount"] ?: "0"
                val label = if (count == "1") "pet" else "pets"
                "Gave +$xp XP to $count $label"
            }

            // ── Egg Growth Boost ───────────────────────────────────────────
            "EggGrowthBoost", "EggGrowthBoostII", "EggGrowthBoostII_NEW", "SnowyEggGrowthBoost" -> {
                val seconds = p["secondsReduced"]?.toDoubleOrNull()?.toInt() ?: 0
                val count = p["eggsAffectedCount"] ?: "0"
                val label = if (count == "1") "egg" else "eggs"
                "Reduced $count $label growth by ${formatTime(seconds)}"
            }

            // ── Plant Growth Boost ─────────────────────────────────────────
            "PlantGrowthBoost", "PlantGrowthBoostII", "PlantGrowthBoostIII",
            "SnowyPlantGrowthBoost", "DawnPlantGrowthBoost", "AmberPlantGrowthBoost" -> {
                val seconds = p["secondsReduced"]?.toDoubleOrNull()?.toInt() ?: 0
                val count = p["numPlantsAffected"] ?: "0"
                val label = if (count == "1") "plant" else "plants"
                "Reduced $count $label growth by ${formatTime(seconds)}"
            }

            // ── Produce Scale Boost ────────────────────────────────────────
            "ProduceScaleBoost", "ProduceScaleBoostII", "ProduceScaleBoostIII", "SnowyCropSizeBoost" -> {
                val raw = p["scaleIncreasePercentage"]
                val pct = raw?.toDoubleOrNull()?.toInt()?.toString() ?: (raw ?: "?")
                val count = p["numPlantsAffected"] ?: "0"
                val label = if (count == "1") "crop" else "crops"
                "Boosted $count $label size by +$pct%"
            }

            // ── Pet Mutation Boost (kept for back-compat with older states) ─
            "PetMutationBoost", "PetMutationBoostII" -> {
                val target = targetName(p)
                val mutation = p["mutation"] ?: "Unknown"
                "Gave $mutation mutation to $target"
            }

            else -> null
        }
    }

    /** Pet name with species fallback when the target has no custom name. */
    private fun targetName(p: Map<String, String>): String =
        p["targetPetName"]?.takeIf { it.isNotBlank() }
            ?: p["targetPetSpecies"]?.takeIf { it.isNotBlank() }
            ?: "Unknown"

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
