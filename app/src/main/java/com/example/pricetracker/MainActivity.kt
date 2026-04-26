package com.example.pricetracker
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var tvG: TextView? = null; private var tvS: TextView? = null; private var tvB: TextView? = null
    private var tvT: TextView? = null; private var tvSt: TextView? = null; private var chart: LineChart? = null
    private val client = OkHttpClient()
    
    // Внутренние ID активов: "gold", "silver", "bitcoin"
    private var activeAsset = "gold" 
    private var days = 30
    
    private lateinit var prefs: SharedPreferences
    private val syncQueue = mutableListOf<Pair<String, Int>>()
    private var isSyncing = false
    private var totalTasks = 15; private var completedTasks = 0
    private val h = Handler(Looper.getMainLooper()); private var timeLeft = 80

    inner class ChartMarker(context: Context, layoutResource: Int, private val lastPrice: Float) : MarkerView(context, layoutResource) {
        private val tvContent: TextView = findViewById(R.id.tvMarker)
        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            if (e != null) {
                val diff = e.y - lastPrice
                val pct = (diff / lastPrice) * 100
                val sign = if (pct > 0) "+" else ""
                tvContent.text = String.format(Locale("ru"), "€%,.0f\n%s%.2f%%", e.y, sign, pct)
            }
            super.refreshContent(e, highlight)
        }
        override fun getOffset(): MPPointF { return MPPointF(-(width / 1.2f), -height.toFloat() - 20f) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = getSharedPreferences("PriceTrackerCache", Context.MODE_PRIVATE)
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        setupChart()

        val btns = listOf<Button>(findViewById(R.id.btn1D), findViewById(R.id.btn1W), 
                                 findViewById(R.id.btn1M), findViewById(R.id.btn1Y), findViewById(R.id.btn3Y))

        fun selectBtn(b: Button, d: Int) {
            btns.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BA68C8")) }
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            days = d; showFromLocalCache()
        }

        findViewById<Button>(R.id.btn1D).setOnClickListener { selectBtn(it as Button, 1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { selectBtn(it as Button, 7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { selectBtn(it as Button, 30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { selectBtn(it as Button, 365) }
        findViewById<Button>(R.id.btn3Y).setOnClickListener { selectBtn(it as Button, 1095) } // 3 года
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeAsset = "gold"; showFromLocalCache() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeAsset = "silver"; showFromLocalCache() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeAsset = "bitcoin"; showFromLocalCache() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { timeLeft = 80; fetchPrices(); buildQueueAndStart() }

        loadPricesFromCache()
        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        fetchPrices(); buildQueueAndStart()
    }

    private fun isWeekend(): Boolean {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false; chart?.legend?.isEnabled = false; chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Нет сохраненных данных. Идет скачивание..."); chart?.setNoDataTextColor(Color.LTGRAY)
        chart?.axisLeft?.textColor = Color.LTGRAY
        chart?.axisLeft?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(v: Float): String = String.format(Locale("ru"), "%,.0f", v)
        }
        chart?.xAxis?.textColor = Color.LTGRAY; chart?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
        chart?.xAxis?.setDrawGridLines(true); chart?.xAxis?.gridColor = Color.parseColor("#333333")
        chart?.xAxis?.gridLineWidth = 1f; chart?.xAxis?.setAvoidFirstLastClipping(true)
        // ЖЕСТКАЯ ФИКСАЦИЯ ДАТ: Ровно 4 метки, равномерно распределенные. Никакой каши.
        chart?.xAxis?.setLabelCount(4, true)
    }

    private fun startTimer() {
        h.post(object : Runnable {
            override fun run() {
                tvT?.text = "Обновление: ${timeLeft}с"
                if (timeLeft <= 0) { timeLeft = 80; fetchPrices(); buildQueueAndStart() } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun loadPricesFromCache() {
        val saved = prefs.getString("last_prices", "")
        if (saved!!.isNotEmpty()) parseAndSetPrices(saved)
    }

    private fun parseAndSetPrices(json: String) {
        try {
            val j = JSONObject(json)
            tvB?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
            tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * 32.1507)
            tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * 32.1507)
        } catch(e:Exception){}
    }

    private fun showFromLocalCache() {
        val key = "chart_${activeAsset}_$days"
        val saved = prefs.getString(key, "")
        if (saved!!.isNotEmpty()) {
            // В кэше лежит JSON, где есть ключ source ("gecko" или "yahoo"), чтобы правильно его распарсить
            parseAndRenderChart(saved, activeAsset, days)
        } else {
            chart?.clear(); chart?.setNoDataText("Ожидание сети..."); chart?.invalidate()
        }
    }

    private fun fetchPrices() {
        // Цены ВСЕГДА тянем с Gecko, они работают 24/7
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    prefs.edit().putString("last_prices", r).apply()
                    runOnUiThread { parseAndSetPrices(r) }
                }
            }
        })
    }

    private fun buildQueueAndStart() {
        if (isSyncing) return 
        syncQueue.clear()
        val assets = listOf("gold", "silver", "bitcoin")
        val periods = listOf(1, 7, 30, 365, 1095) // 1095 = 3 года
        for (a in assets) for (p in periods) syncQueue.add(Pair(a, p))
        totalTasks = syncQueue.size; completedTasks = 0
        processQueue()
    }

    private fun processQueue() {
        if (syncQueue.isEmpty()) {
            isSyncing = false
            runOnUiThread { tvSt?.text = "Все данные актуальны" }
            return
        }
        isSyncing = true
        val task = syncQueue[0]; val asset = task.first; val pDays = task.second
        val key = "chart_${asset}_$pDays"

        runOnUiThread { tvSt?.text = "Синхронизация баз: $completedTasks/$totalTasks..." }

        // ГИБРИДНАЯ ЛОГИКА ВЫБОРА API
        val useGecko = when {
            asset == "bitcoin" -> false // Биткоин всегда через Yahoo
            pDays > 7 -> false          // Длинные периоды всегда через Yahoo
            else -> isWeekend()         // 1D и 1W для металлов: на выходных через Gecko
        }

        val url = if (useGecko) {
            val geckoId = if (asset == "gold") "tether-gold" else "kinesis-silver"
            "https://api.coingecko.com/api/v3/coins/$geckoId/market_chart?vs_currency=eur&days=$pDays"
        } else {
            val yahooId = when(asset) { "gold" -> "XAUEUR=X"; "silver" -> "XAGEUR=X"; else -> "BTC-EUR" }
            val range = when(pDays) { 1->"3d"; 7->"1wk"; 30->"1mo"; 365->"1y"; else->"3y" }
            val interval = when(pDays) { 1->"15m"; 7->"1h"; 30->"1d"; 365->"1d"; else->"1wk" }
            "https://query1.finance.yahoo.com/v8/finance/chart/$yahooId?interval=$interval&range=$range"
        }

        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { h.postDelayed({ processQueue() }, 5000) }
            override fun onResponse(call: Call, response: Response) {
                if (response.code == 429) { h.postDelayed({ processQueue() }, 10000); return }
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        // Оборачиваем ответ, чтобы при чтении знать, как парсить
                        val wrappedData = JSONObject().apply {
                            put("source", if (useGecko) "gecko" else "yahoo")
                            put("data", r)
                        }.toString()
                        
                        prefs.edit().putString(key, wrappedData).apply()
                        syncQueue.removeAt(0); completedTasks++
                        
                        runOnUiThread { if (activeAsset == asset && days == pDays) parseAndRenderChart(wrappedData, asset, pDays) }
                        h.postDelayed({ processQueue() }, 1500)
                    } catch (e: Exception) { syncQueue.removeAt(0); h.postDelayed({ processQueue() }, 1500) }
                } else { syncQueue.removeAt(0); h.postDelayed({ processQueue() }, 1500) }
            }
        })
    }

    private fun parseAndRenderChart(wrappedJson: String, asset: String, pDays: Int) {
        try {
            val wrapper = JSONObject(wrappedJson)
            val source = wrapper.getString("source")
            val dataStr = wrapper.getString("data")
            
            val entries = ArrayList<Entry>(); val dates = ArrayList<String>()
            val sdf = when(pDays) {
                1 -> SimpleDateFormat("HH:mm", Locale("ru"))
                7, 30 -> SimpleDateFormat("dd MMM", Locale("ru")) // 26 Апр
                else -> SimpleDateFormat("MMM yy", Locale("ru"))  // Апр 25
            }

            if (source == "gecko") {
                val p = JSONObject(dataStr).getJSONArray("prices")
                val mult = if (asset == "bitcoin") 1.0 else 32.1507
                for (i in 0 until p.length()) {
                    val pt = p.getJSONArray(i)
                    entries.add(Entry(i.toFloat(), (pt.getDouble(1) * mult).toFloat()))
                    dates.add(sdf.format(Date(pt.getLong(0))))
                }
            } else { // yahoo
                val json = JSONObject(dataStr).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val ts = json.getJSONArray("timestamp")
                val close = json.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                val mult = if (asset == "bitcoin") 1.0 else 32.1507
                var validIndex = 0f
                for (i in 0 until ts.length()) {
                    if (!close.isNull(i)) {
                        entries.add(Entry(validIndex, (close.getDouble(i) * mult).toFloat()))
                        dates.add(sdf.format(Date(ts.getLong(i) * 1000L)))
                        validIndex++
                    }
                }
            }

            val color = when(asset) { "gold" -> "#FFD700"; "silver" -> "#E0E0E0"; else -> "#F7931A" }
            val set = LineDataSet(entries, "").apply {
                this.color = Color.parseColor(color); setDrawCircles(false); setDrawValues(false)
                lineWidth = 2.0f; setDrawFilled(true); fillColor = Color.parseColor(color); fillAlpha = 30
                mode = LineDataSet.Mode.LINEAR
            }
            
            chart?.xAxis?.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val idx = v.toInt(); return if (idx >= 0 && idx < dates.size) dates[idx] else ""
                }
            }
            if (entries.isNotEmpty()) {
                chart?.marker = ChartMarker(this, R.layout.marker_view, entries.last().y)
                chart?.setDrawMarkers(true)
            }
            chart?.data = LineData(set); chart?.invalidate()
        } catch (e: Exception) {}
    }
}
