package com.magicmirrorprint

import android.content.Context
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sends a PDF to a network printer via IPP (Internet Printing Protocol) over port 631.
 * Constructs a minimal IPP/1.1 Print-Job request.
 *
 * Runs on a background thread — do NOT call from the main thread.
 */
object PrintManager {

    private const val TAG = "MagicMirrorPrint/Print"

    // IPP operation and status codes
    private const val IPP_VERSION_MAJOR: Byte = 1
    private const val IPP_VERSION_MINOR: Byte = 1
    private const val OPERATION_PRINT_JOB: Short = 0x0002

    // IPP attribute tag bytes
    private const val TAG_OPERATION_ATTRIBUTES: Byte = 0x01
    private const val TAG_JOB_ATTRIBUTES: Byte = 0x02
    private const val TAG_END_ATTRIBUTES: Byte = 0x03
    private const val TAG_CHARSET: Byte = 0x47.toByte()
    private const val TAG_NATURAL_LANGUAGE: Byte = 0x48.toByte()
    private const val TAG_URI: Byte = 0x45.toByte()
    private const val TAG_MIME_MEDIA_TYPE: Byte = 0x49.toByte()
    private const val TAG_NAME_WITHOUT_LANGUAGE: Byte = 0x42.toByte()

    fun print(context: Context, pdfPath: String, prefs: AppPrefs) {
        Thread {
            try {
                val ip   = prefs.printerIp
                val port = prefs.printerPort
                val file = File(pdfPath)

                if (!file.exists()) {
                    Log.e(TAG, "PDF file not found: $pdfPath")
                    return@Thread
                }

                Log.i(TAG, "Connecting to printer $ip:$port")
                val socket = Socket(ip, port)
                val out = DataOutputStream(socket.getOutputStream())

                val ippHeader = buildIppHeader(ip, port, file.name)
                val pdfBytes  = FileInputStream(file).use { it.readBytes() }

                // IPP message = IPP header bytes + raw PDF bytes
                out.write(ippHeader)
                out.write(pdfBytes)
                out.flush()

                // Read response (we just drain it — a real app might parse status)
                val response = socket.getInputStream().readBytes()
                Log.i(TAG, "Printer response: ${response.size} bytes")

                out.close()
                socket.close()

                Log.i(TAG, "Print job sent successfully: ${file.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Print failed: ${e.message}", e)
            }
        }.start()
    }

    /**
     * Builds a minimal IPP/1.1 Print-Job request header.
     */
    private fun buildIppHeader(ip: String, port: Int, jobName: String): ByteArray {
        val buf = ByteBuffer.allocate(2048)
        buf.order(ByteOrder.BIG_ENDIAN)

        // IPP version 1.1
        buf.put(IPP_VERSION_MAJOR)
        buf.put(IPP_VERSION_MINOR)

        // Operation: Print-Job
        buf.putShort(OPERATION_PRINT_JOB)

        // Request ID (arbitrary, 1 is fine)
        buf.putInt(1)

        // --- operation-attributes-tag ---
        buf.put(TAG_OPERATION_ATTRIBUTES)

        // attributes-charset = utf-8
        putIppAttribute(buf, TAG_CHARSET, "attributes-charset", "utf-8")

        // attributes-natural-language = en
        putIppAttribute(buf, TAG_NATURAL_LANGUAGE, "attributes-natural-language", "en")

        // printer-uri
        val printerUri = "ipp://$ip:$port/ipp/print"
        putIppAttribute(buf, TAG_URI, "printer-uri", printerUri)

        // requesting-user-name
        putIppAttribute(buf, TAG_NAME_WITHOUT_LANGUAGE, "requesting-user-name", "MagicMirrorPrint")

        // job-name
        putIppAttribute(buf, TAG_NAME_WITHOUT_LANGUAGE, "job-name", jobName)

        // document-format = application/pdf
        putIppAttribute(buf, TAG_MIME_MEDIA_TYPE, "document-format", "application/pdf")

        // --- job-attributes-tag (empty, just mark it) ---
        buf.put(TAG_JOB_ATTRIBUTES)

        // --- end-of-attributes-tag ---
        buf.put(TAG_END_ATTRIBUTES)

        val result = ByteArray(buf.position())
        buf.rewind()
        buf.get(result)
        return result
    }

    /**
     * Writes a single IPP attribute: tag + name length + name + value length + value
     */
    private fun putIppAttribute(buf: ByteBuffer, tag: Byte, name: String, value: String) {
        val nameBytes  = name.toByteArray(Charsets.UTF_8)
        val valueBytes = value.toByteArray(Charsets.UTF_8)

        buf.put(tag)
        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)
        buf.putShort(valueBytes.size.toShort())
        buf.put(valueBytes)
    }
}
