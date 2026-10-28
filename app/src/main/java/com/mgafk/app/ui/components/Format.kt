package com.mgafk.app.ui.components

/**
 * A countdown short enough for a chip: "45s", "2m 10s", "1h 04m". Seconds are dropped
 * once hours show, since nobody waits on the second for an event an hour away.
 */
fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "%dh %02dm".format(h, m)
        m > 0 -> "%dm %02ds".format(m, s)
        else -> "%ds".format(s)
    }
}

/** "mm:ss" for timers that are always under an hour, like shop restocks. */
fun formatClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

/**
 * Time left until [endTime] as the garden and egg tiles show it: "2m 30s", "1h 5m", "45s",
 * or "" once [now] has reached it.
 */
fun formatTimeRemaining(endTime: Long, now: Long): String {
    val remaining = (endTime - now).coerceAtLeast(0)
    if (remaining <= 0) return ""
    val totalSec = remaining / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** Compact item count for a tile badge: "999", "1K", "1.5K", "12K", "2M", "1.2M". */
fun formatQuantity(q: Int): String = when {
    q >= 1_000_000 -> oneDecimal(q / 1_000_000.0) + "M"
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> oneDecimal(q / 1000.0) + "K"
    else -> "$q"
}

/** One decimal in the device locale, or none when it would read ".0". */
private fun oneDecimal(value: Double): String {
    val tenths = Math.round(value * 10)
    return if (tenths % 10 == 0L) "${tenths / 10}" else "%.1f".format(tenths / 10.0)
}
