package com.magicmirrorprint

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("magicmirrorprint_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROOT_FOLDER     = "root_folder"
        private const val KEY_TARGET_FILENAME = "target_filename"
        private const val KEY_PRINTER_IP      = "printer_ip"
        private const val KEY_PRINTER_PORT    = "printer_port"
        private const val KEY_AUTO_PRINT      = "auto_print"
        private const val KEY_NOTIF_TIMEOUT   = "notif_timeout_seconds"

        // Sensible defaults
        const val DEFAULT_TARGET_FILENAME = "customer_report.pdf"
        const val DEFAULT_PRINTER_PORT    = 631
        const val DEFAULT_NOTIF_TIMEOUT   = 60
    }

    var rootFolder: String
        get() = prefs.getString(KEY_ROOT_FOLDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOT_FOLDER, value).apply()

    var targetFilename: String
        get() = prefs.getString(KEY_TARGET_FILENAME, DEFAULT_TARGET_FILENAME) ?: DEFAULT_TARGET_FILENAME
        set(value) = prefs.edit().putString(KEY_TARGET_FILENAME, value).apply()

    var printerIp: String
        get() = prefs.getString(KEY_PRINTER_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRINTER_IP, value).apply()

    var printerPort: Int
        get() = prefs.getInt(KEY_PRINTER_PORT, DEFAULT_PRINTER_PORT)
        set(value) = prefs.edit().putInt(KEY_PRINTER_PORT, value).apply()

    var autoPrint: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PRINT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PRINT, value).apply()

    var notifTimeoutSeconds: Int
        get() = prefs.getInt(KEY_NOTIF_TIMEOUT, DEFAULT_NOTIF_TIMEOUT)
        set(value) = prefs.edit().putInt(KEY_NOTIF_TIMEOUT, value).apply()

    fun isConfigured(): Boolean =
        rootFolder.isNotBlank() && printerIp.isNotBlank()
}
