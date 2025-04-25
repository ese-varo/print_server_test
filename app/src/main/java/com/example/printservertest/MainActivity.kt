// MainActivity.kt
package com.example.printservertest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val PORT = 8080
    }

    private lateinit var ipAddressText: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var printStatusText: TextView
    private lateinit var startServerButton: Button
    private lateinit var stopServerButton: Button
    private lateinit var thermalPrinterRadio: RadioButton
    private lateinit var systemPrinterRadio: RadioButton
    private lateinit var manufacturerPrinterRadio: RadioButton
    private var printServer: PrintServer? = null
    private lateinit var thermalPrinterManager: ThermalPrinterManager
    private lateinit var systemPrinterManager: SystemPrinterManager
    private lateinit var manufacturerPrinterManager: ManufacturerPrinterManager
    private var selectedPrinter = "manufacturer" // Default to manufacturer's API

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        ipAddressText = findViewById(R.id.ipAddressText)
        serverStatusText = findViewById(R.id.serverStatusText)
        printStatusText = findViewById(R.id.printStatusText)
        startServerButton = findViewById(R.id.startServerButton)
        stopServerButton = findViewById(R.id.stopServerButton)
        thermalPrinterRadio = findViewById(R.id.thermalPrinterRadio)
        systemPrinterRadio = findViewById(R.id.systemPrinterRadio)
        manufacturerPrinterRadio = findViewById(R.id.manufacturerPrinterRadio)

        // Initialize all printer managers
        try {
            thermalPrinterManager = ThermalPrinterManager(this)
            systemPrinterManager = SystemPrinterManager(this)
            manufacturerPrinterManager = ManufacturerPrinterManager(this)

            Log.d(TAG, "Printer managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing printer managers: ${e.message}")
            Toast.makeText(this, "Error initializing printer services", Toast.LENGTH_LONG).show()
        }

        // Setup radio buttons for printer selection
        val printerTypeRadioGroup = findViewById<RadioGroup>(R.id.printerTypeRadioGroup)
        printerTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPrinter = when (checkedId) {
                R.id.thermalPrinterRadio -> "thermal"
                R.id.systemPrinterRadio -> "system"
                else -> "manufacturer"
            }
            Log.d(TAG, "Selected printer changed to: $selectedPrinter")

            updatePrinterStatus()
        }

        // Set initial printer status based on default selection
        updatePrinterStatus()

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

        // Add test print button functionality
        val testPrintButton = findViewById<Button>(R.id.testPrintButton)
        testPrintButton.setOnClickListener {
            // Create sample train ticket
            val sampleTicket = """
                TRAIN TICKET
                ---------------------
                
                Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())}
                Ticket #: ${(1000..9999).random()}
                
                From: Central Station
                To:   North Terminal
                
                Departure: 14:30
                Platform:  3
                
                Adult Fare:     $12.50
                Service Fee:     $1.25
                ---------------------
                TOTAL:          $13.75
                
                Payment: CASH
                
                Thank you for traveling with us!
                Please retain this ticket for inspection.
            """.trimIndent()

            // Try to print with selected printer
            var success = false
            when (selectedPrinter) {
                "thermal" -> {
                    success = if (thermalPrinterManager.isConnected() || thermalPrinterManager.connect()) {
                        thermalPrinterManager.printText(sampleTicket)
                    } else false
                }
                "system" -> {
                    success = systemPrinterManager.isAvailable() && systemPrinterManager.printText(sampleTicket)
                }
                "manufacturer" -> {
                    success = manufacturerPrinterManager.isConnected() && manufacturerPrinterManager.printText(sampleTicket)
                }
            }

            if (success) {
                Toast.makeText(this, "Test ticket sent to $selectedPrinter printer", Toast.LENGTH_SHORT).show()
                printStatusText.text = "Last print: Test ticket ($selectedPrinter)"
            } else {
                // Try all other methods as fallback
                Toast.makeText(this, "Failed with $selectedPrinter printer, trying others...", Toast.LENGTH_SHORT).show()

                // Try each remaining method
                if (selectedPrinter != "manufacturer" && manufacturerPrinterManager.isConnected() &&
                    manufacturerPrinterManager.printText(sampleTicket)) {
                    printStatusText.text = "Last print: Test ticket (manufacturer fallback)"
                    return@setOnClickListener
                }

                if (selectedPrinter != "system" && systemPrinterManager.isAvailable() &&
                    systemPrinterManager.printText(sampleTicket)) {
                    printStatusText.text = "Last print: Test ticket (system fallback)"
                    return@setOnClickListener
                }

                if (selectedPrinter != "thermal" &&
                    (thermalPrinterManager.isConnected() || thermalPrinterManager.connect()) &&
                    thermalPrinterManager.printText(sampleTicket)) {
                    printStatusText.text = "Last print: Test ticket (thermal fallback)"
                    return@setOnClickListener
                }

                // All methods failed
                printStatusText.text = "Print failed with all methods!"
            }
        }

        // Initially disable the stop button
        updateButtonState(false)
    }

    /**
     * Update printer status based on the selected printer type
     */
    private fun updatePrinterStatus() {
        when (selectedPrinter) {
            "thermal" -> {
                // Test thermal printer connection
                if (thermalPrinterManager.connect()) {
                    printStatusText.text = "Thermal printer connected via USB"
                } else {
                    printStatusText.text = "Failed to connect to thermal printer via USB"
                    // Don't auto-switch, just show the status
                }
            }
            "system" -> {
                // System printer selected
                if (systemPrinterManager.isAvailable()) {
                    printStatusText.text = "System printer service ready"
                } else {
                    printStatusText.text = "System printing service not available"
                }
            }
            "manufacturer" -> {
                // Manufacturer printer selected
                if (manufacturerPrinterManager.isConnected()) {
                    printStatusText.text = "Integrated printer ready"
                } else {
                    printStatusText.text = "Integrated printer not detected"
                }
            }
        }
    }

    private fun updateButtonState(serverRunning: Boolean) {
        startServerButton.isEnabled = !serverRunning
        stopServerButton.isEnabled = serverRunning
    }

    override fun onDestroy() {
        try {
            if (printServer != null && printServer!!.isAlive) {
                printServer!!.stop()
            }
            if (::thermalPrinterManager.isInitialized) {
                thermalPrinterManager.disconnect()
            }
            if (::systemPrinterManager.isInitialized) {
                systemPrinterManager.cleanup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        } finally {
            super.onDestroy()
        }
    }

    // Get the device's local IP address
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val addresses = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val ifAddresses = iface.inetAddresses
                while (ifAddresses.hasMoreElements()) {
                    val addr = ifAddresses.nextElement()
                    if (addr.hostAddress.contains(":")) continue // Skip IPv6

                    // Prefer non-local addresses (not 192.168.x.x or 10.x.x.x)
                    val ip = addr.hostAddress
                    addresses.add(ip)

                    // WLAN interfaces are usually more relevant for our use case
                    if (iface.name.startsWith("wlan")) {
                        Log.d(TAG, "Found WLAN IP: $ip on ${iface.displayName}")
                        return ip
                    }
                }
            }

            // If we found at least one address, return the first one
            if (addresses.isNotEmpty()) {
                return addresses[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address: ${e.message}")
            e.printStackTrace()
        }
        return "Unknown"
    }

    // HTTP Server class that handles print requests
    private inner class PrintServer(private val context: MainActivity, port: Int) : NanoHTTPD(port) {
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun serve(session: IHTTPSession): Response {
            Log.d(TAG, "Received request: ${session.uri} (${session.method})")

            if (session.uri == "/print" && session.method == Method.POST) {
                // Handle print request
                try {
                    val contentLength = Integer.parseInt(session.headers["content-length"] ?: "0")
                    val buffer = ByteArray(contentLength)
                    session.inputStream.read(buffer, 0, contentLength)
                    val textToPrint = String(buffer)

                    Log.d(TAG, "Received print request with content length: $contentLength")

                    val success = printText(textToPrint)

                    if (success) {
                        mainHandler.post {
                            Toast.makeText(context, "Printing text...", Toast.LENGTH_SHORT).show()
                            printStatusText.text = "Last print: ${
                                if (textToPrint.length > 20)
                                    textToPrint.substring(0, 20) + "..."
                                else
                                    textToPrint
                            }"
                        }
                        return newFixedLengthResponse("Print job sent successfully")
                    } else {
                        mainHandler.post {
                            printStatusText.text = "Print failed!"
                        }
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to print")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error processing print request: ${e.message}")
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
                        body { font-family: Arial; margin: 20px; max-width: 800px; margin: 0 auto; padding: 20px; }
                        button { padding: 10px; margin: 5px; background-color: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; }
                        button:hover { background-color: #45a049; }
                        textarea { width: 100%; height: 100px; padding: 8px; margin-bottom: 10px; border-radius: 4px; border: 1px solid #ccc; }
                        h1, h2 { color: #333; }
                        .status { margin-top: 10px; padding: 10px; border-radius: 4px; }
                        hr { margin: 20px 0; border: 0; border-top: 1px solid #eee; }
                    </style>
                </head>
                <body>
                    <h1>Printer Test Interface</h1>
                    <textarea id='printText'>Hello World! This is a test print.</textarea><br>
                    <button onclick='sendPrint()'>Print Text</button>
                    <div id='status' class='status'></div>
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
                                document.getElementById('status').style.backgroundColor = '#d4edda';
                            })
                            .catch(error => {
                                document.getElementById('status').innerText = 'Error: ' + error;
                                document.getElementById('status').style.backgroundColor = '#f8d7da';
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
                                document.getElementById('status').style.backgroundColor = '#d4edda';
                            })
                            .catch(error => {
                                document.getElementById('status').innerText = 'Error: ' + error;
                                document.getElementById('status').style.backgroundColor = '#f8d7da';
                            });
                        } 
                    </script>
                </body>
                </html>
            """.trimIndent()
        }

        /**
         * Print text using the selected printer method with fallbacks
         */
        private fun printText(text: String): Boolean {
            try {
                Log.d(TAG, "Attempting to print using $selectedPrinter printer")

                // First attempt with selected printer
                val success = when (selectedPrinter) {
                    "thermal" -> printWithThermalPrinter(text)
                    "system" -> printWithSystemPrinter(text)
                    else -> printWithManufacturerPrinter(text)
                }

                // If selected method failed, try fallback printers
                if (!success) {
                    Log.d(TAG, "Primary printing method failed, trying fallbacks")

                    mainHandler.post {
                        Toast.makeText(context, "Primary printing method failed, trying fallbacks...", Toast.LENGTH_SHORT).show()
                    }

                    // Try manufacturer first if it wasn't the primary
                    if (selectedPrinter != "manufacturer" &&
                        manufacturerPrinterManager.isConnected() &&
                        printWithManufacturerPrinter(text)) {
                        return true
                    }

                    // Then try system printer
                    if (selectedPrinter != "system" &&
                        systemPrinterManager.isAvailable() &&
                        printWithSystemPrinter(text)) {
                        return true
                    }

                    // Finally try thermal printer
                    if (selectedPrinter != "thermal" &&
                        (thermalPrinterManager.isConnected() || thermalPrinterManager.connect()) &&
                        printWithThermalPrinter(text)) {
                        return true
                    }

                    // All methods failed
                    mainHandler.post {
                        Toast.makeText(context, "All printing methods failed", Toast.LENGTH_SHORT).show()
                    }
                    return false
                }

                return success
            } catch (e: Exception) {
                Log.e(TAG, "Error printing: ${e.message}")
                e.printStackTrace()
                return false
            }
        }

        /**
         * Try to print with the integrated manufacturer printer
         */
        private fun printWithManufacturerPrinter(text: String): Boolean {
            // Check if manufacturer printer is connected
            if (!manufacturerPrinterManager.isConnected()) {
                Log.d(TAG, "Manufacturer printer not available")

                mainHandler.post {
                    Toast.makeText(context, "Integrated printer not available", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            // Try to print with manufacturer printer
            Log.d(TAG, "Printing with integrated manufacturer printer")
            return manufacturerPrinterManager.printText(text)
        }

        /**
         * Try to print with the thermal printer
         */
        private fun printWithThermalPrinter(text: String): Boolean {
            // Check if thermal printer is connected
            if (!thermalPrinterManager.isConnected()) {
                Log.d(TAG, "Thermal printer not connected, attempting to reconnect")

                mainHandler.post {
                    Toast.makeText(context, "Thermal printer not connected. Attempting to reconnect...", Toast.LENGTH_SHORT).show()
                }

                // Try to reconnect
                if (!thermalPrinterManager.connect()) {
                    Log.d(TAG, "Failed to connect to thermal printer")

                    mainHandler.post {
                        Toast.makeText(context, "Failed to connect to thermal printer", Toast.LENGTH_SHORT).show()
                    }
                    return false
                }
            }

            // Try to print with thermal printer
            Log.d(TAG, "Printing with thermal printer")
            return thermalPrinterManager.printText(text)
        }

        /**
         * Try to print with the system printer
         */
        private fun printWithSystemPrinter(text: String): Boolean {
            // Check if system printer is available
            if (!systemPrinterManager.isAvailable()) {
                Log.d(TAG, "System printer not available")

                mainHandler.post {
                    Toast.makeText(context, "System printer not available", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            // Try to print with system printer
            Log.d(TAG, "Printing with system printer")
            return systemPrinterManager.printText(text)
        }
    }
}