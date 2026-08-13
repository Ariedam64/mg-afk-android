package com.mgafk.app.data.websocket

import android.net.Uri
import com.mgafk.app.data.model.BotAvatar
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

    fun buildUrl(host: String, version: String, room: String): String {
        val base = "wss://$host/version/$version/api/rooms/$room/connect"
        return Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", SURFACE)
            .appendQueryParameter("platform", PLATFORM)
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("capabilities", CAPABILITIES)
            .appendQueryParameter("locale", LOCALE)
            .build()
            .toString()
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
        return Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", SURFACE)
            .appendQueryParameter("platform", PLATFORM)
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("anonymousUserStyle", styleJson)
            .appendQueryParameter("capabilities", CAPABILITIES)
            .appendQueryParameter("locale", LOCALE)
            .build()
            .toString()
    }
}
