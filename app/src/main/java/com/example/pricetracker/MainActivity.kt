package com.example.pricetracker
import android.content.Context
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
    // Возвращаемся на круглосуточные крипто-токены
    private val client = OkHttpClient(); private var days = 30; private var activeId = "tether-gold"
    
    private val cache = mutableMapOf<String, Pair<ArrayList<Entry>, ArrayList<String>>>()
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
        override fun getOffset(): MPPointF { return MPPointF(-(width / 2f), -height.toFloat() - 20f) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        setupChart()

        val btns = listOf<Button>(findViewById(R.id.btn1D), findViewById(R.id.btn1W), 
                                 findViewById(R.id.btn1M), findViewById(R.id.btn1Y), findViewById(R.id.btn5Y))

        fun selectBtn(b: Button, d: Int) {
            btns.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BA68C8")) }
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            days = d; fetchChart()
        }

        findViewById<Button>(R.id.btn1D).setOnClickListener { selectBtn(it as Button, 1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { selectBtn(it as Button, 7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { selectBtn(it as Button, 30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { selectBtn(it as Button, 365) }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { selectBtn(it as Button, 1825) }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; updateChartUI() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; updateChartUI() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; updateChartUI() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { fullSync() }

        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        fullSync()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false; chart?.legend?.isEnabled = false; chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Ожидание данных..."); chart?.setNoDataTextColor(Color.LTGRAY)
        
        chart?.axisLeft?.textColor = Color.LTGRAY
        chart?.axisLeft?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String { return String.format(Locale("ru"), "%,.0f", value) }
        }
        
        chart?.xAxis?.textColor = Color.LTGRAY
        chart?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
        
        // Включаем НАСЕЧКИ (Вертикальная сетка)
        chart?.xAxis?.setDrawGridLines(true)
        chart?.xAxis?.gridColor = Color.parseColor("#333333") // Темно-серая сетка
        chart?.xAxis?.gridLineWidth = 1f
        chart?.xAxis?.setAvoidFirstLastClipping(true)
    }

    private fun startTimer() {
        h.post(object : Runnable {
            override fun run() {
                tvT?.text = "Обновление: ${timeLeft}с"
                if (timeLeft <= 0) { timeLeft = 80; fullSync() } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun fullSync() {
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                runOnUiThread { try {
                    val j = JSONObject(r)
                    tvB?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
                    tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * 32.1507)
                    tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * 32.1507)
                } catch(e:Exception){} }
            }
        })
        fetchChart()
    }

    private fun fetchChart() {
        val key = "${activeId}_$days"
        if (cache.containsKey(key)) { updateChartUI(); return }

        tvSt?.text = "Загрузка графика..."
        val url = "https://api.coingecko.com/api/v3/coins/$activeId/market_chart?vs_currency=eur&days=$days"
        
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                try {
                    val p = JSONObject(r).getJSONArray("prices")
                    val entries = ArrayList<Entry>()
                    val dates = ArrayList<String>()
                    
                    val sdf = when(days) {
                        1 -> SimpleDateFormat("HH:mm", Locale("ru"))
                        7, 30 -> SimpleDateFormat("dd MMM", Locale("ru"))
                        else -> SimpleDateFormat("MMM yy", Locale("ru"))
                    }
                    val mult = if (activeId == "bitcoin") 1.0 else 32.1507

                    for (i in 0 until p.length()) {
                        val pt = p.getJSONArray(i)
                        entries.add(Entry(i.toFloat(), (pt.getDouble(1) * mult).toFloat()))
                        dates.add(sdf.format(Date(pt.getLong(0))))
                    }
                    
                    cache[key] = Pair(entries, dates)
                    runOnUiThread { updateChartUI(); tvSt?.text = "База обновлена" }
                } catch (e: Exception) { runOnUiThread { tvSt?.text = "Лимит API. Подождите немного." } }
            }
        })
    }

    private fun updateChartUI() {
        val cached = cache["${activeId}_$days"] ?: return
        val entries = cached.first
        val dates = cached.second
        
        val color = when {
            activeId.contains("gold") -> "#FFD700"
            activeId.contains("silver") -> "#E0E0E0"
            else -> "#F7931A"
        }
        
        val set = LineDataSet(entries, "").apply {
            this.color = Color.parseColor(color); setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.0f; setDrawFilled(true); fillColor = Color.parseColor(color); fillAlpha = 30
            mode = LineDataSet.Mode.LINEAR
        }
        
        // Устанавливаем количество насечек в зависимости от периода
        chart?.xAxis?.labelCount = when(days) {
            1 -> 6    // 6 насечек в день (каждые 4 часа)
            7 -> 7    // 7 насечек (каждый день)
            30 -> 6   // 6 насечек (каждые 5 дней)
            365 -> 12 // 12 насечек (каждый месяц)
            else -> 5 // 5 насечек (каждый год)
        }

        chart?.xAxis?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx >= 0 && idx < dates.size) dates[idx] else ""
            }
        }

        if (entries.isNotEmpty()) {
            val lastPrice = entries.last().y
            chart?.marker = ChartMarker(this, R.layout.marker_view, lastPrice)
            chart?.setDrawMarkers(true)
        }

        chart?.data = LineData(set)
        chart?.invalidate()
    }
}
