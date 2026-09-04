package me.proton.android.lumo.chat

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import me.proton.android.lumo.R
import me.proton.android.lumo.notification.LumoNotifier
import javax.inject.Inject

/**
 * Lightweight foreground service that lives for as long as the model is generating a response.
 *
 * Why we need this:
 * - Keeps the streaming OkHttp call alive even if the user backgrounds the app.
 * - Surfaces an "ongoing" notification while streaming, so Android considers Lumo as
 *   actively doing work for the user (instead of killing us).
 *
 * This service is started with `startForegroundService(...)` from the chat ViewModel before
 * kicking off the LLM stream, and stopped with `stopSelf()` after [LumoNotifier.showDoneNotification]
 * has been called.
 *
 * Note: this does NOT itself talk to the LLM — that work happens in the ViewModel / repository
 * coroutine scope. The service is just a lifecycle anchor.
 */
@AndroidEntryPoint
class LlmResponseService : Service() {

    @Inject lateinit var notifier: LumoNotifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val conversationId = intent?.getLongExtra(EXTRA_CONVERSATION_ID, -1L) ?: -1L
        val lumoName = intent?.getStringExtra(EXTRA_LUMO_NAME) ?: getString(R.string.app_name)

        if (conversationId <= 0L) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notif = androidx.core.app.NotificationCompat.Builder(this, LumoNotifier.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.lumo_icon)
            .setContentTitle(lumoName)
            .setContentText(getString(R.string.notif_streaming_text))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS)
            .build()

        startForeground(NOTIF_ID, notif)
        notifier.showStreamingNotification(conversationId, lumoName)

        return START_NOT_STICKY
    }

    fun finish() {
        stopSelf()
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_LUMO_NAME = "lumo_name"
        const val NOTIF_ID = 0xC7 // "L"u"m"o → any stable small int
    }
}
