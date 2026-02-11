package com.example.biobanddisplay

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyObject
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.FileOutputStream

class GraphActivity : AppCompatActivity(), BleDataListener {

    private val TAG = "EMG_GRAPH_ACTIVITY"
    private lateinit var lineChart: LineChart
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())

    // Buffering logic
    private val packetBuffer = mutableListOf<String>()
    private val PACKET_THRESHOLD = 10 // Process every 10 packets
    private var lastPacketInActivation: String? = null
    
    // Storage for results
    private val allFrequencies = mutableListOf<Float>()
    private val allVoltages = mutableListOf<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)
        lineChart = findViewById(R.id.line_chart)

        backButton = findViewById(R.id.back_button_emg)
        backButton.setOnClickListener {
            finish()
        }

        setupChart()

        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Connection lost, returning to main screen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        BleConnectionManager.emgDataListener = this
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.emgDataListener == this) {
            BleConnectionManager.emgDataListener = null
        }
    }

    override fun onDataReceived(data: String) {
        Log.d(TAG, "onDataReceived: '$data'")
        saveToFile(data)
        
        packetBuffer.add(data)

        if (packetBuffer.size >= PACKET_THRESHOLD) {
            processBufferedData()
        }
    }

    private fun processBufferedData() {
        try {
            val python = Python.getInstance()
            val analyzerModule = python.getModule("analyzer")
            
            // Combine buffered packets. 
            // If the previous set ended in activation, prepend that last packet.
            val dataToProcess = mutableListOf<String>()
            lastPacketInActivation?.let { dataToProcess.add(it) }
            dataToProcess.addAll(packetBuffer)
            
            val combinedDataString = dataToProcess.joinToString(",")
            
            val result: PyObject = analyzerModule.callAttr("process_ble_data", combinedDataString)
            val resultMap = result.asMap()

            val frequencies = resultMap[PyObject.fromJava("frequencies")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
            val voltages = resultMap[PyObject.fromJava("voltages")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
            val endsInActivation = resultMap[PyObject.fromJava("ends_in_activation")]?.toBoolean() ?: false

            Log.i(TAG, "Extracted Frequencies: ${frequencies.joinToString()}")
            Log.i(TAG, "Extracted Voltages: ${voltages.joinToString()}")

            // Store the results
            allFrequencies.addAll(frequencies.toList())
            allVoltages.addAll(voltages.toList())

            // Handle carry-over if ending in activation
            if (endsInActivation) {
                lastPacketInActivation = packetBuffer.lastOrNull()
            } else {
                lastPacketInActivation = null
            }

            // Clear buffer for next set
            packetBuffer.clear()

            // Update UI (e.g., plot voltages)
            handler.post {
                for (v in voltages) {
                    addChartEntry(v)
                }
            }

        } catch (e: PyException) {
            Log.e(TAG, "Python error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing buffer", e)
        }
    }

    private fun saveToFile(data: String) {
        try {
            val fileName = "emg_data_log.csv"
            val fileOutputStream: FileOutputStream = openFileOutput(fileName, Context.MODE_APPEND)
            fileOutputStream.write("$data\n".toByteArray())
            fileOutputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving data", e)
        }
    }

    private fun setupChart() {
        lineChart.setNoDataText("No EMG data available")
        lineChart.setNoDataTextColor(Color.WHITE)
        lineChart.description.apply {
            text = "Muscle Activation (Voltage Peaks)"
            textColor = Color.WHITE
        }
        lineChart.legend.textColor = Color.WHITE
        lineChart.xAxis.textColor = Color.WHITE
        lineChart.axisLeft.textColor = Color.WHITE
        lineChart.axisRight.isEnabled = false
        lineChart.data = LineData()
        lineChart.invalidate()
    }

    private fun addChartEntry(value: Float) {
        val data = lineChart.data ?: return
        var set = data.getDataSetByIndex(0)
        if (set == null) {
            set = createDataSet()
            data.addDataSet(set)
        }
        data.addEntry(Entry(set.entryCount.toFloat(), value), 0)
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(50f)
        lineChart.moveViewToX(data.entryCount.toFloat())
    }

    private fun createDataSet(): LineDataSet {
        return LineDataSet(null, "Voltage Peak").apply {
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
            color = Color.CYAN
            setCircleColor(Color.WHITE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextColor = Color.WHITE
            mode = LineDataSet.Mode.LINEAR
        }
    }
}
