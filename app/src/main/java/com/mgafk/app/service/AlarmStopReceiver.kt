package com.mgafk.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the "stop alarm" action from the alarm notification.
 * Stops the MediaPlayer and cancels the notification.
 */
class AlarmStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_ALARM) {
            AlertNotifier.stopGlobalAlarm()
        }
    }

    companion object {
        const val ACTION_STOP_ALARM = "com.mgafk.app.STOP_ALARM"
    }
}
