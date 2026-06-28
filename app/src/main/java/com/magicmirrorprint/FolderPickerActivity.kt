package com.magicmirrorprint

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

/**
 * Simple filesystem folder browser.
 * Returns the selected path via setResult / RESULT_OK with EXTRA_SELECTED_PATH.
 */
class FolderPickerActivity : Activity() {

    companion object {
        const val EXTRA_START_PATH    = "extra_start_path"
        const val EXTRA_SELECTED_PATH = "extra_selected_path"
    }

    private lateinit var listView: ListView
    private lateinit var pathTextView: TextView
    private lateinit var selectButton: Button
    private lateinit var currentDir: File
    private val entries = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build layout programmatically — no XML needed for this simple picker
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Current path label
        pathTextView = TextView(this).apply {
            textSize = 13f
            setPadding(8, 8, 8, 8)
        }
        root.addView(pathTextView)

        // Divider
        val divider = View(this).apply {
            setBackgroundColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        }
        root.addView(divider)

        // Folder list
        listView = ListView(this)
        root.addView(
            listView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        // Bottom button row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }

        selectButton = Button(this).apply {
            text = "Select This Folder"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { returnSelectedFolder() }
        }

        buttonRow.addView(cancelButton)
        buttonRow.addView(selectButton)
        root.addView(buttonRow)

        setContentView(root)
        setTitle("Select Report Folder")

        // Determine start path
        val startPath = intent.getStringExtra(EXTRA_START_PATH)
        currentDir = when {
            !startPath.isNullOrBlank() && File(startPath).exists() -> File(startPath)
            else -> Environment.getExternalStorageDirectory()
        }

        navigateTo(currentDir)

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = entries[position]
            if (selected.name == "..") {
                currentDir.parentFile?.let { navigateTo(it) }
            } else if (selected.isDirectory) {
                navigateTo(selected)
            }
        }
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        pathTextView.text = dir.absolutePath

        entries.clear()

        // Add parent entry if not at root
        if (dir.parentFile != null) {
            entries.add(File(dir, "..").apply { })
            // We'll fake the ".." entry by using the parent directly
        }

        val children = dir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        // Re-do: use a clean approach
        entries.clear()
        if (dir.parentFile != null) entries.add(dir.parentFile!!)
        entries.addAll(children)

        val displayNames = entries.mapIndexed { index, file ->
            if (index == 0 && dir.parentFile != null && file == dir.parentFile)
                "⬆  .."
            else
                "📁  ${file.name}"
        }

        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
    }

    private fun returnSelectedFolder() {
        val result = Intent().apply {
            putExtra(EXTRA_SELECTED_PATH, currentDir.absolutePath)
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
