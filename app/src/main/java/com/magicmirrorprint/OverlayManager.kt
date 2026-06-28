package com.magicmirrorprint

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object OverlayManager {

    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        pdfPath: String,
        clientName: String,
        scanTimestamp: String,
        timeoutSeconds: Int
    ) {
        if (!canShow(context)) return
        handler.post {
            dismiss(context)
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#E61565C0"))
                setPadding(40, 28, 40, 28)
            }

            container.addView(TextView(context).apply {
                text = "New Report Ready"
                textSize = 17f
                setTextColor(Color.WHITE)
            })
            container.addView(TextView(context).apply {
                text = "$clientName  •  $scanTimestamp"
                textSize = 13f
                setTextColor(Color.parseColor("#BBDEFB"))
                setPadding(0, 4, 0, 16)
            })

            val buttonRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val printBtn = Button(context).apply {
                text = "Print"
                setOnClickListener {
                    context.startService(Intent(context, WatcherService::class.java).apply {
                        action = WatcherService.ACTION_PRINT
                        putExtra(WatcherService.EXTRA_PDF_PATH, pdfPath)
                        putExtra(WatcherService.EXTRA_CLIENT_NAME, clientName)
                        putExtra(WatcherService.EXTRA_SCAN_TIMESTAMP, scanTimestamp)
                    })
                    dismiss(context)
                }
            }
            val dismissBtn = Button(context).apply {
                text = "Dismiss"
                setOnClickListener { dismiss(context) }
            }
            buttonRow.addView(printBtn)
            buttonRow.addView(dismissBtn)
            container.addView(buttonRow)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP }

            overlayView = container
            wm.addView(container, params)

            handler.postDelayed({ dismiss(context) }, timeoutSeconds * 1000L)
        }
    }

    fun dismiss(context: Context) {
        handler.post {
            overlayView?.let {
                try {
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
                } catch (_: Exception) {}
                overlayView = null
            }
            handler.removeCallbacksAndMessages(null)
        }
    }
}
