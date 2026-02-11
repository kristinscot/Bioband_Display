package com.example.biobanddisplay

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.*
import kotlin.concurrent.thread

class TestGraphActivity : AppCompatActivity(), BleDataListener {

    private lateinit var chart: LineChart
    private lateinit var backButton: Button
    private lateinit var loadCsvButton: Button
    private lateinit var loadingIndicator: ProgressBar

    // Data handling
    private val entries1 = ArrayList<Entry>()
    private val entries2 = ArrayList<Entry>()
    private val entries3 = ArrayList<Entry>()
    
    private var xValueCounter = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_graph)

        chart = findViewById(R.id.chart)
        backButton = findViewById(R.id.btnBack)
        loadCsvButton = findViewById(R.id.btnLoadCsv)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        setupChart()

        backButton.setOnClickListener {
            finish()
        }
        
        loadCsvButton.setOnClickListener {
            loadAndPlotCsvData()
        }
    }

    override fun onResume() {
        super.onResume()
        BleConnectionManager.testDataListener = this
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.testDataListener == this) {
            BleConnectionManager.testDataListener = null
        }
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setDrawGridBackground(false)
        chart.setPinchZoom(true)
        chart.setBackgroundColor(Color.TRANSPARENT)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.WHITE
        xAxis.setDrawGridLines(false)

        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.WHITE
        leftAxis.setDrawGridLines(true)

        chart.axisRight.isEnabled = false
        
        val dataSets = ArrayList<ILineDataSet>()
        chart.data = LineData(dataSets)
    }

    override fun onDataReceived(data: String) {
        processAndPlotData(data)
    }

    private fun processAndPlotData(rawString: String) {
        val python = Python.getInstance()
        val pyModule = python.getModule("test_analyzer")
        val resultList = pyModule.callAttr("process_test_data", rawString).asList()
        
        for (item in resultList) {
            val value = item.toFloat()
            addEntryToChart(value, 0)
        }
    }
    
    private fun loadAndPlotCsvData() {
        // Show the loading indicator
        loadingIndicator.visibility = View.VISIBLE
        
        // Use a background thread to prevent the UI from freezing
        thread {
            try {
                val python = Python.getInstance()
                val pyModule = python.getModule("test_analyzer")
                val resultList = pyModule.callAttr("get_csv_data").asList()
                
                val newEntries1 = ArrayList<Entry>()
                val newEntries2 = ArrayList<Entry>()
                val newEntries3 = ArrayList<Entry>()

                for (i in resultList.indices) {
                    val row = resultList[i].asList()
                    val xVal = if (row.isNotEmpty()) row[0].toFloat() else i.toFloat()
                    
                    if (row.size > 1) newEntries1.add(Entry(xVal, row[1].toFloat()))
                    if (row.size > 2) newEntries2.add(Entry(xVal, row[2].toFloat()))
                    if (row.size > 3) newEntries3.add(Entry(xVal, row[3].toFloat()))
                }

                // Switch back to the main thread to update the UI
                runOnUiThread {
                    entries1.clear()
                    entries1.addAll(newEntries1)
                    entries2.clear()
                    entries2.addAll(newEntries2)
                    entries3.clear()
                    entries3.addAll(newEntries3)

                    val sets = ArrayList<ILineDataSet>()
                    if (entries1.isNotEmpty()) sets.add(createSet(entries1, "ECG", Color.YELLOW))
                    if (entries2.isNotEmpty()) sets.add(createSet(entries2, "Blood Pressure", Color.CYAN))
                    
                    chart.data = LineData(sets)
                    chart.notifyDataSetChanged()
                    chart.invalidate()
                    chart.fitScreen()
                    chart.moveViewToX(0f)
                    
                    // Hide loading indicator
                    loadingIndicator.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingIndicator.visibility = View.GONE
                    // Log or show error toast if needed
                }
            }
        }
    }

    private fun addEntryToChart(value: Float, datasetIndex: Int) {
        val data = chart.data
        if (data != null) {
            var set = data.getDataSetByIndex(datasetIndex)
            if (set == null) {
                set = createSet(ArrayList(), "Real-time", Color.YELLOW)
                data.addDataSet(set)
            }

            data.addEntry(Entry(xValueCounter, value), datasetIndex)
            if (datasetIndex == 0) xValueCounter++

            data.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.moveViewToX(data.entryCount.toFloat())
        }
    }

    private fun createSet(entries: ArrayList<Entry>, label: String, color: Int): LineDataSet {
        val set = LineDataSet(entries, label)
        set.color = color
        set.lineWidth = 2f
        set.setDrawCircles(false)
        set.setDrawValues(false)
        set.axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        return set
    }
}
