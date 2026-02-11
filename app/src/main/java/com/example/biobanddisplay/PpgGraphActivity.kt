package com.example.biobanddisplay

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

// This activity handles PPG data
class PpgGraphActivity : AppCompatActivity(), BleDataListener {

    private val TAG = "PPG_GRAPH_ACTIVITY"
    private lateinit var lineChart: LineChart
    private lateinit var backButton: Button // Variable for the back button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ppg_graph)
        lineChart = findViewById(R.id.ppg_line_chart)

        // --- Setup for the Back Button ---
        backButton = findViewById(R.id.back_button_ppg)
        backButton.setOnClickListener {
            // finish() closes the current activity and returns to the previous one (MainActivity)
            finish()
        }
        // --- End of Button Setup ---

        setupChart()

        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Connection lost, returning to main screen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        // This activity is listening for PPG data.
        BleConnectionManager.ppgDataListener = this

        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Device Disconnected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // When we leave, we must stop listening for data.
        if (BleConnectionManager.ppgDataListener == this) {
            BleConnectionManager.ppgDataListener = null
        }
    }

    // This is called from BleConnectionManager when new PPG data arrives
    override fun onDataReceived(data: String) {
        Log.d(TAG, "onDataReceived: Attempting to process PPG data string: '$data'")
        try {
            val python = Python.getInstance()
            val analyzerModule = python.getModule("ppg_analyzer") // ppg_analyzer.py for PPG
            val processedDataPyObject = analyzerModule.callAttr("process_ppg_data", data)
            @Suppress("UNCHECKED_CAST")
            val processedData: List<Float> = processedDataPyObject.toJava(List::class.java) as List<Float>
            Log.i(TAG, "Processed PPG data from Python: $processedData")

            handler.post {
                for (point in processedData) {
                    addChartEntry(point)
                }
            }
        } catch (e: PyException) {
            val pythonStackTrace = e.toString()
            Log.e(TAG, "A Python error occurred in PPG processing: ${e.message}")
            Log.e(TAG, "Python stack trace:\n$pythonStackTrace")
        } catch (e: Throwable) {
            Log.e(TAG, "A critical error occurred in PPG onDataReceived", e)
        }
    }

    private fun setupChart() {
        lineChart.setNoDataText("No PPG data available")
        lineChart.setNoDataTextColor(Color.WHITE)
        lineChart.description.apply {
            text = "PPG Sensor Data"
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
        lineChart.setVisibleXRangeMaximum(100f)
        lineChart.moveViewToX(data.entryCount.toFloat())
    }

    private fun createDataSet(): LineDataSet {
        return LineDataSet(null, "PPG Data").apply {
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
            color = Color.GREEN // Green for PPG
            setCircleColor(Color.WHITE)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
    }
}
