package com.example.biobanddisplay

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), ConnectionStateListener {

    private val TAG = "BLE_CONNECT_APP"

    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    private var isPythonReady = false
    private var isBleReady = false
    private var scanning = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var buttonsContainer: View

    private val SCAN_PERIOD: Long = 30000 
    private val DEVICE_NAME = "ADC_DONGLE" // Matches the updated firmware name

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startAppInitialization()
            } else {
                Toast.makeText(this, "Bluetooth & Location permissions are required.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        buttonsContainer = findViewById(R.id.buttons_container)

        // Navigation buttons
        findViewById<Button>(R.id.show_graph_button).setOnClickListener {
            if (BleConnectionManager.gatt != null) startActivity(Intent(this, GraphActivity::class.java))
            else startBleScan()
        }
        findViewById<Button>(R.id.ppg_data_button).setOnClickListener { startActivity(Intent(this, PpgGraphActivity::class.java)) }
        findViewById<Button>(R.id.sweat_data_button).setOnClickListener { startActivity(Intent(this, SweatGraphActivity::class.java)) }
        findViewById<Button>(R.id.real_time_button).setOnClickListener { startActivity(Intent(this, RealTimeActivity::class.java)) }
        findViewById<Button>(R.id.journal_button).setOnClickListener { startActivity(Intent(this, JournalActivity::class.java)) }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionLauncher.launch(permissions)
    }

    private fun startAppInitialization() {
        if (!Python.isStarted()) {
            thread {
                Python.start(AndroidPlatform(this))
                isPythonReady = true
                handler.post { startBleSetup() }
            }
        } else {
            isPythonReady = true
            startBleSetup()
        }
    }

    private fun startBleSetup() {
        if (!bluetoothAdapter.isEnabled) {
            statusText.text = "Bluetooth is OFF. Please turn it ON."
        } else {
            isBleReady = true
            startBleScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (scanning || !isBleReady) return

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            statusText.text = "Scanner Error. Is Bluetooth ON?"
            return
        }

        scanning = true
        statusText.text = "Searching for Device..."
        Log.i(TAG, "--- Scan Started ---")

        handler.postDelayed({ if (scanning) stopBleScan() }, SCAN_PERIOD)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!scanning) return
        scanning = false
        bluetoothAdapter.bluetoothLeScanner?.stopScan(leScanCallback)
        Log.i(TAG, "--- Scan Stopped ---")
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            val address = result.device.address
            val uuids = result.scanRecord?.serviceUuids

            Log.d(TAG, "Discovered: Name=$name, Address=$address, UUIDs=$uuids")

            val targetUuid = BleConnectionManager.SERVICE_UUID.toString()
            val hasOurUuid = uuids?.any { it.uuid.toString().equals(targetUuid, ignoreCase = true) } ?: false

            // Connect if Name matches OR if our Service UUID is present
            if (name == DEVICE_NAME || name == "Test Device" || hasOurUuid) {
                Log.i(TAG, ">>> TARGET FOUND! Connecting to $address")
                stopBleScan()
                connectToDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            handler.post { statusText.text = "Scan Failed ($errorCode)" }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        handler.post { statusText.text = "Connecting..." }
        BleConnectionManager.gatt = device.connectGatt(this, false, BleConnectionManager.gattCallback)
    }

    override fun onConnectionStateChanged(state: Int, status: Int) {
        handler.post {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                statusText.text = "Connected!"
                buttonsContainer.visibility = View.VISIBLE
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    BleConnectionManager.gatt?.discoverServices()
                }
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                statusText.text = "Disconnected. Scanning..."
                buttonsContainer.visibility = View.GONE
                startBleScan()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BleConnectionManager.connectionListener = this
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        BleConnectionManager.gatt?.close()
        BleConnectionManager.gatt = null
    }
}
