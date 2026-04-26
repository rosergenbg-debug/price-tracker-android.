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
    private val client = OkHttpClient(); private var days = 30; private var activeId = "tether-gold"
    
    // ЖЕСТКИЙ КЭШ В ПАМЯТИ ТЕЛЕФОНА
    private lateinit var prefs: SharedPreferences
    
    private val syncQueue = mutableListOf<Pair<String, Int>>()
    private var isSyncing = false
    private var totalTasks = 15
    private var completedTasks = 0

    private val h = Handler(Looper.getMainLooper())
    private var timeLeft = 80

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
        
        // Инициализация памяти телефона
        prefs = getSharedPreferences("PriceTrackerCache", Context.MODE_PRIVATE)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        setupChart()

        val btns = listOf<Button>(findViewById(R.id.btn1D), findViewById(R.id.btn1W), 
                                 findViewById(R.id.btn1M), findViewById(R.id.btn1Y), findViewById(R.id.btn5Y))

        fun selectBtn(b: Button, d: Int) {
            btns.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BA68C8")) }
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            days = d
            showFromLocalCache() // Читаем из телефона
        }

        findViewById<Button>(R.id.btn1D).setOnClickListener { selectBtn(it as Button, 1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { selectBtn(it as Button, 7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { selectBtn(it as Button, 30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { selectBtn(it as Button, 365) }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { selectBtn(it as Button, 1825) }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; showFromLocalCache() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; showFromLocalCache() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; showFromLocalCache() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { 
            timeLeft = 80; fetchPrices(); buildQueueAndStart() 
        }

        // Загружаем последние сохраненные цены при старте мгновенно
        loadPricesFromCache()
        
        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        
        // Запускаем невидимое обновление
        fetchPrices()
        buildQueueAndStart()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false; chart?.legend?.isEnabled = false; chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Нет сохраненных данных. Идет скачивание..."); chart?.setNoDataTextColor(Color.LTGRAY)
        chart?.axisLeft?.textColor = Color.LTGRAY
        chart?.axisLeft?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String { return String.format(Locale("ru"), "%,.0f", value) }
        }
        chart?.xAxis?.textColor = Color.LTGRAY; chart?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
        chart?.xAxis?.setDrawGridLines(true); chart?.xAxis?.gridColor = Color.parseColor("#333333")
        chart?.xAxis?.gridLineWidth = 1f; chart?.xAxis?.setAvoidFirstLastClipping(true)
    }

    private fun startTimer() {
        h.post(object : Runnable {
            override fun run() {
                tvT?.text = "Обновление: ${timeLeft}с"
                if (timeLeft <= 0) { 
                    timeLeft = 80
                    fetchPrices() 
                    buildQueueAndStart() 
                } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    // --- ЛОГИКА ПАМЯТИ ТЕЛЕФОНА ---

    private fun loadPricesFromCache() {
        val savedPrices = prefs.getString("last_prices", "")
        if (savedPrices!!.isNotEmpty()) {
            parseAndSetPrices(savedPrices)
        }
    }

    private fun parseAndSetPrices(jsonString: String) {
        try {
            val j = JSONObject(jsonString)
            tvB?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
            tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * 32.1507)
            tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * 32.1507)
        } catch(e:Exception){}
    }

    private fun showFromLocalCache() {
        val key = "chart_${activeId}_$days"
        val savedData = prefs.getString(key, "")
        if (savedData!!.isNotEmpty()) {
            parseAndRenderChart(savedData, activeId, days)
        } else {
            chart?.clear()
            chart?.setNoDataText("Ожидание сети...")
            chart?.invalidate()
        }
    }

    // --- ФОНОВЫЕ ЗАПРОСЫ К API ---

    private fun fetchPrices() {
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    // Сохраняем в память телефона навсегда (до перезаписи)
                    prefs.edit().putString("last_prices", r).apply()
                    runOnUiThread { parseAndSetPrices(r) }
                }
            }
        })
    }

    private fun buildQueueAndStart() {
        if (isSyncing) return 
        syncQueue.clear()
        val assets = listOf("tether-gold", "kinesis-silver", "bitcoin")
        val periods = listOf(1, 7, 30, 365, 1825)
        for (a in assets) for (p in periods) syncQueue.add(Pair(a, p))
        totalTasks = syncQueue.size
        completedTasks = 0
        processQueue()
    }

    private fun processQueue() {
        if (syncQueue.isEmpty()) {
            isSyncing = false
            runOnUiThread { tvSt?.text = "Все данные актуальны" }
            return
        }
        isSyncing = true
        val task = syncQueue[0]
        val symbol = task.first
        val pDays = task.second
        val key = "chart_${symbol}_$pDays"

        runOnUiThread { tvSt?.text = "Фоновая проверка: $completedTasks/$totalTasks..." }

        val url = "https://api.coingecko.com/api/v3/coins/$symbol/market_chart?vs_currency=eur&days=$pDays"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                h.postDelayed({ processQueue() }, 5000) // Нет сети - ждем 5 сек
            }
            override fun onResponse(call: Call, response: Response) {
                if (response.code == 429) {
                    h.postDelayed({ processQueue() }, 10000) // Лимит - ждем 10 сек
                    return
                }
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        // Сохраняем "сырой" ответ от биржи в телефон
                        prefs.edit().putString(key, r).apply()
                        
                        syncQueue.removeAt(0)
                        completedTasks++
                        
                        // Если смотрим на этот график прямо сейчас - обновляем экран
                        runOnUiThread { if (activeId == symbol && days == pDays) parseAndRenderChart(r, symbol, pDays) }
                        h.postDelayed({ processQueue() }, 1500)
                    } catch (e: Exception) { 
                        syncQueue.removeAt(0)
                        h.postDelayed({ processQueue() }, 1500)
                    }
                } else {
                    syncQueue.removeAt(0)
                    h.postDelayed({ processQueue() }, 1500)
                }
            }
        })
    }

    private fun parseAndRenderChart(jsonString: String, symbol: String, pDays: Int) {
        try {
            val p = JSONObject(jsonString).getJSONArray("prices")
            val entries = ArrayList<Entry>()
            val dates = ArrayList<String>()
            
            val sdf = when(pDays) {
                1 -> SimpleDateFormat("HH:mm", Locale("ru"))
                7, 30 -> SimpleDateFormat("dd MMM", Locale("ru"))
                else -> SimpleDateFormat("MMM yy", Locale("ru"))
            }
            val mult = if (symbol == "bitcoin") 1.0 else 32.1507

            for (i in 0 until p.length()) {
                val pt = p.getJSONArray(i)
                entries.add(Entry(i.toFloat(), (pt.getDouble(1) * mult).toFloat()))
                dates.add(sdf.format(Date(pt.getLong(0))))
            }

            val color = when {
                symbol.contains("gold") -> "#FFD700"
                symbol.contains("silver") -> "#E0E0E0"
                else -> "#F7931A"
            }
            val set = LineDataSet(entries, "").apply {
                this.color = Color.parseColor(color); setDrawCircles(false); setDrawValues(false)
                lineWidth = 2.0f; setDrawFilled(true); fillColor = Color.parseColor(color); fillAlpha = 30
                mode = LineDataSet.Mode.LINEAR
            }
            
            chart?.xAxis?.labelCount = when(pDays) { 1->6; 7->7; 30->6; 365->12; else->5 }
            chart?.xAxis?.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val idx = v.toInt(); return if (idx >= 0 && idx < dates.size) dates[idx] else ""
                }
            }
            if (entries.isNotEmpty()) {
                val lastPrice = entries.last().y
                chart?.marker = ChartMarker(this, R.layout.marker_view, lastPrice)
                chart?.setDrawMarkers(true)
            }
            chart?.data = LineData(set); chart?.invalidate()
        } catch (e: Exception) {}
    }
}
