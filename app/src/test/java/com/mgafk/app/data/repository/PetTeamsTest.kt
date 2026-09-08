package com.mgafk.app.data.repository

import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.model.PetTeamMember
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A team counts as active when its members are exactly the pets standing in the slots. This is
 * the game's own rule, reproduced so the app highlights the same team the game does.
 */
class PetTeamsTest {

    private fun team(vararg petIds: String) = PetTeam(
        id = "t",
        name = "T",
        members = petIds.map { PetTeamMember(petId = it, petSpecies = "Bee") },
    )

    @Test fun `the team whose members are exactly the active pets is active`() {
        assertTrue(PetTeams.isActive(team("a", "b", "c"), listOf("a", "b", "c")))
    }

    @Test fun `order does not matter`() {
        assertTrue(PetTeams.isActive(team("a", "b", "c"), listOf("c", "a", "b")))
    }

    @Test fun `a team missing one of the active pets is not active`() {
        assertFalse(PetTeams.isActive(team("a", "b"), listOf("a", "b", "c")))
    }

    @Test fun `a team with an extra pet is not active`() {
        assertFalse(PetTeams.isActive(team("a", "b", "c"), listOf("a", "b")))
    }

    @Test fun `same size but one different pet is not active`() {
        assertFalse(PetTeams.isActive(team("a", "b", "x"), listOf("a", "b", "c")))
    }

    /** A duplicated member cannot match: it would count once against a slot set of two. */
    @Test fun `a team listing the same pet twice is not active`() {
        assertFalse(PetTeams.isActive(team("a", "a"), listOf("a", "b")))
        assertFalse(PetTeams.isActive(team("a", "a"), listOf("a")))
    }

    @Test fun `no team is active when no pet is out`() {
        assertFalse(PetTeams.isActive(team("a"), emptyList()))
    }

    @Test fun `an empty team is never active`() {
        assertFalse(PetTeams.isActive(team(), emptyList()))
        assertFalse(PetTeams.isActive(team(), listOf("a")))
    }
}
