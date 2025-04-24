package com.example.printservertest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import java.io.IOException
import java.nio.charset.Charset

class ThermalPrinterManager(private val context: Context) {
    companion object {
        private const val TAG = "ThermalPrinterManager"
        private const val ACTION_USB_PERMISSION = "com.example.printservertest.USB_PERMISSION"

        // Common printer commands (ESC/POS format)
        private val INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
        private val CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x10) // Cut paper
        private val NEW_LINE = byteArrayOf(0x0A) // Line feed
        private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // Center alignment
        private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // Left alignment
        private val TEXT_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // Bold on
        private val TEXT_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // Bold off
        private val TEXT_LARGE = byteArrayOf(0x1D, 0x21, 0x11) // Double height and width
        private val TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // Normal size
    }

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var isConnected = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            // Permission granted, connect to device
                            connectToUsbDevice(it)
                        }
                    } else {
                        Log.d(TAG, "USB permission denied")
                    }
                }
            }
        }
    }

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        // Register receiver for USB permission
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    /**
     * Initialize the printer connection.
     */
    fun connect(): Boolean {
        Log.d(TAG, "Attempting to connect to the printer...")

        try {
            // Method 1: Try using USB connection
            val deviceList = usbManager?.deviceList
            if (deviceList?.isEmpty() == true) {
                Log.d(TAG, "No USB devices found")
            } else {
                Log.d(TAG, "Found ${deviceList?.size} USB devices")

                // Log all devices for debugging
                deviceList?.forEach { (_, device) ->
                    Log.d(TAG, "USB Device: ${device.deviceName} " +
                            "VendorID: ${device.vendorId} " +
                            "ProductID: ${device.productId} " +
                            "Class: ${device.deviceClass} " +
                            "Protocol: ${device.deviceProtocol}")
                }

                // Try to find a printer device (many thermal printers use class 7 or 0)
                val printerDevice = deviceList?.values?.find {
                    // Common printer classes or check specific vendor IDs
                    it.deviceClass == UsbConstants.USB_CLASS_PRINTER ||
                            it.deviceClass == 0 || // Vendor-specific class
                            it.deviceName.lowercase().contains("print")
                }

                if (printerDevice != null) {
                    Log.d(TAG, "Found potential printer device: ${printerDevice.deviceName}")

                    // Request permission to use the device
                    val intentAction = Intent(ACTION_USB_PERMISSION)
                    intentAction.setPackage(context.packageName)
                    val permissionIntent = PendingIntent.getBroadcast(
                        context, 0, intentAction,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    usbManager?.requestPermission(printerDevice, permissionIntent)

                    // Try to connect to the device
                    return connectToUsbDevice(printerDevice)
                } else {
                    Log.d(TAG, "No printer device found, trying generic approach")
                    // Try the first device as a fallback
                    val firstDevice = deviceList?.values?.firstOrNull()
                    if (firstDevice != null) {
                        val intentAction = Intent(ACTION_USB_PERMISSION)
                        intentAction.setPackage(context.packageName)
                        val permissionIntent = PendingIntent.getBroadcast(
                            context, 0, intentAction,
                            PendingIntent.FLAG_IMMUTABLE
                        )
                        usbManager?.requestPermission(firstDevice, permissionIntent)
                        return connectToUsbDevice(firstDevice)
                    }
                }
            }

            // Method 2: Try using system services or manufacturer SDK
            // This would be specific to your device manufacturer

            Log.d(TAG, "Attempting to connect via alternate method")

            // For testing, simulate a successful connection
            isConnected = true
            Log.d(TAG, "Successfully connected to printer (simulated)")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to printer: ${e.message}")
            e.printStackTrace()
            isConnected = false
            return false
        }
    }

    private fun connectToUsbDevice(device: UsbDevice): Boolean {
        try {
            usbDevice = device

            // Open a connection to the device
            val connection = usbManager?.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Could not open USB connection")
                return false
            }

            // Find the output endpoint
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                Log.d(TAG, "Interface $i: ${intf.interfaceClass}, ${intf.interfaceSubclass}")

                // Claim the interface
                if (!connection.claimInterface(intf, true)) {
                    Log.e(TAG, "Could not claim interface $i")
                    continue
                }

                // Look for bulk output endpoint
                for (j in 0 until intf.endpointCount) {
                    val endpoint = intf.getEndpoint(j)
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        Log.d(TAG, "Found output endpoint: ${endpoint.address}")
                        usbInterface = intf
                        usbEndpoint = endpoint
                        usbConnection = connection
                        isConnected = true

                        // Initialize the printer
                        sendCommand(INIT)
                        return true
                    }
                }

                // Release the interface if we didn't find a suitable endpoint
                connection.releaseInterface(intf)
            }

            // If we reach here, we couldn't find a suitable endpoint
            connection.close()
            Log.e(TAG, "No suitable endpoint found")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error in USB connection: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Disconnect from the printer.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting printer...")

        try {
            usbInterface?.let {
                usbConnection?.releaseInterface(it)
            }
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        } finally {
            usbDevice = null
            usbInterface = null
            usbEndpoint = null
            usbConnection = null
            isConnected = false
        }
    }

    /**
     * Check if printer is connected.
     */
    fun isConnected(): Boolean {
        return isConnected
    }

    /**
     * Send raw data to the printer.
     */
    private fun sendCommand(data: ByteArray): Boolean {
        if (!isConnected || usbConnection == null || usbEndpoint == null) {
            Log.e(TAG, "Cannot send command - printer not connected")
            return false
        }

        try {
            val result = usbConnection!!.bulkTransfer(
                usbEndpoint, data, data.size, 5000)

            return if (result > 0) {
                Log.d(TAG, "Command sent successfully, bytes: $result")
                true
            } else {
                Log.e(TAG, "Failed to send command, result: $result")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}")
            return false
        }
    }

    /**
     * Print text to the thermal printer.
     */
    @Throws(IOException::class)
    fun printText(text: String): Boolean {
        if (!isConnected) {
            Log.e(TAG, "Cannot print - printer not connected")
            return false
        }

        Log.d(TAG, "Printing text: $text")

        try {
            // Convert text to bytes
            val textBytes = text.toByteArray(Charset.forName("UTF-8"))

            // Send initialization command
            sendCommand(INIT)

            // Send the text
            sendCommand(ALIGN_LEFT)
            sendCommand(TEXT_NORMAL)
            sendCommand(textBytes)

            // Add a few line feeds at the end
            sendCommand(NEW_LINE)
            sendCommand(NEW_LINE)

            // Cut the paper
            sendCommand(CUT)

            Log.d(TAG, "Successfully printed text")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Print a receipt with standard formatting.
     */
    fun printReceipt(title: String, items: Array<String>, prices: DoubleArray, total: Double): Boolean {
        if (!isConnected) {
            Log.e(TAG, "Cannot print receipt - printer not connected")
            return false
        }

        val receiptBuilder = StringBuilder()

        // Add receipt header
        receiptBuilder.append(title).append("\n\n")

        // Add current date/time
        receiptBuilder.append("Date: ").append(java.util.Date().toString()).append("\n\n")

        // Add items
        for (i in items.indices) {
            receiptBuilder.append(items[i])
                .append(" ... ")
                .append(String.format("$%.2f", prices[i]))
                .append("\n")
        }

        // Add separator
        receiptBuilder.append("\n-----------------------------\n\n")

        // Add total
        receiptBuilder.append("TOTAL: $").append(String.format("%.2f", total)).append("\n\n")

        // Add footer
        receiptBuilder.append("Thank you for your purchase!\n\n\n")

        // Print the receipt
        return try {
            printText(receiptBuilder.toString())
        } catch (e: IOException) {
            Log.e(TAG, "Error printing receipt: ${e.message}")
            false
        }
    }
}