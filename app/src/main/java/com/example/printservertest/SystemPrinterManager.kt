package com.example.printservertest

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Printer manager that uses Android's system printing services
 */
class SystemPrinterManager(private val context: Context) {
    companion object {
        private const val TAG = "SystemPrinterManager"
    }

    private var webView: WebView? = null
    private var isReady = false
    private var printJob: android.print.PrintJob? = null

    init {
        init {
            try {
                // Check if printing service is available
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    Log.e(TAG, "Print service not available on this device")
                    isReady = false
                    return
                }

                // Create WebView for HTML rendering
                webView = WebView(context)
                webView?.settings?.javaScriptEnabled = true

                // Enable zoom controls
                webView?.settings?.loadWithOverviewMode = true
                webView?.settings?.useWideViewPort = true

                isReady = true
                Log.d(TAG, "System printer manager initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing System Printer Manager: ${e.message}")
                isReady = false
            }
        }

        /**
         * Check if printing service is available
         */
        fun isAvailable(): Boolean {
            return isReady
        }

        /**
         * Print text content
         */
        fun printText(text: String): Boolean {
            if (!isReady) {
                Log.e(TAG, "Printer system not ready")
                return false
            }

            try {
                // Format the text as HTML for better printing
                val htmlContent = formatTextAsHtml(text)

                // Use WebView to render the HTML content
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // Once the WebView has rendered the content, start printing
                        startPrint("Text Document")
                    }
                }

                // Load the HTML content into WebView
                webView?.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error printing text: ${e.message}")
                e.printStackTrace()
                return false
            }
        }

        /**
         * Print a receipt with standard formatting
         */
        fun printReceipt(
            title: String,
            items: Array<String>,
            prices: DoubleArray,
            total: Double
        ): Boolean {
            if (!isReady) {
                Log.e(TAG, "Printer system not ready")
                return false
            }

            val receiptBuilder = StringBuilder()

            // Add receipt header
            receiptBuilder.append("<h3>$title</h3>")

            // Add current date/time
            receiptBuilder.append("<p>Date: ${java.util.Date()}</p>")

            // Add items
            receiptBuilder.append("<table width='100%'>")
            for (i in items.indices) {
                receiptBuilder.append("<tr>")
                    .append("<td>${items[i]}</td>")
                    .append("<td align='right'>$${String.format("%.2f", prices[i])}</td>")
                    .append("</tr>")
            }

            // Add separator
            receiptBuilder.append("<tr><td colspan='2'><hr></td></tr>")

            // Add total
            receiptBuilder.append("<tr>")
                .append("<td><strong>TOTAL</strong></td>")
                .append("<td align='right'><strong>$${String.format("%.2f", total)}</strong></td>")
                .append("</tr>")
            receiptBuilder.append("</table>")

            // Add footer
            receiptBuilder.append("<p>Thank you for your purchase!</p>")

            val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Receipt</title>
                <style>
                    body { font-family: monospace; font-size: 10pt; }
                    table { width: 100%; }
                </style>
            </head>
            <body>
                ${receiptBuilder.toString()}
            </body>
            </html>
        """.trimIndent()

            // Print the receipt using WebView
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    startPrint("Receipt")
                }
            }

            webView?.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            return true
        }

        /**
         * Format plain text as HTML, handling ESC/POS commands when possible
         */
        private fun formatTextAsHtml(text: String): String {
            var formattedText = text

            // Handle common ESC/POS commands or just strip them out
            // This is a simplified version that just removes the commands

            // Remove ESC @ (initialize printer)
            formattedText = formattedText.replace(Regex("\\x1B\\x40"), "")

            // Handle center alignment ESC a 1
            formattedText =
                formattedText.replace(Regex("\\x1B\\x61\\x01"), "<div style='text-align:center'>")
                    .replace(Regex("\\x1B\\x61\\x00"), "</div><div style='text-align:left'>")

            // Handle bold text
            formattedText = formattedText.replace(Regex("\\x1B\\x45\\x01"), "<strong>")
                .replace(Regex("\\x1B\\x45\\x00"), "</strong>")

            // Handle large text
            formattedText =
                formattedText.replace(Regex("\\x1D\\x21\\x11"), "<span style='font-size:16pt'>")
                    .replace(Regex("\\x1D\\x21\\x00"), "</span>")

            // Remove paper cut command
            formattedText = formattedText.replace(Regex("\\x1D\\x56\\x41.*"), "")

            // Remove other non-printable characters
            formattedText = formattedText.replace(Regex("[\\x00-\\x1F\\x7F]"), "")

            // Handle newlines
            formattedText = formattedText.replace("\n", "<br>")

            // Close any open div tags
            if (formattedText.contains("<div") && !formattedText.contains("</div>")) {
                formattedText += "</div>"
            }

            return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: 'Courier New', monospace; 
                        font-size: 10pt;
                        padding: 0;
                        margin: 0;
                        width: 58mm; /* Standard thermal receipt width */
                    }
                    pre {
                        white-space: pre-wrap;
                        margin: 0;
                        font-family: 'Courier New', monospace;
                    }
                </style>
            </head>
            <body>
                <pre>$formattedText</pre>
            </body>
            </html>
        """.trimIndent()
        }

        /**
         * Start the print job
         */
        /**
         * Start the print job with the system print dialog
         */
        private fun startPrint(jobName: String) {
            try {
                // Get the print manager
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

                // Create print document adapter from WebView
                val printAdapter = webView?.createPrintDocumentAdapter(jobName)
                    ?: throw IllegalStateException("WebView not initialized")

                // Set print attributes for thermal printer (typically narrow format)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A7) // Small paper size for receipts
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS) // No margins for receipts
                    .setResolution(
                        PrintAttributes.Resolution(
                            "default",
                            "Default",
                            203,
                            203
                        )
                    ) // Typical thermal printer DPI
                    .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME) // Black and white for thermal printers
                    .build()

                // Create the print job
                printJob = printManager.print(jobName, printAdapter, attributes)

                Log.d(TAG, "Print job started: ${printJob?.info?.label}")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting print job: ${e.message}")
                e.printStackTrace()

                // Show a toast message on the main thread
                try {
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post {
                        android.widget.Toast.makeText(
                            context,
                            "Print error: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error showing toast: ${e2.message}")
                }
            }
        }

        /**
         * Check if print job is complete
         */
        fun isPrintJobComplete(): Boolean {
            return printJob?.isCompleted ?: true
        }

        /**
         * Get print job state as string
         */
        fun getPrintJobState(): String {
            return when {
                printJob == null -> "No print job"
                printJob?.isCompleted == true -> "Completed"
                printJob?.isStarted == true -> "Started"
                printJob?.isCancelled == true -> "Cancelled"
                printJob?.isFailed == true -> "Failed"
                printJob?.isQueued == true -> "Queued"
                printJob?.isBlocked == true -> "Blocked"
                else -> "Unknown"
            }
        }

        /**
         * Release resources
         */
        fun cleanup() {
            webView?.destroy()
            webView = null
            isReady = false
        }
    }
}