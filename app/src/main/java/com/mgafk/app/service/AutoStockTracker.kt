package com.mgafk.app.service

/**
 * Keeps auto-stock from asking twice for a move that has not happened.
 *
 * Auto-stock runs on every inventory change, and the game sends those constantly. A move the
 * server refuses leaves the item exactly where it was, so the next change asks for the same move
 * again, and the app ends up hammering the server with a request that will never succeed. That
 * is what filled the log with rejected PutItemInStorage.
 *
 * Each move is tried once. It only comes round again when the item has left the inventory and
 * come back, which is the one case where retrying can mean something new.
 *
 * State is per session, since each has its own inventory.
 */
internal class AutoStockTracker {

    /** sessionId -> the moves already asked for and not yet resolved. */
    private val attempted = mutableMapOf<String, MutableSet<String>>()

    /**
     * The subset of [candidates] worth sending now, and records them as asked for.
     *
     * Candidates that have disappeared since the last call are forgotten: the move either went
     * through or the item is gone, and either way the next one is a fresh question.
     */
    fun pending(sessionId: String, candidates: List<String>): List<String> {
        val alreadyAsked = attempted.getOrPut(sessionId) { mutableSetOf() }
        alreadyAsked.retainAll(candidates.toSet())
        val toSend = candidates.filterNot { it in alreadyAsked }
        alreadyAsked.addAll(toSend)
        return toSend
    }

    /** Drops what [sessionId] had tried, so a reconnect starts from a clean slate. */
    fun forget(sessionId: String) {
        attempted.remove(sessionId)
    }
}
