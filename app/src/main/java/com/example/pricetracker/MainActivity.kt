package com.example.pricetracker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private val client = OkHttpClient()
    private val GOOGLE_PROXY_ID = "AKfycbx40Ydl0c0usqUbH9CGPeaaC8l6vb6Q3b7oxpjf9KMK3Jh3ZmmNtUNa3rdvBz3D20RM"
    private val googleScriptUrl = "https://script.google.com/macros/s/$GOOGLE_PROXY_ID/exec"

    private var activeAsset = "bitcoin"
    private var days = 1
    private var timeLeft = 80

    private var chart: LineChart? = null
    private var tvStatus: TextView? = null
    private var tvTimer: TextView? = null
    private var tvGoldPrice: TextView? = null
    private var tvSilverPrice: TextView? = null
    private var tvBitcoinPrice: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timeLeft > 0) timeLeft--
            tvTimer?.text = "Aktualisierung: ${timeLeft}s"
            if (timeLeft == 0) refreshAll()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        chart = findViewById(R.id.lineChart)
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)

        setupChart()

        // Кнопки активов (переключаем цвет полоски)
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { setAsset("tether-gold") }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { setAsset("kinesis-silver") }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { setAsset("bitcoin") }

        // Кнопки времени
        findViewById<Button>(R.id.btn1D).setOnClickListener { setDays(1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { setDays(7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { setDays(30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { setDays(365) }
        findViewById<Button>(R.id.btn3Y).setOnClickListener { setDays(1095) }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshAll() }

        loadPricesFromCache()
        setAsset("bitcoin")
        handler.post(timerRunnable)
        refreshAll()
    }

    private fun setupChart() {
        chart?.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE
            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false
            setNoDataText("Warten auf Daten...")
            setNoDataTextColor(Color.WHITE)
        }
    }

    private fun setAsset(asset: String) {
        activeAsset = asset
        showChartFromCache()
        fetchChartData() // Принудительно запрашиваем график при смене актива
    }

    private fun setDays(d: Int) {
        days = d
        showChartFromCache()
        fetchChartData() // Принудительно запрашиваем график при смене времени
    }

    private fun refreshAll() {
        timeLeft = 80
        tvStatus?.text = "Synchronisierung..."
        fetchPrices()
        fetchChartData()
    }

    private fun fetchPrices() {
        val url = "$googleScriptUrl?url=simple/price"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvStatus?.text = "Verbindungsfehler" }
            }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful && r.contains("bitcoin")) {
                    prefs.edit().putString("last_prices", r).apply()
                    runOnUiThread {
                        parseAndSetPrices(r)
                        tvStatus?.text = "Alle Daten sind aktuell"
                    }
                }
            }
        })
    }

    private fun fetchChartData() {
        val url = "$googleScriptUrl?url=market_chart&asset=$activeAsset&days=$days"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful && r.contains("prices")) {
                    prefs.edit().putString("chart_${activeAsset}_$days", r).apply()
                    runOnUiThread { showChartFromCache() }
                }
            }
        })
    }

    private fun loadPricesFromCache() {
        val saved = prefs.getString("last_prices", "")
        if (!saved.isNullOrEmpty()) parseAndSetPrices(saved)
    }

    private fun parseAndSetPrices(json: String) {
        try {
            val obj = JSONObject(json)
            val btc = obj.getJSONObject("bitcoin").getDouble("eur")
            val gold = obj.getJSONObject("tether-gold").getDouble("eur") * 32.1507 * 32.1507
            val silver = obj.getJSONObject("kinesis-silver").getDouble("eur") * 32.1507 * 32.1507

            tvBitcoinPrice?.text = String.format(Locale.GERMAN, "€%,.0f", btc)
            tvGoldPrice?.text = String.format(Locale.GERMAN, "€%,.0f", gold)
            tvSilverPrice?.text = String.format(Locale.GERMAN, "€%,.0f", silver)
        } catch (e: Exception) {}
    }

    private fun showChartFromCache() {
        val saved = prefs.getString("chart_${activeAsset}_$days", "")
        if (saved.isNullOrEmpty()) {
            chart?.clear()
            chart?.setNoDataText("Warten auf Google Server...")
            chart?.invalidate()
            return
        }

        try {
            val obj = JSONObject(saved)
            val prices = obj.getJSONArray("prices")
            val entries = ArrayList<Entry>()
            val dates = ArrayList<String>()

            // НЕМЕЦКИЙ КАЛЕНДАРЬ
            val sdf = when(days) {
                1 -> SimpleDateFormat("HH:mm", Locale.GERMAN)
                7, 30 -> SimpleDateFormat("dd. MMM", Locale.GERMAN)
                else -> SimpleDateFormat("MMM yy", Locale.GERMAN)
            }

            // МНОЖИТЕЛИ: Биткоин = 1.0, Металлы = 32.1507
            val mult = if (activeAsset == "bitcoin") 1.0 else 32.1507

            for (i in 0 until prices.length()) {
                val pt = prices.getJSONArray(i)
                entries.add(Entry(i.toFloat(), (pt.getDouble(1) * mult).toFloat()))
                dates.add(sdf.format(Date(pt.getLong(0))))
            }

            // ЦВЕТА
            val colorHex = when(activeAsset) {
                "tether-gold" -> "#FFD700"
                "kinesis-silver" -> "#C0C0C0"
                else -> "#F7931A" // Биткоин - оранжевый
            }
            val colorInt = Color.parseColor(colorHex)

            val dataSet = LineDataSet(entries, "").apply {
                color = colorInt
                setDrawCircles(false)
                setDrawValues(false)
                lineWidth = 2.0f
                setDrawFilled(true)
                fillColor = colorInt
                fillAlpha = 30
                mode = LineDataSet.Mode.LINEAR
            }

            chart?.xAxis?.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val idx = v.toInt()
                    return if (idx >= 0 && idx < dates.size) dates[idx] else ""
                }
            }

            // Подключаем маркер цены (флажок)
val lastPrice = entries.last().y; chart?.marker = CustomMarkerView(this@MainActivity, R.layout.marker_view, lastPrice); chart?.setDrawMarkers(true)
            chart?.setDrawMarkers(true)

            chart?.data = LineData(dataSet)
            chart?.invalidate()
        } catch (e: Exception) {
            chart?.clear()
        }
    }
}
