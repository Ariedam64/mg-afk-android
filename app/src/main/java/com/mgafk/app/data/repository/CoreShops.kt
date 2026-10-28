package com.mgafk.app.data.repository

/** The four shops every room has, listed before any event shop. */
object CoreShops {

    /** (display label, shop type) in the order the shop and alert screens list them. */
    val LABELED = listOf(
        "Seeds" to "seed",
        "Tools" to "tool",
        "Eggs" to "egg",
        "Decors" to "decor",
    )

    /** Just the shop types, to tell core shops from event shops. */
    val KEYS = LABELED.map { it.second }.toSet()
}
