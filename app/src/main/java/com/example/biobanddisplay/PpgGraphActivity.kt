package com.example.biobanddisplay

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import java.io.File
import java.util.Locale

class PpgGraphActivity : AppCompatActivity(), BleDataListener {

    private val TAG = "PPG_GRAPH_ACTIVITY"
    private lateinit var heartRateText: TextView
    private lateinit var spo2Text: TextView
    private lateinit var artStiffnessText: TextView
    private lateinit var breathingRateText: TextView
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ppg_graph)
        
        heartRateText = findViewById(R.id.heart_rate_text)
        spo2Text = findViewById(R.id.spo2_text)
        artStiffnessText = findViewById(R.id.art_stiffness_text)
        breathingRateText = findViewById(R.id.breathing_rate_text)
        backButton = findViewById(R.id.back_button_ppg)

        backButton.setOnClickListener {
            finish()
        }

        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Device not connected. Showing stored data.", Toast.LENGTH_SHORT).show()
        }
        
        // Initial process to load data from CSV
        onDataReceived("INITIAL_LOAD")
    }

    override fun onResume() {
        super.onResume()
        BleConnectionManager.ppgDataListener = this
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.ppgDataListener == this) {
            BleConnectionManager.ppgDataListener = null
        }
    }

    override fun onDataReceived(data: String) {
        try {
            val python = Python.getInstance()
            val analyzerModule = python.getModule("ppg_analyzer")
            
            val csvFile = File(filesDir, "ppg_filtered_data.csv")
            val result = analyzerModule.callAttr("process_ppg_data", data, csvFile.absolutePath).asMap()

            val heartRate = result[com.chaquo.python.PyObject.fromJava("bpm")]?.toFloat() ?: 0f
            val spo2 = result[com.chaquo.python.PyObject.fromJava("SpO2")]?.toFloat() ?: 0f
            val artStiffness = result[com.chaquo.python.PyObject.fromJava("artStiffness")]?.toFloat() ?: 0f
            val breathingRate = result[com.chaquo.python.PyObject.fromJava("breathingRate")]?.toFloat() ?: 0f

            handler.post {
                heartRateText.text = String.format(Locale.US, "%.0f BPM", heartRate)
                spo2Text.text = String.format(Locale.US, "%.1f %%", spo2)
                artStiffnessText.text = String.format(Locale.US, "%.2f m/s", artStiffness)
                breathingRateText.text = String.format(Locale.US, "%.0f BrPM", breathingRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing PPG data", e)
        }
    }
}
