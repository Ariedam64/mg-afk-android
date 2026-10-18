package com.mgafk.app.data.websocket

/**
 * The answer a server heartbeat frame asks for, or null when [frame] is not one.
 *
 * Since game build 1625 the server pings every 4 seconds with `ping:<seq>`, or
 * `ping:<seq>:<rttMs>` once it has measured a round trip, and the game answers `pong:<seq>`
 * with the same number. The round trip time is only for display, so it is not sent back.
 * Frames are matched the way the game matches them, so anything else is left to the JSON
 * path. Answering nothing gets the socket closed with 4400 "heartbeat expired" after about
 * 16 seconds, and the old bare `ping` handling did exactly that, since a numbered ping is not
 * a bare one. A bare `ping` still gets a bare `pong`, in case a server sends one again.
 */
internal fun heartbeatReply(frame: String): String? {
    if (frame == "ping" || frame == "\"ping\"") return "pong"
    val seq = NUMBERED_PING.matchEntire(frame)?.groupValues?.get(1) ?: return null
    return "pong:$seq"
}

/** The game's own pattern for a server ping, from its RoomConnection chunk. */
private val NUMBERED_PING = Regex("""ping:(\d{1,9})(?::(\d{1,9}))?""")
