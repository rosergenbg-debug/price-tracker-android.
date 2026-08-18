package com.example.pricetracker

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap
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
    private val yahooChartBaseUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
    private val binanceSpotBaseUrl = "https://data-api.binance.vision/api/v3"
    private val troyOuncesPerKilogram = 32.1507466
    private val lastPricesKey = "last_prices_proxy_v1"
    private val lastPumpPriceKey = "last_pump_eur_v1"
    private val refreshIntervalSeconds = 80
    private val scrollHistoryDays = 90
    private val historyRetentionDays = 1095
    private val fallbackPriceLock = Any()
    private val historyLock = Any()
    private val assets = listOf("gold", "silver", "bitcoin", "pump")

    private var activeAsset = "bitcoin"
    private var days = 1
    private var timeLeft = refreshIntervalSeconds
    private var showAllCharts = false

    private var chart: LineChart? = null
    private val overviewCharts = mutableMapOf<String, LineChart>()
    private var allChartsScroll: ScrollView? = null
    private var btnShowAll: Button? = null
    private var tvStatus: TextView? = null
    private var tvTimer: TextView? = null
    private var tvGoldPrice: TextView? = null
    private var tvSilverPrice: TextView? = null
    private var tvBitcoinPrice: TextView? = null
    private var tvPumpPrice: TextView? = null

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
        overviewCharts["gold"] = findViewById(R.id.chartGold)
        overviewCharts["silver"] = findViewById(R.id.chartSilver)
        overviewCharts["bitcoin"] = findViewById(R.id.chartBitcoin)
        overviewCharts["pump"] = findViewById(R.id.chartPump)
        allChartsScroll = findViewById(R.id.allChartsScroll)
        btnShowAll = findViewById(R.id.btnShowAll)
        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvPumpPrice = findViewById(R.id.tvPumpPrice)
        findViewById<TextView>(R.id.tvVersion)?.text = "PRICE MONITOR · VERSION ${BuildConfig.VERSION_NAME}"

        setupChart(chart)
        overviewCharts.values.forEach { setupChart(it) }
        setupButtons()

        loadPricesFromCache()
        updateTimeButtons()
        setAsset("bitcoin")
        handler.post(timerRunnable)
        fetchPrices()
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
        findViewById<LinearLayout>(R.id.layoutPump)?.setOnClickListener { setAsset("pump") }

        findViewById<Button>(R.id.btn1D)?.setOnClickListener { setDays(1) }
        findViewById<Button>(R.id.btn1W)?.setOnClickListener { setDays(7) }
        findViewById<Button>(R.id.btn1M)?.setOnClickListener { setDays(30) }
        findViewById<Button>(R.id.btn1Y)?.setOnClickListener { setDays(365) }
        findViewById<Button>(R.id.btn3Y)?.setOnClickListener { setDays(1095) }
        btnShowAll?.setOnClickListener { toggleAllCharts() }
        findViewById<Button>(R.id.btnRefresh)?.setOnClickListener { refreshAll(forceChart = true) }
    }

    private fun setupChart(target: LineChart?) {
        target?.apply {
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE
            xAxis.granularity = 1f
            axisLeft.textColor = Color.WHITE
            axisRight.isEnabled = false
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true
            setAutoScaleMinMaxEnabled(true)
            isDragDecelerationEnabled = true
            dragDecelerationFrictionCoef = 0.9f
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
        showAllCharts = false
        updateChartMode()
        chart?.clear()
        showChartFromCache(asset, days)
        fetchChartData(asset, days)
    }

    private fun setDays(newDays: Int) {
        val returnToLatest = days == newDays
        days = newDays
        updateTimeButtons()
        visibleAssets().forEach { asset ->
            visibleChartTargets(asset).forEach { it.clear() }
            showChartFromCache(asset, days)
            fetchChartData(asset, days)
        }
        if (returnToLatest) moveVisibleChartsToLatest()
    }

    private fun refreshAll(forceChart: Boolean = false) {
        timeLeft = refreshIntervalSeconds
        setStatus("Synchronisierung...")
        fetchPrices()
        visibleAssets().forEach { fetchChartData(it, days, forceChart) }
    }

    private fun toggleAllCharts() {
        showAllCharts = !showAllCharts
        updateChartMode()
        visibleAssets().forEach { asset ->
            visibleChartTargets(asset).forEach { it.clear() }
            showChartFromCache(asset, days)
            fetchChartData(asset, days)
        }
    }

    private fun updateChartMode() {
        chart?.visibility = if (showAllCharts) View.GONE else View.VISIBLE
        allChartsScroll?.visibility = if (showAllCharts) View.VISIBLE else View.GONE
        btnShowAll?.text = if (showAllCharts) "EINZELCHART" else "ALLE 4 CHARTS"
    }

    private fun visibleAssets(): List<String> = if (showAllCharts) assets else listOf(activeAsset)

    private fun moveVisibleChartsToLatest() {
        visibleAssets().forEach { asset ->
            visibleChartTargets(asset).forEach { target ->
                target.data?.let { target.moveViewToX(it.xMax) }
            }
        }
    }

    private fun fetchPrices() {
        fetchPumpPrice()
        fetchFallbackPrices { success ->
            if (!success) fetchProxyPrices()
        }
    }

    private fun fetchPumpPrice() {
        val lock = Any()
        var pumpUsdt: Double? = null
        var eurUsdt: Double? = null
        var completed = 0

        fun remember(isPump: Boolean, value: Double?) {
            synchronized(lock) {
                if (isPump) pumpUsdt = value else eurUsdt = value
                completed++
                if (completed == 2) {
                    val pump = pumpUsdt
                    val eur = eurUsdt
                    if (pump != null && eur != null && eur > 0.0) {
                        val pumpEur = pump / eur
                        prefs.edit().putLong(lastPumpPriceKey, java.lang.Double.doubleToRawLongBits(pumpEur)).apply()
                        runOnUiThread { tvPumpPrice?.text = formatPrice("pump", pumpEur) }
                    }
                }
            }
        }

        fetchBinanceTicker("PUMPUSDT") { remember(true, it) }
        fetchBinanceTicker("EURUSDT") { remember(false, it) }
    }

    private fun fetchBinanceTicker(symbol: String, callback: (Double?) -> Unit) {
        val url = "$binanceSpotBaseUrl/ticker/price?symbol=$symbol"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                callback(JSONObject(json).optString("price").toDoubleOrNull())
            }

            override fun onError(message: String) {
                callback(null)
            }
        })
    }

    private fun fetchProxyPrices() {
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

    private fun fetchFallbackPrices(onFinished: ((Boolean) -> Unit)? = null) {
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
                        setStatus("Daten aktualisiert")
                        onFinished?.invoke(true)
                    } else {
                        setStatus("Server wartet auf Daten")
                        onFinished?.invoke(false)
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

    private fun fetchChartData(asset: String, selectedDays: Int, force: Boolean = false) {
        if (!force && isChartCacheFresh(asset, selectedDays)) {
            setStatus("Chart aus Speicher")
            return
        }

        fetchFallbackChart(asset, selectedDays)
    }

    private fun getJson(url: String, callback: JsonCallback) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "PriceTrackerAndroid/${BuildConfig.VERSION_NAME}")
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
                    val trimmed = body.trimStart()
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
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
            .header("User-Agent", "PriceTrackerAndroid/${BuildConfig.VERSION_NAME}")
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

        latestCachedPumpPrice()?.let { tvPumpPrice?.text = formatPrice("pump", it) }
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

    private fun showChartFromCache(asset: String, selectedDays: Int): Boolean {
        val saved = cachedHistory(asset, selectedDays)
        val targets = visibleChartTargets(asset)
        if (saved.isNullOrEmpty()) {
            targets.forEach { target ->
                target.clear()
                target.setNoDataText("Warten auf Daten...")
                target.invalidate()
            }
            return false
        }

        try {
            targets.forEach { showChartFromJson(asset, selectedDays, saved, it) }
            return true
        } catch (e: Exception) {
            prefs.edit()
                .remove(chartHistoryKey(asset))
                .remove(chartKey(asset, selectedDays))
                .apply()
            targets.forEach { target ->
                target.clear()
                target.setNoDataText("Keine gueltigen Daten")
                target.invalidate()
            }
            return false
        }
    }

    private fun showChartFromJson(asset: String, selectedDays: Int, json: String, target: LineChart) {
        val prices = JSONObject(json).getJSONArray("prices")
        if (prices.length() < 2) throw IllegalArgumentException("Too few chart points")

        val entries = ArrayList<Entry>()
        val firstTime = prices.getJSONArray(0).getLong(0)
        val lastTime = prices.getJSONArray(prices.length() - 1).getLong(0)
        val sdf = when (selectedDays) {
            1 -> SimpleDateFormat("HH:mm", Locale.GERMAN)
            7, 30 -> SimpleDateFormat("dd. MMM", Locale.GERMAN)
            else -> SimpleDateFormat("MMM yy", Locale.GERMAN)
        }

        for (i in 0 until prices.length()) {
            val point = prices.getJSONArray(i)
            val hoursFromStart = (point.getLong(0) - firstTime) / TimeUnit.HOURS.toMillis(1).toFloat()
            entries.add(Entry(hoursFromStart, point.getDouble(1).toFloat()))
        }

        val selectedCutoff = lastTime - TimeUnit.DAYS.toMillis(selectedDays.toLong())
        var selectedBasePrice = entries.first().y
        for (i in 0 until prices.length()) {
            if (prices.getJSONArray(i).getLong(0) >= selectedCutoff) {
                selectedBasePrice = entries[i].y
                break
            }
        }

        val oldData = target.data
        val oldLowestVisibleX = target.lowestVisibleX
        val wasAtLatest = oldData == null ||
            oldData.entryCount == 0 ||
            oldData.xMax - target.highestVisibleX <= 2f

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

        target.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val time = firstTime + (value * TimeUnit.HOURS.toMillis(1)).toLong()
                return sdf.format(Date(time))
            }
        }
        target.xAxis.granularity = axisGranularityHours(selectedDays)
        target.xAxis.setLabelCount(5, false)
        target.marker = CustomMarkerView(
            this,
            R.layout.marker_view,
            selectedBasePrice,
            unitSuffixFor(asset),
            firstTime,
            selectedDays
        )
        target.data = LineData(dataSet)
        target.notifyDataSetChanged()
        target.setVisibleXRangeMaximum(TimeUnit.DAYS.toHours(selectedDays.toLong()).toFloat())
        if (wasAtLatest) {
            target.moveViewToX(entries.last().x)
        } else {
            target.moveViewToX(oldLowestVisibleX.coerceIn(entries.first().x, entries.last().x))
        }
        target.invalidate()
    }

    private fun axisGranularityHours(selectedDays: Int): Float {
        return when (selectedDays) {
            1 -> 2f
            7 -> 12f
            30 -> 24f
            365 -> 24f * 30f
            else -> 24f * 90f
        }
    }

    private fun visibleChartTargets(asset: String): List<LineChart> {
        return if (showAllCharts) {
            listOfNotNull(overviewCharts[asset])
        } else if (asset == activeAsset) {
            listOfNotNull(chart)
        } else {
            emptyList()
        }
    }

    private fun renderChartIfVisible(asset: String, selectedDays: Int, json: String) {
        if (!isCurrentChart(asset, selectedDays)) return
        runOnUiThread {
            visibleChartTargets(asset).forEach { showChartFromJson(asset, selectedDays, json, it) }
        }
    }

    private fun fetchFallbackChart(asset: String, selectedDays: Int) {
        when (asset) {
            "bitcoin" -> fetchFallbackBitcoinChart(selectedDays)
            "pump" -> fetchPumpChart(selectedDays)
            else -> fetchFallbackMetalChart(asset, selectedDays)
        }
    }

    private fun fetchFallbackBitcoinChart(selectedDays: Int) {
        val url = "$yahooChartBaseUrl/BTC-EUR?range=${yahooRange(selectedDays)}&interval=${yahooInterval(selectedDays)}"
        getJson(url, object : JsonCallback {
            override fun onSuccess(json: String) {
                try {
                    val chartJson = yahooBitcoinChartToChartJson(selectedDays, json)
                    cacheChartJson("bitcoin", selectedDays, chartJson)
                    setStatus("Chart aktualisiert")
                } catch (e: Exception) {
                    if (isCurrentChart("bitcoin", selectedDays)) showFlatFallbackChart("bitcoin", selectedDays)
                }
            }

            override fun onError(message: String) {
                if (isCurrentChart("bitcoin", selectedDays)) showFlatFallbackChart("bitcoin", selectedDays)
            }
        })
    }

    private fun fetchFallbackMetalChart(asset: String, selectedDays: Int) {
        val metalSymbol = if (asset == "gold") "GC%3DF" else "SI%3DF"
        val range = yahooRange(selectedDays)
        val interval = yahooInterval(selectedDays)
        val metalUrl = "$yahooChartBaseUrl/$metalSymbol?range=$range&interval=$interval"
        val fxUrl = "$yahooChartBaseUrl/EURUSD%3DX?range=$range&interval=$interval"

        val lock = Any()
        var metalJson: String? = null
        var fxJson: String? = null
        var failures = 0

        fun finishIfReady() {
            synchronized(lock) {
                if (failures > 0) {
                    if (isCurrentChart(asset, selectedDays)) showFlatFallbackChart(asset, selectedDays)
                    return
                }

                val metal = metalJson
                val fx = fxJson
                if (metal != null && fx != null) {
                    try {
                        val chartJson = yahooMetalChartToChartJson(asset, selectedDays, metal, fx)
                        cacheChartJson(asset, selectedDays, chartJson)
                        setStatus("Chart aktualisiert")
                    } catch (e: Exception) {
                        if (isCurrentChart(asset, selectedDays)) showFlatFallbackChart(asset, selectedDays)
                    }
                }
            }
        }

        getJson(metalUrl, object : JsonCallback {
            override fun onSuccess(json: String) {
                synchronized(lock) { metalJson = json }
                finishIfReady()
            }

            override fun onError(message: String) {
                synchronized(lock) { failures++ }
                finishIfReady()
            }
        })
        getJson(fxUrl, object : JsonCallback {
            override fun onSuccess(json: String) {
                synchronized(lock) { fxJson = json }
                finishIfReady()
            }

            override fun onError(message: String) {
                synchronized(lock) { failures++ }
                finishIfReady()
            }
        })
    }

    private fun fetchPumpChart(selectedDays: Int) {
        val interval = binanceInterval(selectedDays)
        val lock = Any()
        var pumpJson: String? = null
        var eurJson: String? = null
        var failures = 0

        fun finishIfReady() {
            synchronized(lock) {
                if (failures > 0) {
                    if (isCurrentChart("pump", selectedDays)) showFlatFallbackChart("pump", selectedDays)
                    return
                }

                val pump = pumpJson
                val eur = eurJson
                if (pump != null && eur != null) {
                    try {
                        val chartJson = binancePumpChartToChartJson(selectedDays, pump, eur)
                        cacheChartJson("pump", selectedDays, chartJson)
                        setStatus("Chart aktualisiert")
                    } catch (e: Exception) {
                        if (isCurrentChart("pump", selectedDays)) showFlatFallbackChart("pump", selectedDays)
                    }
                }
            }
        }

        fetchBinanceHistory("PUMPUSDT", interval, historyWindowDays(selectedDays), object : JsonCallback {
            override fun onSuccess(json: String) {
                synchronized(lock) { pumpJson = json }
                finishIfReady()
            }

            override fun onError(message: String) {
                synchronized(lock) { failures++ }
                finishIfReady()
            }
        })
        fetchBinanceHistory("EURUSDT", interval, historyWindowDays(selectedDays), object : JsonCallback {
            override fun onSuccess(json: String) {
                synchronized(lock) { eurJson = json }
                finishIfReady()
            }

            override fun onError(message: String) {
                synchronized(lock) { failures++ }
                finishIfReady()
            }
        })
    }

    private fun fetchBinanceHistory(
        symbol: String,
        interval: String,
        requestedDays: Int,
        callback: JsonCallback
    ) {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(requestedDays.toLong())
        val collected = JSONArray()

        fun fetchPage(cursor: Long) {
            val url = "$binanceSpotBaseUrl/klines?symbol=$symbol&interval=$interval" +
                "&limit=1000&startTime=$cursor&endTime=$endTime"
            getJson(url, object : JsonCallback {
                override fun onSuccess(json: String) {
                    try {
                        val rows = JSONArray(json)
                        if (rows.length() == 0) {
                            callback.onSuccess(collected.toString())
                            return
                        }

                        for (i in 0 until rows.length()) collected.put(rows.getJSONArray(i))
                        val lastOpenTime = rows.getJSONArray(rows.length() - 1).getLong(0)
                        val nextCursor = lastOpenTime + 1L

                        if (rows.length() >= 1000 && nextCursor in (cursor + 1)..endTime) {
                            fetchPage(nextCursor)
                        } else {
                            callback.onSuccess(collected.toString())
                        }
                    } catch (e: Exception) {
                        callback.onError("Ungueltige Binance-Historie")
                    }
                }

                override fun onError(message: String) {
                    callback.onError(message)
                }
            })
        }

        fetchPage(startTime)
    }

    private fun binancePumpChartToChartJson(selectedDays: Int, pumpJson: String, eurJson: String): String {
        val pumpPoints = binanceClosePoints(pumpJson)
        val eurPoints = binanceClosePoints(eurJson)
        val cutoff = System.currentTimeMillis() -
            TimeUnit.DAYS.toMillis(historyWindowDays(selectedDays).toLong())
        val points = ArrayList<Pair<Long, Double>>()

        pumpPoints.forEach { point ->
            if (point.first >= cutoff) {
                val eurUsdt = nearestValue(eurPoints, point.first)
                if (eurUsdt != null && eurUsdt > 0.0) {
                    points.add(point.first to (point.second / eurUsdt))
                }
            }
        }

        if (points.size < 2) throw IllegalArgumentException("Too few PUMP points")
        return chartJson("pump", selectedDays, "EUR", points)
    }

    private fun binanceClosePoints(json: String): List<Pair<Long, Double>> {
        val rows = JSONArray(json)
        val points = ArrayList<Pair<Long, Double>>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            points.add(row.getLong(0) to row.getString(4).toDouble())
        }
        return points.sortedBy { it.first }
    }

    private fun binanceInterval(selectedDays: Int): String {
        return when (selectedDays) {
            1, 7, 30 -> "1h"
            else -> "1d"
        }
    }

    private fun showFlatFallbackChart(asset: String, selectedDays: Int) {
        if (!isCurrentChart(asset, selectedDays)) return

        val price = latestCachedPrice(asset)
        if (price == null) {
            runOnUiThread {
                visibleChartTargets(asset).forEach { target ->
                    target.clear()
                    target.setNoDataText("Warten auf Daten...")
                    target.invalidate()
                }
            }
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

        runOnUiThread {
            visibleChartTargets(asset).forEach { target ->
                target.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < dates.size) dates[index] else ""
                    }
                }
                target.marker = CustomMarkerView(
                    this,
                    R.layout.marker_view,
                    points.first().y,
                    unitSuffixFor(asset),
                    now - span,
                    selectedDays
                )
                target.data = LineData(dataSet)
                target.invalidate()
            }
        }
        setStatus("Reserve-Chart angezeigt")
    }

    private fun isCurrentChart(asset: String, selectedDays: Int): Boolean {
        return selectedDays == days && (showAllCharts || asset == activeAsset)
    }

    private fun yahooRange(selectedDays: Int): String {
        return when (selectedDays) {
            1, 7, 30 -> "3mo"
            365 -> "1y"
            else -> "5y"
        }
    }

    private fun yahooInterval(selectedDays: Int): String {
        return when (selectedDays) {
            1, 7, 30 -> "1h"
            else -> "1d"
        }
    }

    private fun yahooBitcoinChartToChartJson(selectedDays: Int, json: String): String {
        val sourcePoints = yahooClosePoints(json)
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(historyWindowDays(selectedDays).toLong())
        val points = ArrayList<Pair<Long, Double>>()

        sourcePoints.forEach { point ->
            if (point.first >= cutoff) points.add(point)
        }

        if (points.size < 2) throw IllegalArgumentException("Too few fallback points")
        return chartJson("bitcoin", selectedDays, "EUR", points)
    }

    private fun yahooMetalChartToChartJson(asset: String, selectedDays: Int, metalJson: String, fxJson: String): String {
        val metalPoints = yahooClosePoints(metalJson)
        val fxPoints = yahooClosePoints(fxJson)
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(historyWindowDays(selectedDays).toLong())
        val points = ArrayList<Pair<Long, Double>>()

        metalPoints.forEach { point ->
            if (point.first >= cutoff) {
                val eurUsd = nearestValue(fxPoints, point.first)
                if (eurUsd != null && eurUsd > 0.0) {
                    points.add(point.first to (point.second / eurUsd * troyOuncesPerKilogram))
                }
            }
        }

        if (points.size < 2) throw IllegalArgumentException("Too few fallback points")
        return chartJson(asset, selectedDays, "EUR/kg", points)
    }

    private fun yahooClosePoints(json: String): List<Pair<Long, Double>> {
        val result = JSONObject(json)
            .getJSONObject("chart")
            .getJSONArray("result")
            .getJSONObject(0)
        val timestamps = result.getJSONArray("timestamp")
        val closes = result
            .getJSONObject("indicators")
            .getJSONArray("quote")
            .getJSONObject(0)
            .getJSONArray("close")
        val points = ArrayList<Pair<Long, Double>>()

        for (i in 0 until timestamps.length()) {
            if (!closes.isNull(i)) {
                points.add(timestamps.getLong(i) * 1000L to closes.getDouble(i))
            }
        }

        return points.sortedBy { it.first }
    }

    private fun nearestValue(points: List<Pair<Long, Double>>, timeMs: Long): Double? {
        var best: Double? = null

        for (point in points) {
            if (point.first <= timeMs) {
                best = point.second
            } else {
                break
            }
        }

        return best ?: points.firstOrNull()?.second
    }

    private fun chartJson(asset: String, selectedDays: Int, unit: String, points: List<Pair<Long, Double>>): String {
        val prices = JSONArray()
        points.forEach { point ->
            prices.put(JSONArray().put(point.first).put(point.second))
        }

        return JSONObject()
            .put("ok", true)
            .put("asset", asset)
            .put("days", selectedDays)
            .put("unit", unit)
            .put("cache", "android_chart_fallback")
            .put("prices", prices)
            .toString()
    }

    private fun colorFor(asset: String): String {
        return when (asset) {
            "gold" -> "#FFD700"
            "silver" -> "#C0C0C0"
            "pump" -> "#00E676"
            else -> "#F7931A"
        }
    }

    private fun chartKey(asset: String, selectedDays: Int): String = "chart_proxy_${asset}_$selectedDays"

    private fun chartHistoryKey(asset: String): String = "chart_history_v2_$asset"

    private fun chartFetchedAtKey(asset: String, selectedDays: Int): String = "${chartKey(asset, selectedDays)}_fetched_at"

    private fun cacheChartJson(asset: String, selectedDays: Int, json: String) {
        val displayJson = synchronized(historyLock) {
            val merged = TreeMap<Long, Double>()
            val incoming = JSONObject(json)
            val unit = incoming.optString("unit", if (asset == "gold" || asset == "silver") "EUR/kg" else "EUR")

            fun addPoints(source: String?) {
                if (source.isNullOrEmpty()) return
                try {
                    val prices = JSONObject(source).getJSONArray("prices")
                    for (i in 0 until prices.length()) {
                        val point = prices.getJSONArray(i)
                        merged[point.getLong(0)] = point.getDouble(1)
                    }
                } catch (_: Exception) {
                    // Ignore an obsolete or damaged cache entry and keep the valid history.
                }
            }

            addPoints(prefs.getString(chartHistoryKey(asset), ""))
            if (merged.isEmpty()) {
                listOf(1, 7, 30, 365, 1095).forEach {
                    addPoints(prefs.getString(chartKey(asset, it), ""))
                }
            }
            addPoints(json)

            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(historyRetentionDays.toLong())
            val retained = merged.entries
                .filter { it.key >= cutoff }
                .map { it.key to it.value }
            val historyJson = chartJson(asset, historyRetentionDays, unit, retained)
            val editor = prefs.edit().putString(chartHistoryKey(asset), historyJson)
            val fetchedAt = System.currentTimeMillis()
            if (selectedDays <= 30) {
                listOf(1, 7, 30).forEach {
                    editor.putLong(chartFetchedAtKey(asset, it), fetchedAt)
                }
            } else {
                editor.putLong(chartFetchedAtKey(asset, selectedDays), fetchedAt)
            }
            editor.apply()
            historyForDisplay(asset, selectedDays, historyJson)
        }

        renderChartIfVisible(asset, selectedDays, displayJson)
    }

    private fun cachedHistory(asset: String, selectedDays: Int): String? {
        val history = prefs.getString(chartHistoryKey(asset), "")
        if (!history.isNullOrEmpty()) return historyForDisplay(asset, selectedDays, history)

        val legacy = prefs.getString(chartKey(asset, selectedDays), "")
        return if (legacy.isNullOrEmpty()) null else legacy
    }

    private fun historyForDisplay(asset: String, selectedDays: Int, json: String): String {
        val source = JSONObject(json)
        val prices = source.getJSONArray("prices")
        if (prices.length() < 2) return json

        val lastTime = prices.getJSONArray(prices.length() - 1).getLong(0)
        val cutoff = lastTime - TimeUnit.DAYS.toMillis(historyWindowDays(selectedDays).toLong())
        val points = ArrayList<Pair<Long, Double>>()
        for (i in 0 until prices.length()) {
            val point = prices.getJSONArray(i)
            if (point.getLong(0) >= cutoff) {
                points.add(point.getLong(0) to point.getDouble(1))
            }
        }
        return chartJson(asset, selectedDays, source.optString("unit", "EUR"), points)
    }

    private fun historyWindowDays(selectedDays: Int): Int {
        return if (selectedDays <= 30) scrollHistoryDays else selectedDays
    }

    private fun isChartCacheFresh(asset: String, selectedDays: Int): Boolean {
        val saved = prefs.getString(chartHistoryKey(asset), "")
        if (saved.isNullOrEmpty()) return false

        val fetchedAt = prefs.getLong(chartFetchedAtKey(asset, selectedDays), 0L)
        if (fetchedAt <= 0L) return false

        return System.currentTimeMillis() - fetchedAt <= chartCacheMaxAgeMs(selectedDays)
    }

    private fun chartCacheMaxAgeMs(selectedDays: Int): Long {
        return when (selectedDays) {
            1 -> TimeUnit.MINUTES.toMillis(5)
            7 -> TimeUnit.HOURS.toMillis(6)
            30 -> TimeUnit.HOURS.toMillis(12)
            365 -> TimeUnit.DAYS.toMillis(1)
            else -> TimeUnit.DAYS.toMillis(7)
        }
    }

    private fun metalKgPrice(obj: JSONObject): Double {
        if (obj.has("eurPerKg")) return obj.getDouble("eurPerKg")
        return obj.getDouble("eur")
    }

    private fun latestCachedPrice(asset: String): Double? {
        if (asset == "pump") return latestCachedPumpPrice()

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
        return if (asset == "pump") {
            String.format(Locale.GERMAN, "EUR %,.6f", value)
        } else if (value >= 1000) {
            String.format(Locale.GERMAN, "EUR %,.0f%s", value, suffix)
        } else {
            String.format(Locale.GERMAN, "EUR %,.2f%s", value, suffix)
        }
    }

    private fun latestCachedPumpPrice(): Double? {
        if (!prefs.contains(lastPumpPriceKey)) return null
        return java.lang.Double.longBitsToDouble(prefs.getLong(lastPumpPriceKey, 0L))
            .takeIf { it.isFinite() && it > 0.0 }
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
