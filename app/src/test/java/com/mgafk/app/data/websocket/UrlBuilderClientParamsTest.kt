package com.mgafk.app.data.websocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The client params are asserted on the pure builder rather than on the full
 * URL: `android.net.Uri` is not available in JVM unit tests.
 */
class UrlBuilderClientParamsTest {

    private fun paramsOf(context: ClientContext): Map<String, String> =
        UrlBuilder.clientParams(context).toMap()

    @Test
    fun `first connection is a navigate with attempt 1 and no reclaim`() {
        val params = paramsOf(
            ClientContext(
                documentId = "20e1be6e-fcc1-44a5-98d3-e03d7f0ae22a",
                connectionAttempt = 1,
                navigationType = NavigationType.NAVIGATE,
                reclaimSupersededSession = false,
            )
        )

        assertFalse(params.containsKey("reclaimSupersededSession"))
        assertEquals("\"20e1be6e-fcc1-44a5-98d3-e03d7f0ae22a\"", params["clientDocumentId"])
        assertEquals("1", params["clientConnectionAttempt"])
        assertEquals("\"navigate\"", params["clientNavigationType"])
        assertEquals("\"visible\"", params["clientVisibilityState"])
    }

    @Test
    fun `retry after a superseded close reclaims the session and reloads`() {
        val params = paramsOf(
            ClientContext(
                documentId = "doc-1",
                connectionAttempt = 2,
                navigationType = NavigationType.RELOAD,
                reclaimSupersededSession = true,
            )
        )

        assertEquals("true", params["reclaimSupersededSession"])
        assertEquals("2", params["clientConnectionAttempt"])
        assertEquals("\"reload\"", params["clientNavigationType"])
    }

    @Test
    fun `reclaim comes first and the browser order is preserved`() {
        val keys = UrlBuilder.clientParams(
            ClientContext(
                documentId = "doc-1",
                connectionAttempt = 3,
                navigationType = NavigationType.RELOAD,
                reclaimSupersededSession = true,
            )
        ).map { it.first }

        assertEquals(
            listOf(
                "reclaimSupersededSession",
                "clientDocumentId",
                "clientConnectionAttempt",
                "clientNavigationType",
                "clientVisibilityState",
            ),
            keys,
        )
    }
}
