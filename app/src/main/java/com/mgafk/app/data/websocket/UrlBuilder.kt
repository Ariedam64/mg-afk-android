package com.mgafk.app.data.websocket

import android.net.Uri
import com.mgafk.app.data.model.BotAvatar
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How the client reached this connection, mirroring the browser's navigation type. */
enum class NavigationType(val value: String) {
    /** First connection of a session. */
    NAVIGATE("navigate"),

    /** Re-opening the same session after a drop or a game version change. */
    RELOAD("reload"),
}

/**
 * Per-connection identity the server uses to tell one client "document" from
 * another. [documentId] stays stable for the whole session (it identifies the
 * tab in the web client) while [connectionAttempt] counts the attempts made
 * with that document: 1 for the first one, then 2, 3, ... for each retry.
 */
data class ClientContext(
    val documentId: String,
    val connectionAttempt: Int,
    val navigationType: NavigationType,
    /**
     * Asks the server to hand this session back to us. Only sent (as `true`)
     * after the previous connection was closed with a superseded code, which
     * is the case it exists for.
     */
    val reclaimSupersededSession: Boolean,
)

object UrlBuilder {
    /**
     * Query values are JSON-encoded (quotes included), exactly like the web client.
     * The parameter set below mirrors a real browser connect URL: no `playerId`
     * (the server assigns it and reports it back as `selfPlayerId` in Welcome)
     * and no `source`, which only the manual-URL flow of the web app sends.
     */
    private const val SURFACE = "\"web\""
    private const val PLATFORM = "\"desktop\""
    private const val CAPABILITIES = "\"fbo_mipmap_unsupported\""
    private const val LOCALE = "\"en\""

    /** The app never backgrounds its socket the way a hidden tab does. */
    private const val VISIBILITY_STATE = "\"visible\""

    /**
     * The client-context query params, in the order the web client sends them.
     * Booleans and numbers go raw, strings are quoted like every other value.
     */
    internal fun clientParams(context: ClientContext): List<Pair<String, String>> = buildList {
        if (context.reclaimSupersededSession) add("reclaimSupersededSession" to "true")
        add("clientDocumentId" to "\"${context.documentId}\"")
        add("clientConnectionAttempt" to context.connectionAttempt.toString())
        add("clientNavigationType" to "\"${context.navigationType.value}\"")
        add("clientVisibilityState" to VISIBILITY_STATE)
    }

    fun buildUrl(host: String, version: String, room: String, client: ClientContext): String {
        val base = "wss://$host/version/$version/api/rooms/$room/connect"
        val builder = Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", SURFACE)
            .appendQueryParameter("platform", PLATFORM)
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("capabilities", CAPABILITIES)
            .appendQueryParameter("locale", LOCALE)
        clientParams(client).forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build().toString()
    }

    /**
     * Build the WS URL for an unauthenticated guest connection. The server
     * identifies the guest via [anonymousUserStyle] (color, avatar pieces,
     * display name) instead of a Cookie header.
     */
    fun buildGuestUrl(
        host: String,
        version: String,
        room: String,
        name: String,
        avatar: BotAvatar,
        client: ClientContext,
    ): String {
        val base = "wss://$host/version/$version/api/rooms/$room/connect"
        val styleJson = buildJsonObject {
            put("color", JsonPrimitive(avatar.color))
            put("avatarBottom", JsonPrimitive(avatar.avatarBottom))
            put("avatarMid", JsonPrimitive(avatar.avatarMid))
            put("avatarTop", JsonPrimitive(avatar.avatarTop))
            put("avatarExpression", JsonPrimitive(avatar.avatarExpression))
            put("name", JsonPrimitive(name))
        }.toString()
        val builder = Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", SURFACE)
            .appendQueryParameter("platform", PLATFORM)
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("anonymousUserStyle", styleJson)
            .appendQueryParameter("capabilities", CAPABILITIES)
            .appendQueryParameter("locale", LOCALE)
        clientParams(client).forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build().toString()
    }
}
