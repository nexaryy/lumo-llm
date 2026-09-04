package me.proton.android.lumo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.lumo.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local notifications (no Firebase / FCM) for the noGms build.
 *
 * - Channel `lumo::status` — used by the foreground service while the model is generating,
 *   so the user can see progress in the system shade.
 * - Channel `lumo::done`    — fired when the model finishes (or fails). Tapping it reopens
 *   MainActivity at the relevant conversation.
 */
@Singleton
class LumoNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        ensureChannels(context)
    }

    fun showStreamingNotification(conversationId: Long, lumoName: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.lumo_icon)
            .setContentTitle(lumoName)
            .setContentText(context.getString(R.string.notif_streaming_text))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIF_ID_STREAMING_BASE + (conversationId and 0xFF).toInt(), n)
    }

    fun cancelStreamingNotification(conversationId: Long) {
        NotificationManagerCompat.from(context)
            .cancel(NOTIF_ID_STREAMING_BASE + (conversationId and 0xFF).toInt())
    }

    fun showDoneNotification(
        conversationId: Long,
        lumoName: String,
        success: Boolean,
        preview: String,
    ) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("lumo://conversation/$conversationId")
            setClassName(context.packageName, "me.proton.android.lumo.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            conversationId.toInt(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (success) {
            context.getString(R.string.notif_done_title, lumoName)
        } else {
            context.getString(R.string.notif_error_title, lumoName)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.lumo_icon)
            .setContentTitle(title)
            .setContentText(preview.take(NOTIF_PREVIEW_MAX_LEN))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIF_ID_DONE_BASE + (conversationId and 0xFF).toInt(), n)
    }

    companion object {
        const val CHANNEL_STATUS = "lumo::status"
        const val CHANNEL_DONE = "lumo::done"
        private const val NOTIF_ID_STREAMING_BASE = 9000
        private const val NOTIF_ID_DONE_BASE = 9100
        private const val NOTIF_PREVIEW_MAX_LEN = 200

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val status = NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_status_desc)
                setShowBadge(false)
            }
            val done = NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_done_desc)
                enableVibration(true)
            }
            nm.createNotificationChannels(listOf(status, done))
        }
    }
}
