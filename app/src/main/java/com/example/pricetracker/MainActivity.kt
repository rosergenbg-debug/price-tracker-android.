package com.example.pricetracker
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
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
    private val OZ_TO_KG = 32.1507
    private var currentDays = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvGoldPrice = findViewById(R.id.tvGoldPrice); tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice); tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh); lineChart = findViewById(R.id.lineChart)
        
        setupChart()
        findViewById<Button>(R.id.btn1W).setOnClickListener { currentDays = 7; update() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { currentDays = 30; update() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { currentDays = 365; update() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { currentDays = 1825; update() }
        btnRefresh.setOnClickListener { update() }
        update()
    }

    private fun setupChart() {
        lineChart.description.isEnabled = false; lineChart.xAxis.textColor = Color.WHITE
        lineChart.axisLeft.textColor = Color.WHITE; lineChart.axisRight.isEnabled = false
        lineChart.legend.textColor = Color.WHITE
    }

    private fun update() {
        tvStatus.text = "Загрузка..."; btnRefresh.isEnabled = false
        val urlPrices = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        val urlHistory = "https://api.coingecko.com/api/v3/coins/tether-gold/market_chart?vs_currency=eur&days=$currentDays"

        client.newCall(Request.Builder().url(urlPrices).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvStatus.text = "Ошибка сети"; btnRefresh.isEnabled = true } }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val json = JSONObject(data)
                        tvBitcoinPrice.text = String.format("€%,.0f", json.getJSONObject("bitcoin").getDouble("eur"))
                        tvGoldPrice.text = String.format("€%,.0f", json.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG)
                        tvSilverPrice.text = String.format("€%,.0f", json.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG)
                        loadHistory(urlHistory)
                    } catch (e: Exception) { tvStatus.text = "Ошибка API"; btnRefresh.isEnabled = true }
                }
            }
        })
    }

    private fun loadHistory(url: String) {
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val prices = JSONObject(data).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until prices.length()) {
                            val point = prices.getJSONArray(i)
                            entries.add(Entry(point.getLong(0).toFloat(), (point.getDouble(1) * OZ_TO_KG).toFloat()))
                        }
                        displayChart(entries)
                        tvStatus.text = "Обновлено: $currentDays дн."; btnRefresh.isEnabled = true
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun displayChart(entries: ArrayList<Entry>) {
        val set = LineDataSet(entries, "Золото (€/КГ)").apply {
            color = Color.GOLD; setDrawCircles(false); setDrawValues(false); lineWidth = 2f
            setDrawFilled(true); fillColor = Color.GOLD; fillAlpha = 40
        }
        lineChart.data = LineData(set)
        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            val sdf = SimpleDateFormat("dd/MM", Locale.GERMANY)
            override fun getFormattedValue(v: Float) = sdf.format(Date(v.toLong()))
        }
        lineChart.invalidate()
    }
}
