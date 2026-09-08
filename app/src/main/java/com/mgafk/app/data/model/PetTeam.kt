package com.mgafk.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The badge shown on a team, as the game models it: a discriminated union on `type`.
 *
 * The server assigns a free [Letter] when a team is created, and the game is where a player
 * changes it, so the app only ever reads these.
 */
@Serializable
sealed interface PetTeamEmblem {

    /** A letter A..Z, carried over the wire as its 1-based index. */
    @Serializable
    data class Letter(val number: Int) : PetTeamEmblem {
        val label: String get() = ('A' + (number - 1).coerceIn(0, LETTER_COUNT - 1)).toString()
    }

    /** The sprite of one species, which must be a member of the team. */
    @Serializable
    data class Pet(val petSpecies: String) : PetTeamEmblem

    /** One of the game's fixed icons (rainbow, gold, thunder, ...). */
    @Serializable
    data class Icon(val icon: String) : PetTeamEmblem

    /** A cosmetic the player owns. */
    @Serializable
    data class Cosmetic(val cosmetic: String) : PetTeamEmblem

    /** A type this build does not know. Kept so one new emblem cannot break the whole list. */
    @Serializable
    data object Unknown : PetTeamEmblem

    /**
     * The wire shape the game expects in SetPetTeamEmblem, or null for [Unknown], which this
     * build cannot describe and must not guess at.
     */
    fun toJson(): JsonObject? = when (this) {
        is Letter -> buildJsonObject {
            put("type", JsonPrimitive("number"))
            put("number", JsonPrimitive(number))
        }
        is Pet -> buildJsonObject {
            put("type", JsonPrimitive("pet"))
            put("petSpecies", JsonPrimitive(petSpecies))
        }
        is Icon -> buildJsonObject {
            put("type", JsonPrimitive("icon"))
            put("icon", JsonPrimitive(icon))
        }
        is Cosmetic -> buildJsonObject {
            put("type", JsonPrimitive("cosmetic"))
            put("cosmetic", JsonPrimitive(cosmetic))
        }
        Unknown -> null
    }

    companion object {
        const val LETTER_COUNT = 26

        fun fromJson(obj: JsonObject?): PetTeamEmblem {
            val value = obj ?: return Unknown
            return when (value["type"]?.jsonPrimitive?.contentOrNull) {
                "number" -> value["number"]?.jsonPrimitive?.intOrNull?.let(::Letter) ?: Unknown
                "pet" -> value["petSpecies"]?.jsonPrimitive?.contentOrNull?.let(::Pet) ?: Unknown
                "icon" -> value["icon"]?.jsonPrimitive?.contentOrNull?.let(::Icon) ?: Unknown
                "cosmetic" -> value["cosmetic"]?.jsonPrimitive?.contentOrNull?.let(::Cosmetic) ?: Unknown
                else -> Unknown
            }
        }
    }
}

/** One pet in a team. [name] is null until the player renames that pet. */
@Serializable
data class PetTeamMember(
    val petId: String,
    val petSpecies: String = "",
    val name: String? = null,
)

/**
 * A pet team as the game stores it, in `userSlots[i].data.petTeams`.
 *
 * Teams are server state: the app reads them from the room and changes them through the game's
 * own commands, so nothing here is persisted locally.
 */
@Serializable
data class PetTeam(
    val id: String,
    val name: String = "",
    val members: List<PetTeamMember> = emptyList(),
    val emblem: PetTeamEmblem = PetTeamEmblem.Unknown,
) {
    val petIds: List<String> get() = members.map { it.petId }

    companion object {
        /** The game's schema allows 1..3 members, and rejects an empty team. */
        const val MAX_PETS = 3
        const val MIN_PETS = 1

        /** Creating past this is rejected by the server. */
        const val MAX_TEAMS = 25

        /** The server truncates a longer name to this many grapheme clusters. */
        const val MAX_NAME_CLUSTERS = 16

        /** Returns null for a payload that cannot be a team: no id, or no member. */
        fun fromJson(obj: JsonObject): PetTeam? {
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val members = (obj["members"] as? JsonArray ?: return null)
                .mapNotNull { element ->
                    val member = element as? JsonObject ?: return@mapNotNull null
                    val petId = member["petId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    PetTeamMember(
                        petId = petId,
                        petSpecies = member["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = member["name"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            if (members.isEmpty()) return null
            return PetTeam(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                members = members,
                emblem = PetTeamEmblem.fromJson(obj["emblem"] as? JsonObject),
            )
        }

        /** Every team in a `petTeams` array, skipping any entry the game sends in a shape we reject. */
        fun listFromJson(array: JsonArray): List<PetTeam> =
            array.mapNotNull { fromJson(it as? JsonObject ?: return@mapNotNull null) }
    }
}
