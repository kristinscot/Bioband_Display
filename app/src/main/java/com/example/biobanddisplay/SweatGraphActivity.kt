package com.example.biobanddisplay

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import kotlin.concurrent.thread

class SweatGraphActivity : AppCompatActivity(), BleDataListener {

    private val TAG = "SWEAT_GRAPH_ACTIVITY"
    private lateinit var chart1: LineChart
    private lateinit var chart2: LineChart
    private lateinit var chart3: LineChart
    private lateinit var backButton: Button
    private lateinit var loadingOverlay: View
    private val handler = Handler(Looper.getMainLooper())

    private val colors = intArrayOf(
        Color.parseColor("#1F4E79"), // Dark Blue
        Color.parseColor("#ED7D31"), // Orange
        Color.parseColor("#70AD47"), // Green
        Color.parseColor("#5B9BD5"), // Light Blue
        Color.parseColor("#7030A0"), // Purple
        Color.parseColor("#A9D08E")  // Light Green
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sweat_graph)

        chart1 = findViewById(R.id.sweat_chart_1)
        chart2 = findViewById(R.id.sweat_chart_2)
        chart3 = findViewById(R.id.sweat_chart_3)
        backButton = findViewById(R.id.back_button_sweat)
        loadingOverlay = findViewById(R.id.loading_overlay_sweat)

        backButton.setOnClickListener { finish() }

        setupChart(chart1)
        setupChart(chart2)
        setupChart(chart3)

        loadSweatData()
    }

    private fun setupChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("Processing CSV Data...")
        chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
    }

    private fun loadSweatData() {
        loadingOverlay.visibility = View.VISIBLE
        thread {
            try {
                copyCsvToInternalStorage("Cortisol on IDE 1 with AB Dataset 1.csv")
                val csvFile = File(filesDir, "Cortisol on IDE 1 with AB Dataset 1.csv")
                
                val python = Python.getInstance()
                val analyzer = python.getModule("sweat_analyzer")
                val results = analyzer.callAttr("process_sweat_data", "INITIAL_LOAD", csvFile.absolutePath)
                val resultsMap = results.asMap()

                handler.post {
                    displayPlot(chart1, resultsMap[PyObject.fromJava("plot1")])
                    displayPlot(chart2, resultsMap[PyObject.fromJava("plot2")])
                    displayPlot(chart3, resultsMap[PyObject.fromJava("plot3")])
                    loadingOverlay.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sweat data", e)
                handler.post { loadingOverlay.visibility = View.GONE }
            }
        }
    }

    private fun displayPlot(chart: LineChart, plotData: PyObject?) {
        val dataMap = plotData?.asMap() ?: return
        val lineData = LineData()
        var colorIdx = 0
        
        // Define distinct colors explicitly
        val plotColors = intArrayOf(
            Color.parseColor("#1F4E79"), // Dark Blue
            Color.parseColor("#ED7D31"), // Orange
            Color.parseColor("#70AD47"), // Green
            Color.parseColor("#5B9BD5"), // Light Blue
            Color.parseColor("#7030A0"), // Purple
            Color.parseColor("#FF0000")  // Red
        )
        
        val concentrations = listOf("1pg/ml", "10pg/ml", "100pg/ml", "1ng/ml", "10ng/ml", "100ng/ml")
        
        for (conc in concentrations) {
            val concPy = PyObject.fromJava(conc)
            val concData = dataMap[concPy]?.asMap() ?: continue
            val xList = concData[PyObject.fromJava("x")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
            val yList = concData[PyObject.fromJava("y")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
            
            if (xList.isEmpty()) continue

            val entries = ArrayList<Entry>()
            for (i in xList.indices) {
                entries.add(Entry(xList[i], yList[i]))
            }
            
            // Sort by X for proper Nyquist rendering
            entries.sortBy { it.x }

            val set = LineDataSet(entries, conc)
            val currentColor = plotColors[colorIdx % plotColors.size]
            
            // Using explicit methods to avoid property ambiguity
            set.setColor(currentColor)
            set.setCircleColor(currentColor)
            set.lineWidth = 2.5f
            set.circleRadius = 4f
            set.setDrawCircleHole(false)
            set.setDrawValues(false)

            lineData.addDataSet(set)
            colorIdx++
        }

        if (lineData.dataSetCount > 0) {
            chart.data = lineData
            chart.invalidate()
        }
    }

    private fun copyCsvToInternalStorage(fileName: String) {
        try {
            val targetFile = File(filesDir, fileName)
            val python = Python.getInstance()
            val sys = python.getModule("sys")
            val pyPath = sys["path"]?.asList() ?: emptyList()
            
            var sourceFile: File? = null
            for (path in pyPath) {
                val potential = File(path.toString(), fileName)
                if (potential.exists()) { sourceFile = potential; break }
            }

            if (sourceFile != null) {
                sourceFile.inputStream().use { input ->
                    targetFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Copy error", e) }
    }

    override fun onResume() {
        super.onResume()
        BleConnectionManager.sweatDataListener = this
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.sweatDataListener == this) BleConnectionManager.sweatDataListener = null
    }

    override fun onDataReceived(data: String) {
        // Real-time implementation can be added here
    }
}
