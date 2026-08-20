package com.mgafk.app.data.websocket

import java.util.concurrent.atomic.AtomicLong

/**
 * Hands out the `commandSequence` numbers the server now requires on every
 * command the game wraps in a `QuinoaCommand` envelope (see
 * [GameActions.quinoaCommand]).
 *
 * The server seeds us on every connect: `Welcome.executedCommandSequence` is
 * the last sequence it executed, so the next command we send must be that
 * value plus one. Numbers must be *contiguous* - a gap makes the server reject
 * that command with `invalid_sequence` and every later command too, because
 * the gap never closes.
 *
 * We own our socket and are its only sender, so seed-and-increment is enough:
 * no high-water tracking, and reconnects need no special handling because each
 * Welcome re-seeds. Mirrors the game's own counter, which likewise starts at
 * [FIRST_SEQUENCE] before the first Welcome arrives.
 */
class CommandSequencer {
    private val nextSequence = AtomicLong(FIRST_SEQUENCE)

    /** Seeds from `Welcome.executedCommandSequence`. Called on every Welcome. */
    fun seed(executedCommandSequence: Long) {
        nextSequence.set(executedCommandSequence + 1)
    }

    /** Consumes and returns the number for the command being sent right now. */
    fun next(): Long = nextSequence.getAndIncrement()

    /** Back to the pre-Welcome value, for a fresh connection. */
    fun reset() {
        nextSequence.set(FIRST_SEQUENCE)
    }

    companion object {
        private const val FIRST_SEQUENCE = 1L
    }
}
