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
