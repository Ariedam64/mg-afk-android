package com.mgafk.app.data.repository

import com.mgafk.app.data.model.PetTeam

/** Rules about pet teams that belong to the game rather than to the app. */
object PetTeams {

    /**
     * Whether [team] is the one currently out, given the pet ids standing in the slots.
     *
     * The game's rule, reproduced exactly: the members must be a duplicate-free set of the same
     * size as the slots, and every one of them must be in a slot. Anything else, including a
     * team that merely contains all the active pets, is not the active team.
     */
    fun isActive(team: PetTeam, activePetIds: List<String>): Boolean {
        val memberIds = team.members.map { it.petId }.toSet()
        if (memberIds.isEmpty()) return false
        if (memberIds.size != team.members.size) return false
        val activeIds = activePetIds.toSet()
        if (memberIds.size != activeIds.size) return false
        return memberIds.all { it in activeIds }
    }
}
