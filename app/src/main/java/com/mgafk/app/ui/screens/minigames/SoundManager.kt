package com.mgafk.app.ui.screens.minigames

import android.content.Context
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mgafk.app.R

/**
 * SoundPool wrapper for mini-game SFX.
 *
 * The mini-games are migrating to the in-game [MgSfx] sound bank (sliced from the
 * official Magic Garden audio sprite). The legacy [Sfx] enum is mapped onto the closest
 * MG-equivalent so existing games keep working until each one is migrated explicitly.
 */
class SoundManager(context: Context) {

    private val pool = SoundPool.Builder().setMaxStreams(8).build()

    private val mgSounds: Map<MgSfx, Int> = mapOf(
        MgSfx.ALL_SHOPS_RESTOCKED to pool.load(context, R.raw.mg_all_shops_restocked, 1),
        MgSfx.BILLBOARD to pool.load(context, R.raw.mg_billboard, 1),
        MgSfx.BREAD_DONE_POPPING to pool.load(context, R.raw.mg_bread_done_popping, 1),
        MgSfx.BREAD_POPPING_LOOP to pool.load(context, R.raw.mg_bread_popping_loop, 1),
        MgSfx.BREAD_PULL_LEVER to pool.load(context, R.raw.mg_bread_pull_lever, 1),
        MgSfx.BREAD_SPARKLE_LOOP to pool.load(context, R.raw.mg_bread_sparkle_loop, 1),
        MgSfx.BREAD_YOUVE_GOT_BREAD to pool.load(context, R.raw.mg_bread_youve_got_bread, 1),
        MgSfx.BUTTON_AVATAR_RANDOMIZER to pool.load(context, R.raw.mg_button_avatar_randomizer, 1),
        MgSfx.BUTTON_AVATAR_THATS_ME to pool.load(context, R.raw.mg_button_avatar_thats_me, 1),
        MgSfx.BUTTON_FORWARD to pool.load(context, R.raw.mg_button_forward, 1),
        MgSfx.BUTTON_MAIN to pool.load(context, R.raw.mg_button_main, 1),
        MgSfx.BUTTON_MODAL_CLOSE to pool.load(context, R.raw.mg_button_modal_close, 1),
        MgSfx.BUTTON_MODAL_OPEN to pool.load(context, R.raw.mg_button_modal_open, 1),
        MgSfx.CARD_CLOSE to pool.load(context, R.raw.mg_card_close, 1),
        MgSfx.CARD_OPEN_A to pool.load(context, R.raw.mg_card_open_a, 1),
        MgSfx.CARD_OPEN_B to pool.load(context, R.raw.mg_card_open_b, 1),
        MgSfx.CARD_PEAK_A to pool.load(context, R.raw.mg_card_peak_a, 1),
        MgSfx.CARD_PEAK_B to pool.load(context, R.raw.mg_card_peak_b, 1),
        MgSfx.CARD_PEAK_C to pool.load(context, R.raw.mg_card_peak_c, 1),
        MgSfx.COIN_BUY_A to pool.load(context, R.raw.mg_coin_buy_a, 1),
        MgSfx.COIN_BUY_B to pool.load(context, R.raw.mg_coin_buy_b, 1),
        MgSfx.COIN_BUY_C to pool.load(context, R.raw.mg_coin_buy_c, 1),
        MgSfx.COIN_BUY_D to pool.load(context, R.raw.mg_coin_buy_d, 1),
        MgSfx.DECOR_FLIP to pool.load(context, R.raw.mg_decor_flip, 1),
        MgSfx.DECOR_PLACE to pool.load(context, R.raw.mg_decor_place, 1),
        MgSfx.DECOR_ROTATE to pool.load(context, R.raw.mg_decor_rotate, 1),
        MgSfx.DESTROY_OBJECT to pool.load(context, R.raw.mg_destroy_object, 1),
        MgSfx.DONUT_BUY_A to pool.load(context, R.raw.mg_donut_buy_a, 1),
        MgSfx.DONUT_BUY_B to pool.load(context, R.raw.mg_donut_buy_b, 1),
        MgSfx.DONUT_BUY_C to pool.load(context, R.raw.mg_donut_buy_c, 1),
        MgSfx.DONUT_BUY_D to pool.load(context, R.raw.mg_donut_buy_d, 1),
        MgSfx.EMOTE_SFX_HAPPY to pool.load(context, R.raw.mg_emote_sfx_happy, 1),
        MgSfx.EMOTE_SFX_HEART to pool.load(context, R.raw.mg_emote_sfx_heart, 1),
        MgSfx.EMOTE_SFX_LOVE to pool.load(context, R.raw.mg_emote_sfx_love, 1),
        MgSfx.EMOTE_SFX_MAD to pool.load(context, R.raw.mg_emote_sfx_mad, 1),
        MgSfx.EMOTE_SFX_SAD to pool.load(context, R.raw.mg_emote_sfx_sad, 1),
        MgSfx.FOOTSTEP_SNOW_A to pool.load(context, R.raw.mg_footstep_snow_a, 1),
        MgSfx.FOOTSTEP_SNOW_B to pool.load(context, R.raw.mg_footstep_snow_b, 1),
        MgSfx.FOOTSTEP_SNOW_C to pool.load(context, R.raw.mg_footstep_snow_c, 1),
        MgSfx.FOOTSTEP_A to pool.load(context, R.raw.mg_footstep_a, 1),
        MgSfx.FOOTSTEP_B to pool.load(context, R.raw.mg_footstep_b, 1),
        MgSfx.FOOTSTEP_C to pool.load(context, R.raw.mg_footstep_c, 1),
        MgSfx.FOOTSTEP_D to pool.load(context, R.raw.mg_footstep_d, 1),
        MgSfx.HARVEST_A to pool.load(context, R.raw.mg_harvest_a, 1),
        MgSfx.HARVEST_B to pool.load(context, R.raw.mg_harvest_b, 1),
        MgSfx.HARVEST_C to pool.load(context, R.raw.mg_harvest_c, 1),
        MgSfx.HARVEST_D to pool.load(context, R.raw.mg_harvest_d, 1),
        MgSfx.HARVEST_SPAM_A to pool.load(context, R.raw.mg_harvest_spam_a, 1),
        MgSfx.HARVEST_SPAM_B to pool.load(context, R.raw.mg_harvest_spam_b, 1),
        MgSfx.HARVEST_SPAM_C to pool.load(context, R.raw.mg_harvest_spam_c, 1),
        MgSfx.HARVEST_SPAM_D to pool.load(context, R.raw.mg_harvest_spam_d, 1),
        MgSfx.JOURNAL_CLOSES to pool.load(context, R.raw.mg_journal_closes, 1),
        MgSfx.JOURNAL_NEXT_PAGE to pool.load(context, R.raw.mg_journal_next_page, 1),
        MgSfx.JOURNAL_OPENS to pool.load(context, R.raw.mg_journal_opens, 1),
        MgSfx.JOURNAL_STAMPED_A to pool.load(context, R.raw.mg_journal_stamped_a, 1),
        MgSfx.JOURNAL_STAMPED_B to pool.load(context, R.raw.mg_journal_stamped_b, 1),
        MgSfx.JOURNAL_STAMPED_C to pool.load(context, R.raw.mg_journal_stamped_c, 1),
        MgSfx.KEYBOARD_ENTER to pool.load(context, R.raw.mg_keyboard_enter, 1),
        MgSfx.KEYBOARD_TYPE_TAP_A to pool.load(context, R.raw.mg_keyboard_type_tap_a, 1),
        MgSfx.KEYBOARD_TYPE_TAP_B to pool.load(context, R.raw.mg_keyboard_type_tap_b, 1),
        MgSfx.KEYBOARD_TYPE_TAP_C to pool.load(context, R.raw.mg_keyboard_type_tap_c, 1),
        MgSfx.KEYBOARD_TYPE_TAP_D to pool.load(context, R.raw.mg_keyboard_type_tap_d, 1),
        MgSfx.NEWSPAPER_HIT to pool.load(context, R.raw.mg_newspaper_hit, 1),
        MgSfx.NEWSPAPER_OPEN to pool.load(context, R.raw.mg_newspaper_open, 1),
        MgSfx.OBJECT_DROP to pool.load(context, R.raw.mg_object_drop, 1),
        MgSfx.OBJECT_PICK_UP to pool.load(context, R.raw.mg_object_pick_up, 1),
        MgSfx.PET_BEE to pool.load(context, R.raw.mg_pet_bee, 1),
        MgSfx.PET_BUTTERFLY to pool.load(context, R.raw.mg_pet_butterfly, 1),
        MgSfx.PET_CAPYBARA to pool.load(context, R.raw.mg_pet_capybara, 1),
        MgSfx.PET_CHICKEN to pool.load(context, R.raw.mg_pet_chicken, 1),
        MgSfx.PET_COW to pool.load(context, R.raw.mg_pet_cow, 1),
        MgSfx.PET_DRAGONFLY to pool.load(context, R.raw.mg_pet_dragonfly, 1),
        MgSfx.PET_EFFECT_ACTIVE to pool.load(context, R.raw.mg_pet_effect_active, 1),
        MgSfx.PET_FIRE_HORSE to pool.load(context, R.raw.mg_pet_fire_horse, 1),
        MgSfx.PET_GOAT to pool.load(context, R.raw.mg_pet_goat, 1),
        MgSfx.PET_HORSE to pool.load(context, R.raw.mg_pet_horse, 1),
        MgSfx.PET_HUNGRY to pool.load(context, R.raw.mg_pet_hungry, 1),
        MgSfx.PET_PEACOCK to pool.load(context, R.raw.mg_pet_peacock, 1),
        MgSfx.PET_PIG to pool.load(context, R.raw.mg_pet_pig, 1),
        MgSfx.PET_PONY to pool.load(context, R.raw.mg_pet_pony, 1),
        MgSfx.PET_RABBIT to pool.load(context, R.raw.mg_pet_rabbit, 1),
        MgSfx.PET_SNAIL to pool.load(context, R.raw.mg_pet_snail, 1),
        MgSfx.PET_SNOW_FOX to pool.load(context, R.raw.mg_pet_snow_fox, 1),
        MgSfx.PET_SQUIRREL to pool.load(context, R.raw.mg_pet_squirrel, 1),
        MgSfx.PET_STOAT to pool.load(context, R.raw.mg_pet_stoat, 1),
        MgSfx.PET_TURKEY to pool.load(context, R.raw.mg_pet_turkey, 1),
        MgSfx.PET_TURTLE to pool.load(context, R.raw.mg_pet_turtle, 1),
        MgSfx.PET_WHITE_CARIBOU to pool.load(context, R.raw.mg_pet_white_caribou, 1),
        MgSfx.PET_WORM to pool.load(context, R.raw.mg_pet_worm, 1),
        MgSfx.PLANT_HARVESTED_A to pool.load(context, R.raw.mg_plant_harvested_a, 1),
        MgSfx.PLANT_HARVESTED_B to pool.load(context, R.raw.mg_plant_harvested_b, 1),
        MgSfx.PLANT_HARVESTED_C to pool.load(context, R.raw.mg_plant_harvested_c, 1),
        MgSfx.PLANT_HARVESTED_D to pool.load(context, R.raw.mg_plant_harvested_d, 1),
        MgSfx.PLANT_SEED_A to pool.load(context, R.raw.mg_plant_seed_a, 1),
        MgSfx.PLANT_SEED_B to pool.load(context, R.raw.mg_plant_seed_b, 1),
        MgSfx.PLANT_SEED_C to pool.load(context, R.raw.mg_plant_seed_c, 1),
        MgSfx.PLANT_MATURES_A to pool.load(context, R.raw.mg_plant_matures_a, 1),
        MgSfx.PLANT_MATURES_B to pool.load(context, R.raw.mg_plant_matures_b, 1),
        MgSfx.PLANT_MATURES_C to pool.load(context, R.raw.mg_plant_matures_c, 1),
        MgSfx.PLAYER_APPEARS to pool.load(context, R.raw.mg_player_appears, 1),
        MgSfx.SCORE_PROMOTION to pool.load(context, R.raw.mg_score_promotion, 1),
        MgSfx.SEED_SHOP_RESTOCKED to pool.load(context, R.raw.mg_seed_shop_restocked, 1),
        MgSfx.SELL to pool.load(context, R.raw.mg_sell, 1),
        MgSfx.SHOP_OPEN to pool.load(context, R.raw.mg_shop_open, 1),
        MgSfx.SNOWBALL_FALL_A to pool.load(context, R.raw.mg_snowball_fall_a, 1),
        MgSfx.SNOWBALL_FALL_B to pool.load(context, R.raw.mg_snowball_fall_b, 1),
        MgSfx.SNOWBALL_FALL_C to pool.load(context, R.raw.mg_snowball_fall_c, 1),
        MgSfx.SNOWBALL_THROW_A to pool.load(context, R.raw.mg_snowball_throw_a, 1),
        MgSfx.SNOWBALL_THROW_B to pool.load(context, R.raw.mg_snowball_throw_b, 1),
        MgSfx.SNOWBALL_THROW_C to pool.load(context, R.raw.mg_snowball_throw_c, 1),
        MgSfx.SPRAY_BOTTLE to pool.load(context, R.raw.mg_spray_bottle, 1),
        MgSfx.STREAK_CONTINUES to pool.load(context, R.raw.mg_streak_continues, 1),
        MgSfx.WATERING_CAN_SPEEDUP to pool.load(context, R.raw.mg_watering_can_speedup, 1),
    )

    /** Legacy enum → MG sound mapping. Replaced as games migrate to MgSfx directly. */
    private val legacyMap: Map<Sfx, MgSfx> = mapOf(
        Sfx.BET to MgSfx.COIN_BUY_A,
        Sfx.WIN to MgSfx.PLANT_MATURES_A,
        Sfx.WIN_COINS to MgSfx.SELL,
        Sfx.BIG_WIN to MgSfx.SCORE_PROMOTION,
        Sfx.JACKPOT to MgSfx.BREAD_YOUVE_GOT_BREAD,
        Sfx.LOSE to MgSfx.DECOR_ROTATE,
        Sfx.CASHOUT to MgSfx.SELL,
        Sfx.CARD_DEAL to MgSfx.CARD_PEAK_A,
        Sfx.CARD_FLIP to MgSfx.CARD_OPEN_A,
        Sfx.BUTTON to MgSfx.BUTTON_MAIN,
        Sfx.CRASH_RISING to MgSfx.BREAD_SPARKLE_LOOP,
        Sfx.ALARM to MgSfx.NEWSPAPER_HIT,
        Sfx.SLOTS_LEVER to MgSfx.BREAD_PULL_LEVER,
        Sfx.SLOTS_SPINNING to MgSfx.BREAD_POPPING_LOOP,
        Sfx.REEL_STOP to MgSfx.OBJECT_DROP,
        Sfx.DICE_ROLL to MgSfx.SNOWBALL_FALL_A,
        Sfx.REVEAL to MgSfx.CARD_OPEN_B,
        Sfx.STREAK to MgSfx.STREAK_CONTINUES,
        Sfx.CHIP_LAY to MgSfx.DECOR_PLACE,
        Sfx.CHIP_STACK to MgSfx.COIN_BUY_C,
        Sfx.CHIP_COLLIDE to MgSfx.OBJECT_PICK_UP,
        Sfx.PEG_HIT_1 to MgSfx.CARD_PEAK_A,
        Sfx.PEG_HIT_2 to MgSfx.CARD_PEAK_B,
        Sfx.PEG_HIT_3 to MgSfx.CARD_PEAK_C,
        Sfx.BALL_DROP to MgSfx.OBJECT_DROP,
        Sfx.BUCKET_LAND to MgSfx.NEWSPAPER_HIT,
        Sfx.BALL_CLICK to MgSfx.CARD_PEAK_A,
        Sfx.CLICK_SOFT to MgSfx.BUTTON_FORWARD,
        Sfx.SWITCH to MgSfx.BUTTON_MODAL_OPEN,
        Sfx.ROLLOVER to MgSfx.BUTTON_FORWARD,
        Sfx.CONFIRM to MgSfx.BUTTON_MAIN,
        Sfx.POWERUP to MgSfx.BREAD_DONE_POPPING,
        Sfx.BONG to MgSfx.PLANT_MATURES_C,
        Sfx.PHASER_UP to MgSfx.BREAD_SPARKLE_LOOP,
    )

    private val activeStreams = mutableMapOf<MgSfx, Int>()

    fun play(sfx: MgSfx, volume: Float = 1f, loop: Boolean = false, rate: Float = 1f): Int {
        val id = mgSounds[sfx] ?: return 0
        val clampedRate = rate.coerceIn(0.5f, 2f)
        val streamId = pool.play(id, volume, volume, 1, if (loop) -1 else 0, clampedRate)
        if (loop) activeStreams[sfx] = streamId
        return streamId
    }

    fun stop(sfx: MgSfx) {
        activeStreams.remove(sfx)?.let { pool.stop(it) }
    }

    /** Legacy compatibility — translates a legacy [Sfx] to its mapped [MgSfx]. */
    fun play(sfx: Sfx, volume: Float = 1f, loop: Boolean = false, rate: Float = 1f): Int {
        val mapped = legacyMap[sfx] ?: return 0
        return play(mapped, volume, loop, rate)
    }

    fun stop(sfx: Sfx) {
        val mapped = legacyMap[sfx] ?: return
        stop(mapped)
    }

    fun release() {
        pool.release()
    }
}

/**
 * Magic Garden sound bank — every individual sprite sliced from the game's master
 * sfx.mp3. Names mirror the sprite manifest (snake_case + UPPER_SNAKE).
 */
enum class MgSfx {
    ALL_SHOPS_RESTOCKED,
    BILLBOARD,
    BREAD_DONE_POPPING,
    BREAD_POPPING_LOOP,
    BREAD_PULL_LEVER,
    BREAD_SPARKLE_LOOP,
    BREAD_YOUVE_GOT_BREAD,
    BUTTON_AVATAR_RANDOMIZER,
    BUTTON_AVATAR_THATS_ME,
    BUTTON_FORWARD,
    BUTTON_MAIN,
    BUTTON_MODAL_CLOSE,
    BUTTON_MODAL_OPEN,
    CARD_CLOSE,
    CARD_OPEN_A,
    CARD_OPEN_B,
    CARD_PEAK_A,
    CARD_PEAK_B,
    CARD_PEAK_C,
    COIN_BUY_A,
    COIN_BUY_B,
    COIN_BUY_C,
    COIN_BUY_D,
    DECOR_FLIP,
    DECOR_PLACE,
    DECOR_ROTATE,
    DESTROY_OBJECT,
    DONUT_BUY_A,
    DONUT_BUY_B,
    DONUT_BUY_C,
    DONUT_BUY_D,
    EMOTE_SFX_HAPPY,
    EMOTE_SFX_HEART,
    EMOTE_SFX_LOVE,
    EMOTE_SFX_MAD,
    EMOTE_SFX_SAD,
    FOOTSTEP_SNOW_A,
    FOOTSTEP_SNOW_B,
    FOOTSTEP_SNOW_C,
    FOOTSTEP_A,
    FOOTSTEP_B,
    FOOTSTEP_C,
    FOOTSTEP_D,
    HARVEST_A,
    HARVEST_B,
    HARVEST_C,
    HARVEST_D,
    HARVEST_SPAM_A,
    HARVEST_SPAM_B,
    HARVEST_SPAM_C,
    HARVEST_SPAM_D,
    JOURNAL_CLOSES,
    JOURNAL_NEXT_PAGE,
    JOURNAL_OPENS,
    JOURNAL_STAMPED_A,
    JOURNAL_STAMPED_B,
    JOURNAL_STAMPED_C,
    KEYBOARD_ENTER,
    KEYBOARD_TYPE_TAP_A,
    KEYBOARD_TYPE_TAP_B,
    KEYBOARD_TYPE_TAP_C,
    KEYBOARD_TYPE_TAP_D,
    NEWSPAPER_HIT,
    NEWSPAPER_OPEN,
    OBJECT_DROP,
    OBJECT_PICK_UP,
    PET_BEE,
    PET_BUTTERFLY,
    PET_CAPYBARA,
    PET_CHICKEN,
    PET_COW,
    PET_DRAGONFLY,
    PET_EFFECT_ACTIVE,
    PET_FIRE_HORSE,
    PET_GOAT,
    PET_HORSE,
    PET_HUNGRY,
    PET_PEACOCK,
    PET_PIG,
    PET_PONY,
    PET_RABBIT,
    PET_SNAIL,
    PET_SNOW_FOX,
    PET_SQUIRREL,
    PET_STOAT,
    PET_TURKEY,
    PET_TURTLE,
    PET_WHITE_CARIBOU,
    PET_WORM,
    PLANT_HARVESTED_A,
    PLANT_HARVESTED_B,
    PLANT_HARVESTED_C,
    PLANT_HARVESTED_D,
    PLANT_SEED_A,
    PLANT_SEED_B,
    PLANT_SEED_C,
    PLANT_MATURES_A,
    PLANT_MATURES_B,
    PLANT_MATURES_C,
    PLAYER_APPEARS,
    SCORE_PROMOTION,
    SEED_SHOP_RESTOCKED,
    SELL,
    SHOP_OPEN,
    SNOWBALL_FALL_A,
    SNOWBALL_FALL_B,
    SNOWBALL_FALL_C,
    SNOWBALL_THROW_A,
    SNOWBALL_THROW_B,
    SNOWBALL_THROW_C,
    SPRAY_BOTTLE,
    STREAK_CONTINUES,
    WATERING_CAN_SPEEDUP,
}

/** Legacy enum kept for unmigrated games. New code should use [MgSfx] directly. */
enum class Sfx {
    BET, WIN, WIN_COINS, BIG_WIN, JACKPOT, LOSE, CASHOUT,
    CARD_DEAL, CARD_FLIP, BUTTON,
    CRASH_RISING, ALARM,
    SLOTS_LEVER, SLOTS_SPINNING, REEL_STOP,
    DICE_ROLL, REVEAL, STREAK,
    CHIP_LAY, CHIP_STACK, CHIP_COLLIDE,
    PEG_HIT_1, PEG_HIT_2, PEG_HIT_3, BALL_DROP, BUCKET_LAND, BALL_CLICK,
    CLICK_SOFT, SWITCH, ROLLOVER,
    CONFIRM, POWERUP, BONG, PHASER_UP,
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
