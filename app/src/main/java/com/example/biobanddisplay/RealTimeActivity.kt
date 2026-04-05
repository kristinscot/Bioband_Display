package com.example.biobanddisplay

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class RealTimeActivity : AppCompatActivity() {

    private val TAG = "REAL_TIME_ACTIVITY"
    private lateinit var ppgChart: LineChart
    private lateinit var emgChart: LineChart
    private lateinit var emgMetricsText: TextView
    private lateinit var ppgMetricsText: TextView
    private lateinit var loadingOverlay: View
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private val emgListener = object : BleDataListener {
        override fun onDataReceived(data: String) {
            processEmgData(data, false)
        }
    }

    private val ppgListener = object : BleDataListener {
        override fun onDataReceived(data: String) {
            processPpgData(data, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_real_time)

        ppgChart = findViewById(R.id.ppg_chart_realtime)
        emgChart = findViewById(R.id.emg_chart_realtime)
        emgMetricsText = findViewById(R.id.emg_metrics_realtime)
        ppgMetricsText = findViewById(R.id.ppg_metrics_realtime)
        loadingOverlay = findViewById(R.id.loading_overlay)
        backButton = findViewById(R.id.back_button_realtime)

        backButton.setOnClickListener { finish() }

        setupChart(ppgChart, "PPG Data", Color.GREEN)
        setupEmgChart(emgChart)

        loadingOverlay.visibility = View.VISIBLE

        thread {
            try {
                copyCsvToInternalStorage("ppg_filtered_data.csv")
                copyCsvToInternalStorage("LONG02.CSV")
                
                processPpgData("INITIAL_LOAD", true)
                processEmgData("INITIAL_LOAD", true)
            } finally {
                handler.post { loadingOverlay.visibility = View.GONE }
            }
        }

        if (BleConnectionManager.gatt == null) {
            Toast.makeText(this, "Device not connected. Showing stored data.", Toast.LENGTH_SHORT).show()
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
        BleConnectionManager.emgDataListener = emgListener
        BleConnectionManager.ppgDataListener = ppgListener
    }

    override fun onPause() {
        super.onPause()
        if (BleConnectionManager.emgDataListener == emgListener) BleConnectionManager.emgDataListener = null
        if (BleConnectionManager.ppgDataListener == ppgListener) BleConnectionManager.ppgDataListener = null
    }

    private fun processEmgData(data: String, isInitial: Boolean) {
        val action = {
            try {
                val python = Python.getInstance()
                val analyzer = python.getModule("analyzer")
                val csvFile = File(filesDir, "LONG02.CSV")
                val result = analyzer.callAttr("process_ble_data", data, csvFile.absolutePath).asMap()
                
                val movAvg = result[com.chaquo.python.PyObject.fromJava("mov_avg")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
                val rectified = result[com.chaquo.python.PyObject.fromJava("rectified")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
                val stftMnf = result[com.chaquo.python.PyObject.fromJava("stft_mnf")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
                
                val vrms = result[com.chaquo.python.PyObject.fromJava("vrms")]?.toFloat() ?: 0f
                val tdmf = result[com.chaquo.python.PyObject.fromJava("tdmf")]?.toFloat() ?: 0f
                val isResting = result[com.chaquo.python.PyObject.fromJava("is_resting")]?.toBoolean() ?: true
                
                handler.post {
                    emgMetricsText.text = String.format(Locale.US, "Vrms: %.2f mV | MNF: %.0f Hz", vrms, tdmf)
                    emgMetricsText.setTextColor(if (isResting) Color.parseColor("#BDC3C7") else Color.GREEN)
                    
                    if (isInitial) {
                        setEmgTripleData(emgChart, rectified, movAvg, stftMnf)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "EMG error", e) }
        }

        if (isInitial) action() else thread { action() }
    }

    private fun processPpgData(data: String, isInitial: Boolean) {
        val action = {
            try {
                val python = Python.getInstance()
                val analyzer = python.getModule("ppg_analyzer")
                val csvFile = File(filesDir, "ppg_filtered_data.csv")
                val result = analyzer.callAttr("process_ppg_data", data, csvFile.absolutePath).asMap()
                
                val pointsRed = result[com.chaquo.python.PyObject.fromJava("points_red")]?.toJava(FloatArray::class.java) ?: floatArrayOf()
                val bpm = result[com.chaquo.python.PyObject.fromJava("bpm")]?.toFloat() ?: 0f
                val spo2 = result[com.chaquo.python.PyObject.fromJava("SpO2")]?.toFloat() ?: 0f

                handler.post {
                    ppgMetricsText.text = String.format(Locale.US, "BPM: %.0f | SpO2: %.1f%%", bpm, spo2)
                    
                    if (isInitial) {
                        setBulkData(ppgChart, pointsRed)
                    }
                }
            } catch (e: Exception) { Log.e(TAG, "PPG error", e) }
        }

        if (isInitial) action() else thread { action() }
    }

    private fun setupChart(chart: LineChart, label: String, color: Int) {
        chart.description.isEnabled = false
        chart.setNoDataText("Waiting for data...")
        chart.setNoDataTextColor(Color.WHITE)
        chart.xAxis.textColor = Color.BLACK
        chart.axisLeft.textColor = Color.BLACK
        chart.axisRight.isEnabled = false
        chart.legend.textColor = Color.BLACK
        chart.data = LineData()
    }

    private fun setupEmgChart(chart: LineChart) {
        setupChart(chart, "EMG", Color.CYAN)
        chart.axisRight.isEnabled = true
        chart.axisRight.textColor = Color.RED
        chart.axisRight.setDrawGridLines(false)
        chart.axisRight.axisMinimum = 0f
        chart.axisRight.axisMaximum = 500f // Max frequency (Hz)
    }

    private fun setEmgTripleData(chart: LineChart, rectified: FloatArray, movAvg: FloatArray, stftMnf: FloatArray) {
        val rectifiedEntries = ArrayList<Entry>()
        val movAvgEntries = ArrayList<Entry>()
        val stftEntries = ArrayList<Entry>()
        
        for (i in rectified.indices) rectifiedEntries.add(Entry(i.toFloat(), rectified[i]))
        for (i in movAvg.indices) movAvgEntries.add(Entry(i.toFloat(), movAvg[i]))
        
        // Scale STFT entries to match the time-domain X-axis indices
        val scale = if (stftMnf.isNotEmpty()) rectified.size.toFloat() / stftMnf.size.toFloat() else 1f
        for (i in stftMnf.indices) stftEntries.add(Entry(i.toFloat() * scale, stftMnf[i]))

        val rectifiedSet = LineDataSet(rectifiedEntries, "Rectified").apply {
            color = Color.parseColor("#44BDC3C7")
            setDrawCircles(false)
            lineWidth = 0.5f
            setDrawValues(false)
        }

        val movAvgSet = LineDataSet(movAvgEntries, "Mov Avg").apply {
            color = Color.CYAN
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }

        val stftSet = LineDataSet(stftEntries, "TDMF (Hz)").apply {
            color = Color.RED
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT // Use Right axis for Hz
        }

        chart.data = LineData(rectifiedSet, movAvgSet, stftSet)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun setBulkData(chart: LineChart, values: FloatArray) {
        val entries = ArrayList<Entry>()
        for (i in values.indices) entries.add(Entry(i.toFloat(), values[i]))

        val set = LineDataSet(entries, "Data").apply {
            color = Color.GREEN
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(set)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun addEntry(chart: LineChart, value: Float) {
        val data = chart.data ?: return
        var set = data.getDataSetByIndex(0)
        if (set == null) {
            set = LineDataSet(null, "Data").apply {
                color = Color.GREEN
                setDrawCircles(false)
                lineWidth = 2f
                setDrawValues(false)
            }
            data.addDataSet(set)
        }
        data.addEntry(Entry(set.entryCount.toFloat(), value), 0)
        data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.setVisibleXRangeMaximum(1000f)
        chart.moveViewToX(data.entryCount.toFloat())
    }
}
