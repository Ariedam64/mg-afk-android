package com.mgafk.app.ui.screens.minigames

import android.content.Context
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mgafk.app.R

/**
 * Lightweight SoundPool wrapper for mini-game SFX.
 * Create via [rememberSoundManager] inside a Composable.
 */
class SoundManager(context: Context) {

    private val pool = SoundPool.Builder().setMaxStreams(6).build()

    private val sounds = mapOf(
        Sfx.BET to pool.load(context, R.raw.sfx_coin_bet, 1),
        Sfx.WIN to pool.load(context, R.raw.sfx_coin_win, 1),
        Sfx.WIN_COINS to pool.load(context, R.raw.sfx_win_coins, 1),
        Sfx.BIG_WIN to pool.load(context, R.raw.sfx_big_win, 1),
        Sfx.JACKPOT to pool.load(context, R.raw.sfx_jackpot, 1),
        Sfx.LOSE to pool.load(context, R.raw.sfx_lose, 1),
        Sfx.CASHOUT to pool.load(context, R.raw.sfx_cashout, 1),
        Sfx.CARD_DEAL to pool.load(context, R.raw.sfx_card_deal, 1),
        Sfx.CARD_FLIP to pool.load(context, R.raw.sfx_card_flip, 1),
        Sfx.BUTTON to pool.load(context, R.raw.sfx_button, 1),
        Sfx.CRASH_RISING to pool.load(context, R.raw.sfx_crash_rising, 1),
        Sfx.SLOTS_LEVER to pool.load(context, R.raw.sfx_slots_lever, 1),
        Sfx.SLOTS_SPINNING to pool.load(context, R.raw.sfx_slots_spinning, 1),
        Sfx.REEL_STOP to pool.load(context, R.raw.sfx_reel_stop, 1),
        Sfx.DICE_ROLL to pool.load(context, R.raw.sfx_dice_roll, 1),
        Sfx.REVEAL to pool.load(context, R.raw.sfx_reveal, 1),
        Sfx.STREAK to pool.load(context, R.raw.sfx_streak, 1),
    )

    // Track active stream IDs for stopping
    private val activeStreams = mutableMapOf<Sfx, Int>()

    fun play(sfx: Sfx, volume: Float = 1f, loop: Boolean = false): Int {
        val id = sounds[sfx] ?: return 0
        val streamId = pool.play(id, volume, volume, 1, if (loop) -1 else 0, 1f)
        if (loop) activeStreams[sfx] = streamId
        return streamId
    }

    fun stop(sfx: Sfx) {
        activeStreams.remove(sfx)?.let { pool.stop(it) }
    }

    fun release() {
        pool.release()
    }
}

enum class Sfx {
    BET, WIN, WIN_COINS, BIG_WIN, JACKPOT, LOSE, CASHOUT,
    CARD_DEAL, CARD_FLIP, BUTTON,
    CRASH_RISING,
    SLOTS_LEVER, SLOTS_SPINNING, REEL_STOP,
    DICE_ROLL, REVEAL, STREAK,
}

@Composable
fun rememberSoundManager(): SoundManager {
    val context = LocalContext.current
    val manager = remember { SoundManager(context) }
    DisposableEffect(Unit) {
        onDispose { manager.release() }
    }
    return manager
}
