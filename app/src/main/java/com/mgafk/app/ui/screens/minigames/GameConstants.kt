package com.mgafk.app.ui.screens.minigames

import java.text.NumberFormat
import java.util.Locale

/** Shared bread currency sprite URL used across all mini-game UIs. */
internal const val BREAD_SPRITE_URL = "https://i.imgur.com/HlvVrpI.png"

/** Shared US number formatter for displaying balances, bets, and payouts. */
internal val numberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US)
