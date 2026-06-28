package com.magicmirrorprint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class NotificationHelper(private val context: Context) {

    companion object {
        const val WATCHER_NOTIFICATION_ID = 1001
        const val REPORT_NOTIFICATION_ID  = 1002

        private const val CHANNEL_WATCHER = "channel_watcher"
        private const val CHANNEL_REPORT  = "channel_report"
    }

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    // ─── Channels ─────────────────────────────────────────────────────────────

    private fun createChannels() {
        // Low-importance persistent channel for the foreground service indicator
        val watcherChannel = NotificationChannel(
            CHANNEL_WATCHER,
            "Report Watcher",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent indicator that Magic Mirror Print is monitoring for new reports"
        }

        // High-importance channel so report alerts pop over the magicmirror app
        val reportChannel = NotificationChannel(
            CHANNEL_REPORT,
            "New Report Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up alerts when a new client report is ready to print"
            enableVibration(true)
        }

        manager.createNotificationChannel(watcherChannel)
        manager.createNotificationChannel(reportChannel)
    }

    // ─── Watcher persistent notification ──────────────────────────────────────

    fun buildWatcherNotification(): Notification {
        // Tapping the persistent notification opens Settings
        val settingsIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, CHANNEL_WATCHER)
            .setContentTitle("Magic Mirror Print Active")
            .setContentText("Watching for new client reports…")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .build()
    }

    // ─── Report heads-up notification ─────────────────────────────────────────

    fun showReportNotification(pdfPath: String, clientName: String, scanTimestamp: String) {

        // Print action
        val printIntent = Intent(context, WatcherService::class.java).apply {
            action = WatcherService.ACTION_PRINT
            putExtra(WatcherService.EXTRA_PDF_PATH, pdfPath)
            putExtra(WatcherService.EXTRA_CLIENT_NAME, clientName)
            putExtra(WatcherService.EXTRA_SCAN_TIMESTAMP, scanTimestamp)
        }
        val printPending = PendingIntent.getService(
            context, 1, printIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, WatcherService::class.java).apply {
            action = WatcherService.ACTION_DISMISS
        }
        val dismissPending = PendingIntent.getService(
            context, 2, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_REPORT)
            .setContentTitle("📄 New Report Ready")
            .setContentText("$clientName  •  $scanTimestamp")
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("Client: $clientName\nScan: $scanTimestamp")
            )
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setAutoCancel(false)
            .setOngoing(false)
            // Action buttons shown on the heads-up and expanded notification
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Print",
                    printPending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPending
                ).build()
            )
            .build()

        manager.notify(REPORT_NOTIFICATION_ID, notification)
    }

    fun cancelReportNotification() {
        manager.cancel(REPORT_NOTIFICATION_ID)
    }
}
