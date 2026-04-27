package com.example.pricetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var lineChart: LineChart? = null
    private var tvGoldPrice: TextView? = null
    private var tvSilverPrice: TextView? = null
    private var tvBitcoinPrice: TextView? = null
    private var tvStatus: TextView? = null
    
    private val scriptUrl = "https://script.google.com/macros/s/AKfycbzH0t-zQzLuMbZs_mlPMlicir7BZNumgVOMkx7yjsHgN6qLNz17KxFGr9znketNLxjL/exec"
    private var currentDays = "1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        lineChart = findViewById(R.id.lineChart)
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvStatus = findViewById(R.id.tvStatus)
        
        lineChart?.marker = CustomMarkerView(this, R.layout.marker_view)
        lineChart?.description?.isEnabled = false
        lineChart?.legend?.textColor = Color.WHITE
        lineChart?.xAxis?.textColor = Color.WHITE
        lineChart?.axisLeft?.textColor = Color.WHITE
        lineChart?.axisRight?.isEnabled = false
        
        val btn1D = findViewById<Button>(R.id.btn1D)
        val btn1W = findViewById<Button>(R.id.btn1W)
        val btn1M = findViewById<Button>(R.id.btn1M)
        val btn1Y = findViewById<Button>(R.id.btn1Y)
        val btn3Y = findViewById<Button>(R.id.btn3Y)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)

        val timeMap = mapOf(btn1D to "1", btn1W to "7", btn1M to "30", btn1Y to "365", btn3Y to "1095")

        timeMap.forEach { (button, days) ->
            button?.setOnClickListener { clickedBtn ->
                timeMap.keys.forEach { it?.setBackgroundColor(Color.parseColor("#AB47BC")) }
                clickedBtn.setBackgroundColor(Color.parseColor("#4CAF50"))
                currentDays = days
                tvStatus?.text = "Lade Daten..."
                fetchData(currentDays)
            }
        }

        btnRefresh?.setOnClickListener {
            tvStatus?.text = "Aktualisiere..."
            fetchData(currentDays)
        }
        
        // Стартовая загрузка
        fetchData(currentDays)
    }
    
    private fun fetchData(days: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Запрашиваем текущие цены
                val priceUrl = URL("$scriptUrl?url=simple/price")
                val priceConn = priceUrl.openConnection() as HttpURLConnection
                val priceResponse = priceConn.inputStream.bufferedReader().readText()
                val priceJson = JSONObject(priceResponse)
                
                val btc = priceJson.getJSONObject("bitcoin").getDouble("eur")
                val goldOz = priceJson.getJSONObject("tether-gold").getDouble("eur")
                val silverOz = priceJson.getJSONObject("kinesis-silver").getDouble("eur")
                
                // Перевод унций в кг
                val multiplier = 32.1507
                val goldKg = goldOz * multiplier
                val silverKg = silverOz * multiplier

                // 2. Запрашиваем данные для графика (по умолчанию биткоин)
                val chartUrl = URL("$scriptUrl?asset=bitcoin&days=$days")
                val chartConn = chartUrl.openConnection() as HttpURLConnection
                val chartResponse = chartConn.inputStream.bufferedReader().readText()
                val chartJson = JSONObject(chartResponse)
                val pricesArray = chartJson.getJSONArray("prices")

                val entries = ArrayList<Entry>()
                for (i in 0 until pricesArray.length()) {
                    val point = pricesArray.getJSONArray(i)
                    entries.add(Entry(i.toFloat(), point.getDouble(1).toFloat()))
                }

                val dataSet = LineDataSet(entries, "Bitcoin (€)")
                dataSet.color = Color.parseColor("#F7931A")
                dataSet.setDrawCircles(false)
                dataSet.setDrawValues(false)
                val lineData = LineData(dataSet)

                // 3. Обновляем интерфейс
                withContext(Dispatchers.Main) {
                    tvBitcoinPrice?.text = String.format(Locale.GERMAN, "€%,.0f", btc)
                    tvGoldPrice?.text = String.format(Locale.GERMAN, "€%,.0f", goldKg)
                    tvSilverPrice?.text = String.format(Locale.GERMAN, "€%,.0f", silverKg)
                    
                    lineChart?.data = lineData
                    lineChart?.invalidate()
                    tvStatus?.text = "Aktualisiert"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "Fehler beim Laden"
                }
            }
        }
    }
}
