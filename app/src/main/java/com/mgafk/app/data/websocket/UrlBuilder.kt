package com.mgafk.app.data.websocket

import android.net.Uri
import com.mgafk.app.data.model.BotAvatar
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object UrlBuilder {
    fun buildUrl(host: String, version: String, room: String, playerId: String): String {
        val base = "wss://$host/version/$version/api/rooms/$room/connect"
        return Uri.parse(base).buildUpon()
            .appendQueryParameter("surface", "\"web\"")
            .appendQueryParameter("platform", "\"desktop\"")
            .appendQueryParameter("playerId", "\"$playerId\"")
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("source", "\"manualUrl\"")
            .appendQueryParameter("capabilities", "\"fbo_mipmap_ok\"")
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
        playerId: String,
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
            .appendQueryParameter("surface", "\"web\"")
            .appendQueryParameter("platform", "\"desktop\"")
            .appendQueryParameter("playerId", "\"$playerId\"")
            .appendQueryParameter("version", "\"$version\"")
            .appendQueryParameter("anonymousUserStyle", styleJson)
            .appendQueryParameter("source", "\"manualUrl\"")
            .appendQueryParameter("capabilities", "\"fbo_mipmap_unsupported\"")
            .build()
            .toString()
    }
}
