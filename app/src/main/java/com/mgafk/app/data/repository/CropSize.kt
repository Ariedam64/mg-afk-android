package com.mgafk.app.data.repository

import kotlin.math.roundToInt

/**
 * Crop size, as the game models it since schema V30.
 *
 * A crop carries one whole number from [MIN] to [MAX], and that same number drives its value,
 * its weight and how big it is drawn. Before V30 the wire value was a fraction (1.0 up to the
 * species' max multiplier) that every reader had to convert, which is what made a "+8%" boost
 * look like it did nothing: the 8% applied to the fraction, not to the size on screen.
 *
 * The multiplier that used to be sent directly is now derived here: [MIN] is worth the base
 * price, [MAX] is worth the species' `maxSizeMultiplier`, and everything in between is linear.
 */
object CropSize {

    /** A freshly matured crop. Nothing is ever smaller. */
    const val MIN = 50

    /** A fully grown crop, worth the species' full multiplier. */
    const val MAX = 100

    /**
     * The size the game would store for [raw]: rounded, then held inside [MIN]..[MAX].
     *
     * A value the game did not send, or one that is not a finite number, reads as [MIN] rather
     * than as zero, so a missing field can never make a crop look worthless.
     */
    fun clamp(raw: Double): Int =
        if (raw.isFinite()) raw.roundToInt().coerceIn(MIN, MAX) else MIN

    /** True once a crop cannot grow any further, which is what a size boost checks. */
    fun isMax(size: Int): Boolean = size >= MAX

    /** How far along the size range [size] sits, from 0 at [MIN] to 1 at [MAX]. */
    fun fraction(size: Int): Double = (size.coerceIn(MIN, MAX) - MIN).toDouble() / (MAX - MIN)

    /**
     * What [size] multiplies the species' base value and weight by: 1.0 at [MIN], rising
     * linearly to [maxSizeMultiplier] at [MAX].
     */
    fun multiplier(size: Int, maxSizeMultiplier: Double): Double =
        1.0 + (maxSizeMultiplier - 1.0) * fraction(size)
}
