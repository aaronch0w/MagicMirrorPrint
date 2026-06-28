package com.magicmirrorprint

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {

    companion object {
        private const val REQ_FOLDER_PICKER  = 100
        private const val REQ_STORAGE_PERM   = 101
    }

    private lateinit var prefs: AppPrefs

    // UI references
    private lateinit var rootFolderText: TextView
    private lateinit var targetFilenameEdit: EditText
    private lateinit var printerIpEdit: EditText
    private lateinit var printerPortEdit: EditText
    private lateinit var autoPrintSwitch: Switch
    private lateinit var notifTimeoutEdit: EditText
    private lateinit var statusText: TextView
    private lateinit var toggleServiceButton: Button

    private var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)

        // ── Root layout ──────────────────────────────────────────────────────
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scroll.addView(root)
        setContentView(scroll)
        setTitle("Magic Mirror Print — Settings")

        // ── Service status banner ────────────────────────────────────────────
        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE8F5E9.toInt())
        }
        root.addView(statusText)
        addSpacing(root, 16)

        // ── Root folder row ──────────────────────────────────────────────────
        addSectionHeader(root, "Report Root Folder")

        val folderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        rootFolderText = TextView(this).apply {
            text = if (prefs.rootFolder.isBlank()) "Not set — tap Browse" else prefs.rootFolder
            textSize = 13f
            setPadding(8, 12, 8, 12)
            setBackgroundColor(0xFFF5F5F5.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val browseButton = Button(this).apply {
            text = "Browse"
            setOnClickListener { launchFolderPicker() }
        }

        folderRow.addView(rootFolderText)
        folderRow.addView(browseButton)
        root.addView(folderRow)
        addSpacing(root, 16)

        // ── Target filename ──────────────────────────────────────────────────
        addSectionHeader(root, "Target Filename")
        targetFilenameEdit = EditText(this).apply {
            setText(prefs.targetFilename)
            hint = AppPrefs.DEFAULT_TARGET_FILENAME
        }
        root.addView(targetFilenameEdit)
        addSpacing(root, 16)

        // ── Printer IP ───────────────────────────────────────────────────────
        addSectionHeader(root, "Printer IP Address")
        printerIpEdit = EditText(this).apply {
            setText(prefs.printerIp)
            hint = "e.g. 192.168.1.45"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(printerIpEdit)
        addSpacing(root, 8)

        // ── Printer Port ─────────────────────────────────────────────────────
        addSectionHeader(root, "Printer Port (default 631)")
        printerPortEdit = EditText(this).apply {
            setText(prefs.printerPort.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(printerPortEdit)
        addSpacing(root, 16)

        // ── Notification timeout ─────────────────────────────────────────────
        addSectionHeader(root, "Notification Timeout (seconds)")
        notifTimeoutEdit = EditText(this).apply {
            setText(prefs.notifTimeoutSeconds.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(notifTimeoutEdit)
        addSpacing(root, 16)

        // ── Auto-print toggle ────────────────────────────────────────────────
        val autoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val autoLabel = TextView(this).apply {
            text = "Auto-print without prompt"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        autoPrintSwitch = Switch(this).apply {
            isChecked = prefs.autoPrint
        }
        autoRow.addView(autoLabel)
        autoRow.addView(autoPrintSwitch)
        root.addView(autoRow)
        addSpacing(root, 8)

        val autoHint = TextView(this).apply {
            text = "When ON, reports print immediately without showing the notification."
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        }
        root.addView(autoHint)
        addSpacing(root, 24)

        // ── Save button ──────────────────────────────────────────────────────
        val saveButton = Button(this).apply {
            text = "Save Settings"
            setOnClickListener { saveSettings() }
        }
        root.addView(saveButton)
        addSpacing(root, 12)

        // ── Service toggle ───────────────────────────────────────────────────
        toggleServiceButton = Button(this).apply {
            setOnClickListener { toggleService() }
        }
        root.addView(toggleServiceButton)
        addSpacing(root, 24)

        // ── Test print button ────────────────────────────────────────────────
        addSectionHeader(root, "Troubleshooting")
        val testPrintButton = Button(this).apply {
            text = "Send Test Page"
            setOnClickListener { sendTestPage() }
        }
        root.addView(testPrintButton)

        updateStatusUI()
        checkStoragePermission()
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private fun launchFolderPicker() {
        val intent = Intent(this, FolderPickerActivity::class.java).apply {
            val current = prefs.rootFolder
            if (current.isNotBlank()) putExtra(FolderPickerActivity.EXTRA_START_PATH, current)
        }
        startActivityForResult(intent, REQ_FOLDER_PICKER)
    }

    private fun saveSettings() {
        val filename = targetFilenameEdit.text.toString().trim()
        val ip       = printerIpEdit.text.toString().trim()
        val portStr  = printerPortEdit.text.toString().trim()
        val timeout  = notifTimeoutEdit.text.toString().trim()

        if (ip.isBlank()) {
            toast("Please enter a printer IP address")
            return
        }
        if (prefs.rootFolder.isBlank()) {
            toast("Please select the report root folder")
            return
        }

        prefs.targetFilename      = filename.ifBlank { AppPrefs.DEFAULT_TARGET_FILENAME }
        prefs.printerIp           = ip
        prefs.printerPort         = portStr.toIntOrNull() ?: AppPrefs.DEFAULT_PRINTER_PORT
        prefs.notifTimeoutSeconds = timeout.toIntOrNull() ?: AppPrefs.DEFAULT_NOTIF_TIMEOUT
        prefs.autoPrint           = autoPrintSwitch.isChecked

        toast("Settings saved")
        updateStatusUI()
    }

    private fun toggleService() {
        if (serviceRunning) {
            stopService(Intent(this, WatcherService::class.java))
            serviceRunning = false
        } else {
            if (!prefs.isConfigured()) {
                toast("Save your settings first (printer IP + root folder required)")
                return
            }
            startService(Intent(this, WatcherService::class.java))
            serviceRunning = true
        }
        updateStatusUI()
    }

    private fun sendTestPage() {
        if (prefs.printerIp.isBlank()) {
            toast("Set a printer IP first")
            return
        }
        toast("Sending test job to ${prefs.printerIp}…")
        // For test: creates a tiny temp PDF-like file and sends it
        val testFile = java.io.File(cacheDir, "test_print.pdf")
        testFile.writeText("%PDF-1.4 test page from Magic Mirror Print")
        PrintManager.print(this, testFile.absolutePath, prefs)
    }

    private fun updateStatusUI() {
        if (serviceRunning) {
            statusText.text = "● Watcher is RUNNING"
            statusText.setBackgroundColor(0xFFE8F5E9.toInt())
            statusText.setTextColor(0xFF2E7D32.toInt())
            toggleServiceButton.text = "Stop Watcher Service"
        } else {
            statusText.text = "○ Watcher is STOPPED"
            statusText.setBackgroundColor(0xFFFFF3E0.toInt())
            statusText.setTextColor(0xFFE65100.toInt())
            toggleServiceButton.text = "Start Watcher Service"
        }
    }

    // ─── Results ──────────────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FOLDER_PICKER && resultCode == RESULT_OK) {
            val path = data?.getStringExtra(FolderPickerActivity.EXTRA_SELECTED_PATH) ?: return
            prefs.rootFolder = path
            rootFolderText.text = path
            toast("Root folder set")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == REQ_STORAGE_PERM) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                toast("Storage permission is required to watch for reports")
            }
        }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun checkStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQ_STORAGE_PERM
            )
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun addSectionHeader(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF555555.toInt())
            setPadding(0, 0, 0, 4)
        })
    }

    private fun addSpacing(parent: LinearLayout, dp: Int) {
        parent.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (dp * resources.displayMetrics.density).toInt()
            )
        })
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
