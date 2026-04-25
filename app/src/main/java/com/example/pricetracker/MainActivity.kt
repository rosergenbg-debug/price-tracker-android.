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
import com.github.mikephil.charting.formatter.ValueFormatter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var tvGoldPrice: TextView
    private lateinit var tvSilverPrice: TextView
    private lateinit var tvBitcoinPrice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button
    private lateinit var lineChart: LineChart
    private val client = OkHttpClient()
    private val OZ_TO_KG = 32.1507466
    private var currentDays = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)
        lineChart = findViewById(R.id.lineChart)

        setupChart()
        findViewById<Button>(R.id.btn1W).setOnClickListener { currentDays = 7; fetchPricesAndChart() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { currentDays = 30; fetchPricesAndChart() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { currentDays = 365; fetchPricesAndChart() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { currentDays = 1825; fetchPricesAndChart() }
        btnRefresh.setOnClickListener { fetchPricesAndChart() }
        fetchPricesAndChart()
    }

    private fun setupChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.setScaleEnabled(true)
        lineChart.setDrawGridBackground(false)
        lineChart.xAxis.textColor = Color.LTGRAY
        lineChart.axisLeft.textColor = Color.LTGRAY
        lineChart.axisRight.isEnabled = false
        lineChart.legend.textColor = Color.LTGRAY
    }

    private fun fetchPricesAndChart() {
        tvStatus.text = "Синхронизация..."
        btnRefresh.isEnabled = false
        lineChart.clear()
        
        val urlCurrent = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        val urlChartGold = "https://api.coingecko.com/api/v3/coins/tether-gold/market_chart?vs_currency=eur&days=$currentDays"

        client.newCall(Request.Builder().url(urlCurrent).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvStatus.text = "Ошибка сети"; btnRefresh.isEnabled = true } }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && data != null) {
                        try {
                            val json = JSONObject(data)
                            tvBitcoinPrice.text = String.format(Locale.GERMANY, "€ %,.2f", json.getJSONObject("bitcoin").getDouble("eur"))
                            tvGoldPrice.text = String.format(Locale.GERMANY, "€ %,.0f", json.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG)
                            tvSilverPrice.text = String.format(Locale.GERMANY, "€ %,.2f", json.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG)
                            fetchChartData(urlChartGold)
                        } catch (e: Exception) { tvStatus.text = "Ошибка данных"; btnRefresh.isEnabled = true }
                    }
                }
            }
        })
    }

    private fun fetchChartData(url: String) {
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvStatus.text = "Ошибка графика"; btnRefresh.isEnabled = true } }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && data != null) {
                        try {
                            val pricesArray = JSONObject(data).getJSONArray("prices")
                            val entries = ArrayList<Entry>()
                            for (i in 0 until pricesArray.length()) {
                                val point = pricesArray.getJSONArray(i)
                                entries.add(Entry(point.getLong(0).toFloat(), (point.getDouble(1) * OZ_TO_KG).toFloat()))
                            }
                            drawChart(entries)
                            tvStatus.text = "График золота: $currentDays дней"
                        } catch (e: Exception) { tvStatus.text = "Ошибка JSON графика" }
                    }
                    btnRefresh.isEnabled = true
                }
            }
        })
    }

    private fun drawChart(entries: ArrayList<Entry>) {
        val dataSet = LineDataSet(entries, "Золото (Евро/КГ)").apply {
            color = Color.parseColor("#FFD700")
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#FFD700")
            fillAlpha = 50
        }
        lineChart.data = LineData(dataSet)
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val formatLong = SimpleDateFormat("dd/MM/yy", Locale.GERMANY)
            private val formatShort = SimpleDateFormat("dd/MM", Locale.GERMANY)
            
            override fun getFormattedValue(value: Float): String {
                val date = Date(value.toLong())
                return if (currentDays > 60) formatLong.format(date) else formatShort.format(date)
            }
        }
        lineChart.invalidate()
    }
}
