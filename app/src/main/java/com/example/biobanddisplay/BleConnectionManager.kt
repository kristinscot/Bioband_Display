package com.example.biobanddisplay

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import kotlin.text.Charsets

// This Handler will run tasks on the main UI thread
private val mainHandler = Handler(Looper.getMainLooper())

// Listener for connection state changes, implemented by MainActivity
interface ConnectionStateListener {
    fun onConnectionStateChanged(state: Int, status: Int)
}

// A generic listener for incoming data, implemented by both Graph Activities
interface BleDataListener {
    fun onDataReceived(data: String)
}

object BleConnectionManager {
    var gatt: BluetoothGatt? = null

    // Listeners
    var connectionListener: ConnectionStateListener? = null
    var emgDataListener: BleDataListener? = null
    var ppgDataListener: BleDataListener? = null
    var sweatDataListener: BleDataListener? = null
    var testDataListener: BleDataListener? = null // New listener for Test Data

    // GATT constants that must match your nRF52840 firmware
    val SERVICE_UUID: UUID = UUID.fromString("75c276c3-8f97-20bc-a143-b354244886d4")
    // This is the single characteristic that sends both EMG and PPG data
    val NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString("d3d46a35-4394-e9aa-5ae7-921120aad4ed")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // A single, shared callback that forwards events to the active listeners
    @SuppressLint("MissingPermission")
    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BLE_MANAGER", "onConnectionStateChange: status=$status, newState=$newState")
            // Use the handler to post the event to the main thread safely
            mainHandler.post {
                connectionListener?.onConnectionStateChanged(newState, status)
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // When disconnected, clean up the GATT object
                this@BleConnectionManager.gatt?.close()
                this@BleConnectionManager.gatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_MANAGER", "Services discovered successfully. Enabling notifications.")
                val service = gatt.getService(SERVICE_UUID)
                service?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)?.let { characteristic ->
                    enableNotifications(gatt, characteristic)
                } ?: run {
                    Log.e("BLE_MANAGER", "Notify characteristic not found on service ${SERVICE_UUID}.")
                }
            } else {
                Log.w("BLE_MANAGER", "onServicesDiscovered received error: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val rawDataString = characteristic.value.toString(Charsets.UTF_8)
            Log.d("BLE_MANAGER", "Received raw data: $rawDataString")

            // Route the data to the correct listener based on its prefix
            mainHandler.post {
                when {
                    rawDataString.startsWith("EMG,") -> {
                        val emgValue = rawDataString.removePrefix("EMG,")
                        emgDataListener?.onDataReceived(emgValue)
                    }
                    rawDataString.startsWith("PPG,") -> {
                        val ppgValue = rawDataString.removePrefix("PPG,")
                        ppgDataListener?.onDataReceived(ppgValue)
                    }
                    rawDataString.startsWith("SWEAT,") -> {
                        val sweatValue = rawDataString.removePrefix("SWEAT,")
                        sweatDataListener?.onDataReceived(sweatValue)
                    }
                    // Handle Test data
                    rawDataString.startsWith("TEST,") -> {
                        val testValue = rawDataString.removePrefix("TEST,")
                        testDataListener?.onDataReceived(testValue)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE_MANAGER", "Notifications enabled successfully.")
            } else {
                Log.e("BLE_MANAGER", "Failed to write descriptor, status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e("BLE_MANAGER", "Could not find CCCD descriptor for characteristic: ${characteristic.uuid}")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
}
