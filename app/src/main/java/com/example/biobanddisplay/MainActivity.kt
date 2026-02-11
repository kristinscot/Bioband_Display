package com.example.biobanddisplay

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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

    // Bluetooth setup
    private val bluetoothManager: BluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    // App State
    private var isPythonReady = false
    private var isBleReady = false
    private var scanning = false

    // UI and Threading
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var showGraphButton: Button
    private lateinit var ppgDataButton: Button
    private lateinit var sweatDataButton: Button
    private lateinit var testDataButton: Button // New button variable
    private lateinit var journalButton: Button // New button variable
    private lateinit var buttonsContainer: View

    // Constants
    private val SCAN_PERIOD: Long = 15000
    private val DEVICE_NAME = "Test Device" // <-- IMPORTANT: Make sure this name is correct

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Log.d(TAG, "Permissions granted.")
                startAppInitialization()
            } else {
                Toast.makeText(this, "Permissions are required for this app to function.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate: Activity starting.")

        // --- Initialize UI ---
        statusText = findViewById(R.id.status_text)
        showGraphButton = findViewById(R.id.show_graph_button)
        ppgDataButton = findViewById(R.id.ppg_data_button)
        sweatDataButton = findViewById(R.id.sweat_data_button)
        testDataButton = findViewById(R.id.test_data_button)
        journalButton = findViewById(R.id.journal_button)
        buttonsContainer = findViewById(R.id.buttons_container)

        // --- Setup Click Listeners ---
        showGraphButton.setOnClickListener {
            if (BleConnectionManager.gatt != null) {
                val intent = Intent(this, GraphActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Device is disconnected. Please wait.", Toast.LENGTH_SHORT).show()
                buttonsContainer.visibility = View.GONE
                startBleScan() // Try to reconnect
            }
        }

        ppgDataButton.setOnClickListener {
            val intent = Intent(this, PpgGraphActivity::class.java)
            startActivity(intent)
        }

        sweatDataButton.setOnClickListener {
            val intent = Intent(this, SweatGraphActivity::class.java)
            startActivity(intent)
        }

        testDataButton.setOnClickListener { // Setup new button listener
            val intent = Intent(this, TestGraphActivity::class.java)
            startActivity(intent)
        }

        journalButton.setOnClickListener {
            val intent = Intent(this, JournalActivity::class.java)
            startActivity(intent)
        }
        // --- End of Setup ---


        // Start the permission request flow. Everything else follows from here.
        requestPermissions()
        Log.d(TAG, "onCreate: Permission check initiated.")
    }

    private fun requestPermissions() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val allPermissionsGranted = permissionsToRequest.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            startAppInitialization()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startAppInitialization() {
        Log.d(TAG, "Starting app initialization sequence.")

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!isPythonReady) {
            thread(start = true) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@MainActivity))
                    Log.d(TAG, "Python started successfully.")
                }
                isPythonReady = true
                handler.post { startBleSetup() }
            }
        } else {
            startBleSetup()
        }
    }

    private fun startBleSetup() {
        if (!isPythonReady) {
            Log.e(TAG, "Cannot start BLE setup until Python is ready.")
            return
        }

        isBleReady = false
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            startActivity(enableBtIntent)
        } else {
            isBleReady = true
            startBleScan()
        }
    }

    override fun onResume() {
        super.onResume()
        // This activity is now the primary listener for connection state
        BleConnectionManager.connectionListener = this

        // If we return and BLE is ready, start a scan
        if (isPythonReady && bluetoothAdapter.isEnabled && !isBleReady && BleConnectionManager.gatt == null) {
            isBleReady = true
            startBleScan()
        }
    }

    override fun onPause() {
        super.onPause()
        // Only clear the listener if this activity is the one that set it
        if (BleConnectionManager.connectionListener == this) {
            BleConnectionManager.connectionListener = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (scanning || !isBleReady) return
        scanning = true
        Log.i(TAG, "Starting BLE scan...")
        handler.post { statusText.text = "Scanning for $DEVICE_NAME..." }
        handler.postDelayed({
            if (scanning) stopBleScan()
        }, SCAN_PERIOD)
        bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (!scanning) return
        scanning = false
        Log.i(TAG, "Stopping BLE scan.")
        bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) {
                Log.i(TAG, "Found device: ${result.device.name} @ ${result.device.address}")
                stopBleScan()
                connectToDevice(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code $errorCode")
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.i(TAG, "Connecting to ${device.address}...")
        handler.post { statusText.text = "Connecting..." }
        BleConnectionManager.gatt = device.connectGatt(this, false, BleConnectionManager.gattCallback)
    }

    override fun onConnectionStateChanged(state: Int, status: Int) {
        handler.post {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Device connected successfully. Discovering services...")
                        statusText.text = "Device Connected!"
                        buttonsContainer.visibility = View.VISIBLE // Show the button container

                        // Discover services right after connection
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                                this, Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return@post
                        }
                        BleConnectionManager.gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Device disconnected.")
                    statusText.text = "Disconnected. Scanning again..."
                    buttonsContainer.visibility = View.GONE // Hide the button container
                    isBleReady = true
                    startBleScan()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        BleConnectionManager.gatt?.close()
        BleConnectionManager.gatt = null
    }
}
