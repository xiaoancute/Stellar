package roro.stellar.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ContextCompat
import androidx.core.app.NotificationCompat
import roro.stellar.manager.MainActivity
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils.atLeast26

class ShellBridgeService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (!atLeast26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stellar stsh",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stellar)
        .setContentTitle("Stellar stsh")
        .setContentText(getString(R.string.service_running))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "stsh_bridge"
        private const val NOTIFICATION_ID = 1448

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ShellBridgeService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShellBridgeService::class.java))
        }
    }
}
