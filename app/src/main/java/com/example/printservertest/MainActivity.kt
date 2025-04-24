// MainActivity.kt
package com.example.printservertest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var printStatusText: TextView
    private lateinit var startServerButton: Button
    private lateinit var stopServerButton: Button
    private var printServer: PrintServer? = null
    private lateinit var printerManager: ThermalPrinterManager
    private val PORT = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        ipAddressText = findViewById(R.id.ipAddressText)
        serverStatusText = findViewById(R.id.serverStatusText)
        printStatusText = findViewById(R.id.printStatusText)
        startServerButton = findViewById(R.id.startServerButton)
        stopServerButton = findViewById(R.id.stopServerButton)

        // Initialize printer manager
        printerManager = ThermalPrinterManager(this)

        // Connect to printer
        if (printerManager.connect()) {
            printStatusText.text = "Printer connected"
        } else {
            printStatusText.text = "Failed to connect to printer"
        }

        // Display the device IP
        val ipAddress = getLocalIpAddress()
        ipAddressText.text = "Device IP: $ipAddress"

        // Start server button
        startServerButton.setOnClickListener {
            if (printServer == null || !printServer!!.isAlive) {
                try {
                    printServer = PrintServer(this, PORT)
                    printServer!!.start()
                    serverStatusText.text = "Server running on port $PORT"
                    updateButtonState(true)
                } catch (e: IOException) {
                    serverStatusText.text = "Error starting server: ${e.message}"
                    e.printStackTrace()
                }
            }
        }

        // Stop server button
        stopServerButton.setOnClickListener {
            if (printServer != null && printServer!!.isAlive) {
                printServer!!.stop()
                printServer = null
                serverStatusText.text = "Server stopped"
                updateButtonState(false)
            }
        }

        // Initially disable the stop button
        updateButtonState(false)
    }

    private fun updateButtonState(serverRunning: Boolean) {
        startServerButton.isEnabled = !serverRunning
        stopServerButton.isEnabled = serverRunning
    }

    override fun onDestroy() {
        if (printServer != null && printServer!!.isAlive) {
            printServer!!.stop()
        }
        if (::printerManager.isInitialized) {
            printerManager.disconnect()
        }
        super.onDestroy()
    }

    // Get the device's local IP address
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr.hostAddress.contains(":")) continue // Skip IPv6
                    return addr.hostAddress
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }

    // HTTP Server class that handles print requests
    private inner class PrintServer(private val context: MainActivity, port: Int) : NanoHTTPD(port) {
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun serve(session: IHTTPSession): Response {
            if (session.uri == "/print" && session.method == Method.POST) {
                // Handle print request
                try {
                    val contentLength = Integer.parseInt(session.headers["content-length"] ?: "0")
                    val buffer = ByteArray(contentLength)
                    session.inputStream.read(buffer, 0, contentLength)
                    val textToPrint = String(buffer)

                    val success = printText(textToPrint)

                    if (success) {
                        mainHandler.post {
                            Toast.makeText(context, "Printing: $textToPrint", Toast.LENGTH_SHORT).show()
                            printStatusText.text = "Last print: ${textToPrint.substring(0, Math.min(20, textToPrint.length))}..."
                        }
                        return newFixedLengthResponse("Print job sent successfully")
                    } else {
                        mainHandler.post {
                            printStatusText.text = "Print failed!"
                        }
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to print")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    mainHandler.post {
                        printStatusText.text = "Print error: ${e.message}"
                    }
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
                }
            } else if (session.uri == "/") {
                // Serve a simple HTML page for testing
                return newFixedLengthResponse(getTestHtml())
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        private fun getTestHtml(): String {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Thermal Printer Test</title>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <style>
                        body { font-family: Arial; margin: 20px; }
                        button { padding: 10px; margin: 5px; }
                        textarea { width: 100%; height: 100px; }
                    </style>
                </head>
                <body>
                    <h1>Printer Test Interface</h1>
                    <textarea id='printText'>Hello World! This is a test print.</textarea><br>
                    <button onclick='sendPrint()'>Print Text</button>
                    <div id='status'></div>
                    <hr>
                    <h2>Receipt Test</h2>
                    <button onclick='sendReceipt()'>Print Sample Receipt</button>
                    <script>
                        function sendPrint() {
                            const text = document.getElementById('printText').value;
                            document.getElementById('status').innerText = 'Sending...';
                            fetch('/print', {
                                method: 'POST',
                                body: text
                            })
                            .then(response => response.text())
                            .then(result => {
                                document.getElementById('status').innerText = result;
                            })
                            .catch(error => {
                                document.getElementById('status').innerText = 'Error: ' + error;
                            });
                        }
                        
                        function sendReceipt() {
                            const receipt = "TRAIN TICKET RECEIPT\\n\\n" +
                            "Date: " + new Date().toLocaleString() + "\\n" +
                            "Transaction #: " + Math.floor(Math.random() * 10000) + "\\n\\n" +
                            "ITEM                     PRICE\\n" +
                            "--------------------------------\\n" +
                            "Adult Ticket            ${'$'}12.50\\n" +
                            "Child Ticket            ${'$'}6.75\\n" +
                            "Senior Discount         -${'$'}2.50\\n" +
                            "--------------------------------\\n" +
                            "TOTAL                   ${'$'}16.75\\n\\n" +
                            "Payment Method: Cash\\n" +
                            "Change: ${'$'}3.25\\n\\n" +
                            "Thank you for your purchase!\\n" +
                            "Visit us again soon.";
                            
                            document.getElementById('status').innerText = 'Sending receipt...';
                            fetch('/print', {
                                method: 'POST',
                                body: receipt
                            })
                            .then(response => response.text())
                            .then(result => {
                                document.getElementById('status').innerText = result;
                            })
                            .catch(error => {
                                document.getElementById('status').innerText = 'Error: ' + error;
                            });
                        } 
                    </script>
                </body>
                </html>
            """.trimIndent()
        }

        private fun printText(text: String): Boolean {
            try {
                // Use our ThermalPrinterManager to handle the printing
                if (!printerManager.isConnected()) {
                    mainHandler.post {
                        Toast.makeText(context, "Printer not connected. Attempting to reconnect...", Toast.LENGTH_SHORT).show()
                    }

                    // Try to reconnect
                    if (!printerManager.connect()) {
                        mainHandler.post {
                            Toast.makeText(context, "Failed to connect to printer", Toast.LENGTH_SHORT).show()
                        }
                        return false
                    }
                }

                // Log the print request
                android.util.Log.d("PrintServer", "Print request: $text")

                // Send to printer
                return printerManager.printText(text)
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }
}