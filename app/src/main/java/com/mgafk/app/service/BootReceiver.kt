package com.mgafk.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mgafk.app.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Fires after device boot. If any session was live before shutdown
 * (wantConnected=true survives serialization), post a notification inviting
 * the user to resume — Android 14+ doesn't let us start the activity directly
 * from a boot receiver, and we have no WS to host without UI anyway.
 */
class BootReceiver : BroadcastReceiver() {

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val repo = SessionRepository(appContext)
                val sessions = repo.loadSessions()
                val pending = sessions.count { it.wantConnected && it.cookie.isNotBlank() }
                if (pending > 0) {
                    postResumeNotification(appContext, pending)
                    // Keep nagging every ~15 min until the user taps — without
                    // this, ignoring the boot notification means silent forever.
                    AfkWatchdogWorker.schedule(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
