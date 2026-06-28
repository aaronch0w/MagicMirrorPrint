package com.magicmirrorprint

import android.app.Service
import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import java.io.File

class WatcherService : Service() {

    companion object {
        private const val TAG = "MagicMirrorPrint/Watcher"
        const val ACTION_PRINT = "com.magicmirrorprint.ACTION_PRINT"
        const val ACTION_DISMISS = "com.magicmirrorprint.ACTION_DISMISS"
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        const val EXTRA_CLIENT_NAME = "extra_client_name"
        const val EXTRA_SCAN_TIMESTAMP = "extra_scan_timestamp"
        @Volatile var isRunning = false
    }

    // Holds all active FileObservers so they don't get garbage collected
    private val activeObservers = mutableListOf<FileObserver>()
    private lateinit var prefs: AppPrefs
    private lateinit var notificationHelper: NotificationHelper

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PRINT -> {
                val path = intent.getStringExtra(EXTRA_PDF_PATH) ?: return START_STICKY
                PrintManager.print(this, path, prefs)
                notificationHelper.cancelReportNotification()
                OverlayManager.dismiss(this)
            }
            ACTION_DISMISS -> {
                notificationHelper.cancelReportNotification()
                OverlayManager.dismiss(this)
            }
            else -> {
                isRunning = true
                startForeground(
                    NotificationHelper.WATCHER_NOTIFICATION_ID,
                    notificationHelper.buildWatcherNotification()
                )
                startWatching()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopAllObservers()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Watcher tree ─────────────────────────────────────────────────────────

    private fun startWatching() {
        stopAllObservers()
        val rootPath = prefs.rootFolder
        val rootDir = File(rootPath)

        if (!rootDir.exists() || !rootDir.isDirectory) {
            Log.w(TAG, "Root folder does not exist: $rootPath")
            return
        }

        Log.i(TAG, "Starting watch on root: $rootPath")

        // Watch root for new client folders being created
        attachRootObserver(rootDir)

        // Also attach observers to any client folders that already exist
        rootDir.listFiles()?.filter { it.isDirectory }?.forEach { clientDir ->
            attachClientObserver(clientDir)

            // And any existing date/time folders under each client
            clientDir.listFiles()?.filter { it.isDirectory }?.forEach { dateDir ->
                attachDateObserver(dateDir)
            }
        }
    }

    /**
     * Watches the root report/ folder for new client directories being created.
     */
    private fun attachRootObserver(rootDir: File) {
        val observer = object : FileObserver(rootDir.absolutePath, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val newDir = File(rootDir, path)
                if (newDir.isDirectory) {
                    Log.d(TAG, "New client folder: $path")
                    attachClientObserver(newDir)
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)
        Log.d(TAG, "Root observer attached: ${rootDir.absolutePath}")
    }

    /**
     * Watches a client folder for new date/time scan directories.
     * Folder name = client name (e.g. "Sarah Johnson")
     */
    private fun attachClientObserver(clientDir: File) {
        val observer = object : FileObserver(clientDir.absolutePath, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val newDir = File(clientDir, path)
                if (newDir.isDirectory) {
                    Log.d(TAG, "New scan folder: $path under client ${clientDir.name}")
                    attachDateObserver(newDir)
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)
        Log.d(TAG, "Client observer attached: ${clientDir.absolutePath}")
    }

    /**
     * Watches a date/time folder (e.g. 2026_06_27_23_03_33) for the pdf/ subfolder,
     * then watches pdf/ for customer_report.pdf being fully written.
     */
    private fun attachDateObserver(dateDir: File) {
        val observer = object : FileObserver(dateDir.absolutePath, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (path == "pdf") {
                    val pdfDir = File(dateDir, "pdf")
                    Log.d(TAG, "pdf/ folder created under ${dateDir.absolutePath}")
                    attachPdfDirObserver(pdfDir, dateDir)
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)

        // Edge case: pdf/ folder may already exist if we're attaching late
        val pdfDir = File(dateDir, "pdf")
        if (pdfDir.exists()) {
            attachPdfDirObserver(pdfDir, dateDir)
        }

        Log.d(TAG, "Date observer attached: ${dateDir.absolutePath}")
    }

    /**
     * Watches the pdf/ folder for customer_report.pdf to be fully written (CLOSE_WRITE).
     * CLOSE_WRITE fires after the writing process closes the file — safe to print.
     */
    private fun attachPdfDirObserver(pdfDir: File, dateDir: File) {
        val targetFilename = prefs.targetFilename  // default: customer_report.pdf

        val observer = object : FileObserver(pdfDir.absolutePath, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                if (path == targetFilename) {
                    val pdfFile = File(pdfDir, path)
                    Log.i(TAG, "Report ready: ${pdfFile.absolutePath}")

                    // Parse client name and timestamp from path
                    // Structure: rootFolder/clientName/dateTime/pdf/customer_report.pdf
                    val clientName = dateDir.parentFile?.name ?: "Unknown Client"
                    val scanTimestamp = formatTimestamp(dateDir.name)

                    notificationHelper.showReportNotification(
                        pdfPath = pdfFile.absolutePath,
                        clientName = clientName,
                        scanTimestamp = scanTimestamp
                    )
                    // Overlay draws above all apps including full-screen kiosk apps
                    OverlayManager.show(
                        context = this@WatcherService,
                        pdfPath = pdfFile.absolutePath,
                        clientName = clientName,
                        scanTimestamp = scanTimestamp,
                        timeoutSeconds = prefs.notifTimeoutSeconds
                    )
                }
            }
        }
        observer.startWatching()
        activeObservers.add(observer)
        Log.d(TAG, "PDF observer attached: ${pdfDir.absolutePath}")
    }

    private fun stopAllObservers() {
        activeObservers.forEach { it.stopWatching() }
        activeObservers.clear()
        Log.i(TAG, "All observers stopped")
    }

    /**
     * Converts "2026_06_27_23_03_33" → "Jun 27, 2026  11:03 PM"
     */
    private fun formatTimestamp(raw: String): String {
        return try {
            val parts = raw.split("_")
            if (parts.size < 6) return raw
            val year = parts[0]
            val month = parts[1].toInt()
            val day = parts[2]
            val hour = parts[3].toInt()
            val min = parts[4]
            val monthNames = listOf(
                "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
            )
            val amPm = if (hour < 12) "AM" else "PM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            "${monthNames.getOrElse(month) { month.toString() }} $day, $year  $hour12:$min $amPm"
        } catch (e: Exception) {
            raw
        }
    }
}
