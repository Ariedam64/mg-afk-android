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

/** Notification IDs owned across all "tap to resume" entry points. */
internal object ResumeNotificationIds {
    const val FROM_AFK_SERVICE = 2
    const val FROM_BOOT_RECEIVER = 3
    const val FROM_WATCHDOG_WORKER = 4
}

/**
 * Build and post the "MG AFK was stopped — tap to resume" notification used
 * by every code path that detects the service died: AfkService null-intent
 * restart, BootReceiver, AfkWatchdogWorker, and any future entry point.
 */
internal fun postResumeNotification(
    context: Context,
    notificationId: Int,
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
    val notif = NotificationCompat.Builder(context, MgAfkApp.CHANNEL_ALERTS)
        .setContentTitle("MG AFK was stopped")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_rotate)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    context.getSystemService(NotificationManager::class.java)
        ?.notify(notificationId, notif)
}

/**
 * Wrap [Service.startForeground] with the typeMask Android 14+ enforces for
 * the `dataSync|mediaPlayback` combo we use on every FGS in the app.
 */
internal fun Service.startForegroundWithFullTypeMask(
    notificationId: Int,
    notification: Notification,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    } else {
        startForeground(notificationId, notification)
    }
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
