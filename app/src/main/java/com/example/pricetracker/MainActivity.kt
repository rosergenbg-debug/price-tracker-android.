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

    private val proxyBaseUrl =
        "https://script.google.com/macros/s/AKfycbzhen0cxlYd3neWueBzzqQAQRFEO5dyKrCv7-enW0mG6ZMCxvbCStZmtpiPcRySltON/exec"
    private val coinbaseBtcUrl = "https://api.coinbase.com/v2/prices/BTC-EUR/spot"
    private val stooqQuoteBaseUrl = "https://stooq.com/q/l/"
    private val troyOuncesPerKilogram = 32.1507466
    private val lastPricesKey = "last_prices_proxy_v1"
    private val refreshIntervalSeconds = 80
    private val fallbackPriceLock = Any()

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
        val url = "$proxyBaseUrl?action=prices"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    parseAndSetPrices(json)
                    prefs.edit().putString(lastPricesKey, json).apply()
                    setStatus("Daten aktualisiert")
                } catch (e: Exception) {
                    fetchFallbackPrices()
                }
            }

            override fun onError(message: String) {
                fetchFallbackPrices()
            }
        })
    }

    private fun fetchFallbackPrices() {
        val values = mutableMapOf<String, Double>()
        var finished = 0

        fun remember(key: String, value: Double?) {
            synchronized(fallbackPriceLock) {
                if (value != null) values[key] = value
                finished++

                if (finished == 4) {
                    val btc = values["btc"]
                    val xauUsd = values["xauusd"]
                    val xagUsd = values["xagusd"]
                    val eurUsd = values["eurusd"]

                    if (btc != null && xauUsd != null && xagUsd != null && eurUsd != null && eurUsd > 0.0) {
                        val json = JSONObject()
                            .put("ok", true)
                            .put("cache", "android_fallback")
                            .put("bitcoin", JSONObject().put("eur", btc).put("unit", "EUR"))
                            .put("gold", JSONObject().put("eur", xauUsd / eurUsd * troyOuncesPerKilogram).put("unit", "EUR/kg"))
                            .put("silver", JSONObject().put("eur", xagUsd / eurUsd * troyOuncesPerKilogram).put("unit", "EUR/kg"))
                            .toString()

                        parseAndSetPrices(json)
                        prefs.edit().putString(lastPricesKey, json).apply()
                        setStatus("Reserve-Daten aktualisiert")
                    } else {
                        setStatus("Server wartet auf Daten")
                    }
                }
            }
        }

        getJson(coinbaseBtcUrl, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    remember("btc", JSONObject(json).getJSONObject("data").getDouble("amount"))
                } catch (e: Exception) {
                    remember("btc", null)
                }
            }

            override fun onError(message: String) {
                remember("btc", null)
            }
        })
        fetchStooqClose("xauusd") { remember("xauusd", it) }
        fetchStooqClose("xagusd") { remember("xagusd", it) }
        fetchStooqClose("eurusd") { remember("eurusd", it) }
    }

    private fun fetchStooqClose(symbol: String, callback: (Double?) -> Unit) {
        val url = "$stooqQuoteBaseUrl?s=$symbol&f=sd2t2ohlcv&h&e=csv"
        getText(url, object : TextCallback {
            override fun onSuccess(text: String) {
                callback(parseStooqClose(text))
            }

            override fun onError(message: String) {
                callback(null)
            }
        })
    }

    private fun fetchChartData(asset: String, selectedDays: Int) {
        val url = "$proxyBaseUrl?action=chart&asset=$asset&days=$selectedDays"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    val obj = JSONObject(json)
                    ensureOk(obj)

                    val prices = obj.getJSONArray("prices")
                    if (prices.length() < 2) throw IllegalArgumentException("Too few chart points")

                    prefs.edit().putString(chartKey(asset, selectedDays), json).apply()
                    if (asset == activeAsset && selectedDays == days) {
                        runOnUiThread {
                            showChartFromJson(asset, selectedDays, json)
                            setStatus("Daten aktualisiert")
                        }
                    }
                } catch (e: Exception) {
                    if (asset == activeAsset && selectedDays == days) showFallbackChart(asset, selectedDays)
                }
            }

            override fun onError(message: String) {
                if (asset == activeAsset && selectedDays == days) showFallbackChart(asset, selectedDays)
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

    private fun getText(url: String, callback: TextCallback) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/plain,text/csv,*/*")
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
                    callback.onSuccess(body)
                }
            }
        })
    }

    private fun loadPricesFromCache() {
        val saved = prefs.getString(lastPricesKey, "")
        if (!saved.isNullOrEmpty()) {
            try {
                parseAndSetPrices(saved)
            } catch (e: Exception) {
                prefs.edit().remove(lastPricesKey).apply()
            }
        }
    }

    private fun parseAndSetPrices(json: String) {
        val obj = JSONObject(json)
        ensureOk(obj)

        runOnUiThread {
            obj.optJSONObject("bitcoin")?.let {
                tvBitcoinPrice?.text = formatPrice("bitcoin", it.getDouble("eur"))
            }
            obj.optJSONObject("gold")?.let {
                tvGoldPrice?.text = formatPrice("gold", metalKgPrice(it))
            }
            obj.optJSONObject("silver")?.let {
                tvSilverPrice?.text = formatPrice("silver", metalKgPrice(it))
            }
        }
    }

    private fun ensureOk(obj: JSONObject) {
        if (obj.has("ok") && !obj.optBoolean("ok", false)) {
            throw IllegalArgumentException(obj.optString("error", "Proxy error"))
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
            entries.add(Entry(i.toFloat(), point.getDouble(1).toFloat()))
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

    private fun showFallbackChart(asset: String, selectedDays: Int) {
        val price = latestCachedPrice(asset)
        if (price == null) {
            chart?.clear()
            chart?.setNoDataText("Warten auf Daten...")
            chart?.invalidate()
            setStatus("Server wartet auf Daten")
            return
        }

        val now = System.currentTimeMillis()
        val span = TimeUnit.DAYS.toMillis(selectedDays.toLong())
        val points = ArrayList<Entry>()
        val dates = ArrayList<String>()
        val sdf = when (selectedDays) {
            1 -> SimpleDateFormat("HH:mm", Locale.GERMAN)
            7, 30 -> SimpleDateFormat("dd. MMM", Locale.GERMAN)
            else -> SimpleDateFormat("MMM yy", Locale.GERMAN)
        }

        for (i in 0 until 8) {
            val time = now - span + (span / 7L * i)
            points.add(Entry(i.toFloat(), price.toFloat()))
            dates.add(sdf.format(Date(time)))
        }

        val colorHex = colorFor(asset)
        val dataSet = LineDataSet(points, "").apply {
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
        chart?.marker = CustomMarkerView(this, R.layout.marker_view, points.first().y, unitSuffixFor(asset))
        chart?.data = LineData(dataSet)
        chart?.invalidate()
        setStatus("Reserve-Chart angezeigt")
    }

    private fun colorFor(asset: String): String {
        return when (asset) {
            "gold" -> "#FFD700"
            "silver" -> "#C0C0C0"
            else -> "#F7931A"
        }
    }

    private fun chartKey(asset: String, selectedDays: Int): String = "chart_proxy_${asset}_$selectedDays"

    private fun metalKgPrice(obj: JSONObject): Double {
        if (obj.has("eurPerKg")) return obj.getDouble("eurPerKg")
        return obj.getDouble("eur")
    }

    private fun latestCachedPrice(asset: String): Double? {
        val saved = prefs.getString(lastPricesKey, "") ?: return null
        if (saved.isEmpty()) return null

        return try {
            val obj = JSONObject(saved)
            when (asset) {
                "gold" -> obj.optJSONObject("gold")?.let { metalKgPrice(it) }
                "silver" -> obj.optJSONObject("silver")?.let { metalKgPrice(it) }
                else -> obj.optJSONObject("bitcoin")?.getDouble("eur")
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStooqClose(csv: String): Double? {
        val lines = csv.trim().lines()
        if (lines.size < 2) return null

        val columns = lines[1].split(",")
        if (columns.size < 7) return null

        return columns[6].toDoubleOrNull()
    }

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

    private interface TextCallback {
        fun onSuccess(text: String)
        fun onError(message: String)
    }
}
