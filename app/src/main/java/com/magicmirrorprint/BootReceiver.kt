package com.magicmirrorprint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED so the watcher service restarts automatically
 * if the device is ever rebooted — no manual app launch needed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = AppPrefs(context)
            if (prefs.isConfigured()) {
                Log.i("MagicMirrorPrint/Boot", "Boot detected — starting WatcherService")
                val serviceIntent = Intent(context, WatcherService::class.java)
                context.startForegroundService(serviceIntent)
            } else {
                Log.i("MagicMirrorPrint/Boot", "Boot detected but app not configured — skipping auto-start")
            }
        }
    }
}
