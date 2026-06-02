package com.mgafk.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mgafk.app.MainActivity
import com.mgafk.app.MgAfkApp

/**
 * Single notification id for the "tap to resume" notification, shared by every
 * entry point so they replace each other (never stack) and can be cleared from
 * any process via [cancelResumeNotification].
 */
internal const val RESUME_NOTIFICATION_ID = 2

/**
 * Build and post the "MG AFK was stopped, tap to resume" notification used by
 * every code path that detects the service died: AfkService restart,
 * BootReceiver, AfkWatchdogWorker. Always posts under [RESUME_NOTIFICATION_ID],
 * so repeated posts update the single notification instead of stacking.
 */
internal fun postResumeNotification(
    context: Context,
    pendingCount: Int = 1,
) {
    val pendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val text = if (pendingCount <= 1) "Tap to resume your session"
        else "Tap to resume your $pendingCount sessions"
    val notif = NotificationCompat.Builder(context, MgAfkApp.CHANNEL_RESUME)
        .setContentTitle("MG AFK was stopped")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_rotate)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    context.getSystemService(NotificationManager::class.java)
        ?.notify(RESUME_NOTIFICATION_ID, notif)
}

/**
 * Clear the "tap to resume" notification once a session is live again (or there
 * is nothing left to resume). Safe to call from any process.
 */
internal fun cancelResumeNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java)
        ?.cancel(RESUME_NOTIFICATION_ID)
}

/**
 * Build a PendingIntent that starts a service as a foreground service. Fired
 * from our allow-while-idle restart alarms, this is the correct API: such
 * alarms are exempt from the Android 12+ background-FGS-start restriction,
 * whereas a plain getService() background start would throw on modern OEMs.
 * Every alarm-driven restart promotes itself via startForeground() in
 * onStartCommand, so the foreground contract is honored. (minSdk 26, so
 * getForegroundService is always available.)
 */
internal fun Context.foregroundServicePendingIntent(
    requestCode: Int,
    intent: Intent,
): PendingIntent = PendingIntent.getForegroundService(
    this,
    requestCode,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/**
 * Promote this service to the foreground as a `mediaPlayback` FGS, backed by the
 * app's silent audio loop.
 *
 * We deliberately do NOT use the `dataSync` type: on Android 15 (API 35),
 * dataSync foreground services are capped at 6 hours per 24h, after which
 * [Service.startForeground] throws ForegroundServiceStartNotAllowedException
 * ("time limit already exhausted") and would crash an always-on AFK service in
 * a loop. `mediaPlayback` is not time-limited and is the same type that makes
 * OEM task killers leave us alone.
 *
 * Returns true on success, false if the system refused the promotion (the
 * caller should then stop instead of crashing).
 */
internal fun Service.tryStartForegroundAsMediaPlayback(
    notificationId: Int,
    notification: Notification,
): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    } else {
        startForeground(notificationId, notification)
    }
    true
} catch (_: Exception) {
    false
}

/**
 * Owns an [AudioTrack] that loops a buffer of pure silence forever. This is
 * what makes our `mediaPlayback` foreground service type honest: we have a
 * genuine active audio output, so OEM task killers leave us alone for the
 * same reason they leave Spotify alone. Hardware loop, ~88 KB RAM, near-zero
 * CPU.
 */
internal class SilentAudioLoop {
    private var track: AudioTrack? = null

    fun start(): Boolean {
        if (track != null) return true
        return try {
            val sampleRate = 44100
            val samples = ShortArray(sampleRate)
            val built = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            built.write(samples, 0, samples.size)
            built.setLoopPoints(0, samples.size, -1)
            built.setVolume(0f)
            built.play()
            track = built
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        val current = track ?: return
        track = null
        try {
            current.stop()
            current.release()
        } catch (_: Exception) {}
    }
}
