package com.mgafk.app.data.model

/**
 * A one-time hint shown at the top of a screen until the player dismisses it.
 *
 * [key] is the DataStore key the dismissal is saved under. These are the keys each tip had
 * when it was stored on its own, so a tip dismissed before stays dismissed.
 */
enum class Tip(val key: String) {
    SHOP("mgafk.shopTipDismissed"),
    TROUGH("mgafk.troughTipDismissed"),
    PET("mgafk.petTipDismissed"),
    TEAM("mgafk.teamTipDismissed"),
    GARDEN("mgafk.gardenTipDismissed"),
    STORAGE("mgafk.storageTipDismissed"),
    SEED("mgafk.seedTipDismissed"),
    EGG("mgafk.eggTipDismissed"),
    PLANT("mgafk.plantTipDismissed"),
}
