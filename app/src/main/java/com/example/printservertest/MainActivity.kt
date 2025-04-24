// MainActivity.kt
package com.example.printservertest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val PORT = 8080
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
        thermalPrinterManager = ThermalPrinterManager(this)
        systemPrinterManager = SystemPrinterManager(this)
        manufacturerPrinterManager = ManufacturerPrinterManager(this)

        // Setup radio buttons for printer selection
        val printerTypeRadioGroup = findViewById<RadioGroup>(R.id.printerTypeRadioGroup)
        printerTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPrinter = when (checkedId) {
                R.id.thermalPrinterRadio -> "thermal"
                R.id.systemPrinterRadio -> "system"
                else -> "manufacturer"
            }

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
                    success = if (systemPrinterManager.isAvailable()) {
                        systemPrinterManager.printText(sampleTicket)
                    } else false
                }
                "manufacturer" -> {
                    success = if (manufacturerPrinterManager.isConnected()) {
                        manufacturerPrinterManager.printText(sampleTicket)
                    } else false
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
        if (printServer != null && printServer!!.isAlive) {
            printServer!!.stop()
        }
        if (::thermalPrinterManager.isInitialized) {
            thermalPrinterManager.disconnect()
        }
        if (::systemPrinterManager.isInitialized) {
            systemPrinterManager.cleanup()
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
                // First attempt with selected printer
                val success = when (selectedPrinter) {
                    "thermal" -> printWithThermalPrinter(text)
                    "system" -> printWithSystemPrinter(text)
                    else -> printWithManufacturerPrinter(text)
                }

                // If selected method failed, try fallback printers
                if (!success) {
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
                Log.e("PrintServer", "Error printing: ${e.message}")
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
                mainHandler.post {
                    Toast.makeText(context, "Integrated printer not available", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            // Try to print with manufacturer printer
            Log.d("PrintServer", "Printing with integrated manufacturer printer")
            return manufacturerPrinterManager.printText(text)
        }

        /**
         * Try to print with the thermal printer
         */
        private fun printWithThermalPrinter(text: String): Boolean {
            // Check if thermal printer is connected
            if (!thermalPrinterManager.isConnected()) {
                mainHandler.post {
                    Toast.makeText(context, "Thermal printer not connected. Attempting to reconnect...", Toast.LENGTH_SHORT).show()
                }

                // Try to reconnect
                if (!thermalPrinterManager.connect()) {
                    mainHandler.post {
                        Toast.makeText(context, "Failed to connect to thermal printer", Toast.LENGTH_SHORT).show()
                    }
                    return false
                }
            }

            // Try to print with thermal printer
            Log.d("PrintServer", "Printing with thermal printer")
            return thermalPrinterManager.printText(text)
        }

        /**
         * Try to print with the system printer
         */
        private fun printWithSystemPrinter(text: String): Boolean {
            // Check if system printer is available
            if (!systemPrinterManager.isAvailable()) {
                mainHandler.post {
                    Toast.makeText(context, "System printer not available", Toast.LENGTH_SHORT).show()
                }
                return false
            }

            // Try to print with system printer
            Log.d("PrintServer", "Printing with system printer")
            return systemPrinterManager.printText(text)
        }
    }
}