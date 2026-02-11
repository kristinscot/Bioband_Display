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

class SweatGraphActivity : AppCompatActivity(), BleDataListener {

    private val TAG = "SWEAT_GRAPH_ACTIVITY"
    private lateinit var lineChart: LineChart
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sweat_graph)
        lineChart = findViewById(R.id.sweat_line_chart)
        backButton = findViewById(R.id.back_button_sweat)
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
        BleConnectionManager.sweatDataListener = this
        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Device Disconnected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.sweatDataListener == this) {
            BleConnectionManager.sweatDataListener = null
        }
    }

    override fun onDataReceived(data: String) {
        Log.d(TAG, "onDataReceived: Attempting to process sweat data string: '$data'")
        try {
            val python = Python.getInstance()
            val analyzerModule = python.getModule("sweat_analyzer")
            val processedDataPyObject = analyzerModule.callAttr("process_sweat_data", data)
            @Suppress("UNCHECKED_CAST")
            val processedData: List<Float> = processedDataPyObject.toJava(List::class.java) as List<Float>
            handler.post {
                for (point in processedData) {
                    addChartEntry(point)
                }
            }
        } catch (e: PyException) {
            val pythonStackTrace = e.toString()
            Log.e(TAG, "A Python error occurred in sweat processing: ${e.message}")
            Log.e(TAG, "Python stack trace:\n$pythonStackTrace")
        } catch (e: Throwable) {
            Log.e(TAG, "A critical error occurred in sweat onDataReceived", e)
        }
    }

    private fun setupChart() {
        lineChart.setNoDataText("No Sweat data available")
        lineChart.setNoDataTextColor(Color.WHITE)
        lineChart.description.apply { text = "Sweat Sensor Data"; textColor = Color.WHITE }
        lineChart.data = LineData()
        lineChart.legend.textColor = Color.WHITE
        lineChart.xAxis.textColor = Color.WHITE
        lineChart.axisLeft.textColor = Color.WHITE
        lineChart.axisRight.isEnabled = false
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
        return LineDataSet(null, "Sweat Data").apply {
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
            color = Color.YELLOW // Use a different color for Sweat
            setCircleColor(Color.WHITE)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
    }
}
    