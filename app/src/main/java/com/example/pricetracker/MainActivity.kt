package com.example.pricetracker
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var tvG: TextView? = null; private var tvS: TextView? = null; private var tvB: TextView? = null
    private var tvT: TextView? = null; private var tvSt: TextView? = null; private var chart: LineChart? = null
    private val client = OkHttpClient(); private var days = 30; private var activeId = "tether-gold"
    
    private val cache = mutableMapOf<String, Pair<ArrayList<Entry>, ArrayList<String>>>()
    private val h = Handler(Looper.getMainLooper())
    private var timeLeft = 80

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
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; fetchChart() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; fetchChart() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; fetchChart() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { fetchPrices(); fetchChart() }

        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        fetchPrices()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false
        chart?.legend?.isEnabled = false
        chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Загрузка данных...")
        chart?.setNoDataTextColor(Color.LTGRAY)
        
        chart?.axisLeft?.textColor = Color.LTGRAY
        chart?.axisLeft?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format(Locale("ru"), "%,.0f", value)
            }
        }
        chart?.xAxis?.textColor = Color.LTGRAY
        chart?.xAxis?.position = XAxis.XAxisPosition.BOTTOM
        chart?.xAxis?.setDrawGridLines(false)
    }

    private fun startTimer() {
        h.post(object : Runnable {
            override fun run() {
                tvT?.text = "Обновление: ${timeLeft}с"
                if (timeLeft <= 0) { timeLeft = 80; fetchPrices(); fetchChart() } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun fetchPrices() {
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
    }

    private fun fetchChart() {
        val key = "${activeId}_$days"
        if (cache.containsKey(key)) { renderChart(key); return }

        tvSt?.text = "Загрузка графика..."
        val url = "https://api.coingecko.com/api/v3/coins/$activeId/market_chart?vs_currency=eur&days=$days"
        
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvSt?.text = "Ошибка сети. Проверьте интернет." }
            }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val p = JSONObject(r).getJSONArray("prices")
                        if (p.length() == 0) return
                        
                        val entries = ArrayList<Entry>()
                        val dates = ArrayList<String>()
                        val sdf = SimpleDateFormat(if (days <= 7) "HH:mm" else "dd MMM", Locale("ru"))
                        
                        for (i in 0 until p.length()) {
                            val pt = p.getJSONArray(i)
                            var v = pt.getDouble(1)
                            if (activeId != "bitcoin") v *= 32.1507
                            entries.add(Entry(i.toFloat(), v.toFloat()))
                            dates.add(sdf.format(Date(pt.getLong(0))))
                        }
                        
                        cache[key] = Pair(entries, dates)
                        runOnUiThread { renderChart(key); tvSt?.text = "График обновлен" }
                    } catch(e:Exception){ runOnUiThread { tvSt?.text = "Ошибка данных графика" } }
                } else if (response.code == 429) {
                    runOnUiThread { tvSt?.text = "Биржа просит подождать 1 мин..." }
                }
            }
        })
    }

    private fun renderChart(key: String) {
        val cached = cache[key] ?: return
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
        
        chart?.xAxis?.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx >= 0 && idx < dates.size) dates[idx] else ""
            }
        }

        chart?.data = LineData(set)
        chart?.invalidate()
    }
}
