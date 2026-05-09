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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val apiBaseUrl = "https://api.coingecko.com/api/v3"
    private val priceIds = "bitcoin,tether-gold,kinesis-silver"
    private val troyOuncesPerKilogram = 32.1507466
    private val refreshIntervalSeconds = 80

    private var activeAsset = "bitcoin"
    private var days = 1
    private var timeLeft = refreshIntervalSeconds

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
        setupButtons()

        loadPricesFromCache()
        updateTimeButtons()
        setAsset("bitcoin")
        handler.post(timerRunnable)
        refreshAll()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        client.dispatcher.cancelAll()
        super.onDestroy()
    }

    private fun setupButtons() {
        findViewById<LinearLayout>(R.id.layoutGold)?.setOnClickListener { setAsset("gold") }
        findViewById<LinearLayout>(R.id.layoutSilver)?.setOnClickListener { setAsset("silver") }
        findViewById<LinearLayout>(R.id.layoutBtc)?.setOnClickListener { setAsset("bitcoin") }

        findViewById<Button>(R.id.btn1D)?.setOnClickListener { setDays(1) }
        findViewById<Button>(R.id.btn1W)?.setOnClickListener { setDays(7) }
        findViewById<Button>(R.id.btn1M)?.setOnClickListener { setDays(30) }
        findViewById<Button>(R.id.btn1Y)?.setOnClickListener { setDays(365) }
        findViewById<Button>(R.id.btn3Y)?.setOnClickListener { setDays(1095) }
        findViewById<Button>(R.id.btnRefresh)?.setOnClickListener { refreshAll() }
    }

    private fun setupChart() {
        chart?.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE
            xAxis.granularity = 1f
            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false
            setNoDataText("Warten auf Daten...")
            setNoDataTextColor(Color.WHITE)
        }
    }

    private fun updateTimeButtons() {
        val active = Color.parseColor("#4CAF50")
        val inactive = Color.parseColor("#AB47BC")
        findViewById<Button>(R.id.btn1D)?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (days == 1) active else inactive))
        findViewById<Button>(R.id.btn1W)?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (days == 7) active else inactive))
        findViewById<Button>(R.id.btn1M)?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (days == 30) active else inactive))
        findViewById<Button>(R.id.btn1Y)?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (days == 365) active else inactive))
        findViewById<Button>(R.id.btn3Y)?.setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (days == 1095) active else inactive))
    }

    private fun setAsset(asset: String) {
        activeAsset = asset
        showChartFromCache(asset, days)
        fetchChartData(asset, days)
    }

    private fun setDays(newDays: Int) {
        days = newDays
        updateTimeButtons()
        showChartFromCache(activeAsset, days)
        fetchChartData(activeAsset, days)
    }

    private fun refreshAll() {
        timeLeft = refreshIntervalSeconds
        setStatus("Synchronisierung...")
        fetchPrices()
        fetchChartData(activeAsset, days)
    }

    private fun fetchPrices() {
        val url = "$apiBaseUrl/simple/price?ids=$priceIds&vs_currencies=eur"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    parseAndSetPrices(json)
                    prefs.edit().putString("last_prices", json).apply()
                    setStatus("Daten aktualisiert")
                } catch (e: Exception) {
                    setStatus("Preisformat unerwartet")
                }
            }

            override fun onError(message: String) {
                setStatus(message)
            }
        })
    }

    private fun fetchChartData(asset: String, selectedDays: Int) {
        val coinId = coinIdFor(asset)
        val url = "$apiBaseUrl/coins/$coinId/market_chart?vs_currency=eur&days=$selectedDays"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    val prices = JSONObject(json).getJSONArray("prices")
                    if (prices.length() < 2) throw IllegalArgumentException("Too few chart points")

                    prefs.edit().putString(chartKey(asset, selectedDays), json).apply()
                    if (asset == activeAsset && selectedDays == days) {
                        runOnUiThread {
                            showChartFromJson(asset, selectedDays, json)
                            setStatus("Daten aktualisiert")
                        }
                    }
                } catch (e: Exception) {
                    if (asset == activeAsset && selectedDays == days) setStatus("Chartformat unerwartet")
                }
            }

            override fun onError(message: String) {
                if (asset == activeAsset && selectedDays == days) setStatus(message)
            }
        })
    }

    private fun getJson(url: String, callback: JsonCallback) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PriceTrackerAndroid/1.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Verbindungsfehler")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        callback.onError("API Fehler ${it.code}")
                        return
                    }
                    if (!body.trimStart().startsWith("{")) {
                        callback.onError("Keine JSON Antwort")
                        return
                    }
                    callback.onSuccess(body)
                }
            }
        })
    }

    private fun loadPricesFromCache() {
        val saved = prefs.getString("last_prices", "")
        if (!saved.isNullOrEmpty()) {
            try {
                parseAndSetPrices(saved)
            } catch (e: Exception) {
                prefs.edit().remove("last_prices").apply()
            }
        }
    }

    private fun parseAndSetPrices(json: String) {
        val obj = JSONObject(json)
        val btc = obj.getJSONObject("bitcoin").getDouble("eur")
        val gold = ouncePriceToKilogram(obj.getJSONObject("tether-gold").getDouble("eur"))
        val silver = ouncePriceToKilogram(obj.getJSONObject("kinesis-silver").getDouble("eur"))

        runOnUiThread {
            tvBitcoinPrice?.text = formatPrice("bitcoin", btc)
            tvGoldPrice?.text = formatPrice("gold", gold)
            tvSilverPrice?.text = formatPrice("silver", silver)
        }
    }

    private fun showChartFromCache(asset: String, selectedDays: Int) {
        val saved = prefs.getString(chartKey(asset, selectedDays), "")
        if (saved.isNullOrEmpty()) {
            chart?.clear()
            chart?.setNoDataText("Warten auf Daten...")
            chart?.invalidate()
            return
        }

        try {
            showChartFromJson(asset, selectedDays, saved)
        } catch (e: Exception) {
            prefs.edit().remove(chartKey(asset, selectedDays)).apply()
            chart?.clear()
            chart?.setNoDataText("Keine gueltigen Daten")
            chart?.invalidate()
        }
    }

    private fun showChartFromJson(asset: String, selectedDays: Int, json: String) {
        val prices = JSONObject(json).getJSONArray("prices")
        if (prices.length() < 2) throw IllegalArgumentException("Too few chart points")

        val entries = ArrayList<Entry>()
        val dates = ArrayList<String>()
        val sdf = when (selectedDays) {
            1 -> SimpleDateFormat("HH:mm", Locale.GERMAN)
            7, 30 -> SimpleDateFormat("dd. MMM", Locale.GERMAN)
            else -> SimpleDateFormat("MMM yy", Locale.GERMAN)
        }

        for (i in 0 until prices.length()) {
            val point = prices.getJSONArray(i)
            entries.add(Entry(i.toFloat(), displayPriceFor(asset, point.getDouble(1)).toFloat()))
            dates.add(sdf.format(Date(point.getLong(0))))
        }

        val colorHex = colorFor(asset)
        val dataSet = LineDataSet(entries, "").apply {
            color = Color.parseColor(colorHex)
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2.2f
            setDrawFilled(true)
            fillColor = Color.parseColor(colorHex)
            fillAlpha = 35
        }

        chart?.xAxis?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dates.size) dates[index] else ""
            }
        }
        chart?.marker = CustomMarkerView(this, R.layout.marker_view, entries.first().y, unitSuffixFor(asset))
        chart?.data = LineData(dataSet)
        chart?.invalidate()
    }

    private fun coinIdFor(asset: String): String {
        return when (asset) {
            "gold" -> "tether-gold"
            "silver" -> "kinesis-silver"
            else -> "bitcoin"
        }
    }

    private fun colorFor(asset: String): String {
        return when (asset) {
            "gold" -> "#FFD700"
            "silver" -> "#C0C0C0"
            else -> "#F7931A"
        }
    }

    private fun chartKey(asset: String, selectedDays: Int): String = "chart_${asset}_$selectedDays"

    private fun displayPriceFor(asset: String, value: Double): Double {
        return when (asset) {
            "gold", "silver" -> ouncePriceToKilogram(value)
            else -> value
        }
    }

    private fun ouncePriceToKilogram(value: Double): Double = value * troyOuncesPerKilogram

    private fun unitSuffixFor(asset: String): String {
        return when (asset) {
            "gold", "silver" -> "/kg"
            else -> ""
        }
    }

    private fun formatPrice(asset: String, value: Double): String {
        val suffix = unitSuffixFor(asset)
        return if (value >= 1000) {
            String.format(Locale.GERMAN, "EUR %,.0f%s", value, suffix)
        } else {
            String.format(Locale.GERMAN, "EUR %,.2f%s", value, suffix)
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { tvStatus?.text = message }
    }

    private interface JsonCallback {
        fun onSuccess(json: String)
        fun onError(message: String)
    }
}
