package com.mgafk.app.data.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server keeps the socket alive with a text-frame heartbeat and closes it with 4400 when
 * nothing answers. Since game build 1625 it numbers its pings and the game answers with that
 * same number, which the old exact match on "ping" never saw.
 */
class HeartbeatReplyTest {

    @Test fun `a numbered ping is answered with the same number`() {
        assertEquals("pong:42", heartbeatReply("ping:42"))
    }

    @Test fun `the round trip time the server reports is not echoed back`() {
        assertEquals("pong:42", heartbeatReply("ping:42:87"))
    }

    @Test fun `a bare ping from an older server still gets a bare pong`() {
        assertEquals("pong", heartbeatReply("ping"))
        assertEquals("pong", heartbeatReply("\"ping\""))
    }

    @Test fun `a ping without a number is not answered`() {
        assertNull(heartbeatReply("ping:"))
        assertNull(heartbeatReply("ping::87"))
        assertNull(heartbeatReply("ping:abc"))
        assertNull(heartbeatReply("ping:42:"))
    }

    @Test fun `game messages are not heartbeats`() {
        assertNull(heartbeatReply("""{"type":"Welcome"}"""))
        assertNull(heartbeatReply("pong:42"))
        assertNull(heartbeatReply("pingx"))
    }
}
