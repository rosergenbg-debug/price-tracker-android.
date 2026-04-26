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
    private val client = OkHttpClient(); private var days = 30; private var activeId = "XAUEUR=X"
    
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
            btns.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BA68C8")) } // Лиловый
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")) // Зеленый
            days = d; fetchYahoo(activeId)
        }

        findViewById<Button>(R.id.btn1D).setOnClickListener { selectBtn(it as Button, 1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { selectBtn(it as Button, 7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { selectBtn(it as Button, 30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { selectBtn(it as Button, 365) }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { selectBtn(it as Button, 1825) }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "XAUEUR=X"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "XAGEUR=X"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "BTC-EUR"; updateUI() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { fullSync() }

        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        fullSync()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false
        chart?.legend?.isEnabled = false
        chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Синхронизация с биржей...")
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
                if (timeLeft <= 0) { timeLeft = 80; fullSync() } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun fullSync() {
        tvSt?.text = "Обновление данных..."
        listOf("XAUEUR=X", "XAGEUR=X", "BTC-EUR").forEach { fetchYahoo(it) }
    }

    private fun fetchYahoo(symbol: String) {
        // ХИТРОСТЬ: Для 1D запрашиваем 3 дня, чтобы на выходных график не был пустым!
        val range = when(days) { 1->"3d"; 7->"1wk"; 30->"1mo"; 365->"1y"; else->"5y" }
        val interval = when(days) { 1->"15m"; 7->"1h"; 30->"1d"; 365->"1d"; else->"1wk" }
        
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=$interval&range=$range"
        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvSt?.text = "Ошибка сети" }
            }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                try {
                    val json = JSONObject(r).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    val currentPrice = json.getJSONObject("meta").getDouble("regularMarketPrice")
                    val ts = json.getJSONArray("timestamp")
                    val close = json.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                    
                    val entries = ArrayList<Entry>()
                    val dates = ArrayList<String>()
                    val sdf = SimpleDateFormat(if (days <= 7) "HH:mm dd/MM" else "dd MMM yyyy", Locale("ru"))
                    val mult = if (symbol == "BTC-EUR") 1.0 else 32.1507 // Унции в КГ

                    var validIndex = 0f
                    for (i in 0 until ts.length()) {
                        // Жесткая проверка на пустоту (Yahoo любит присылать null)
                        if (!close.isNull(i)) {
                            val p = close.getDouble(i) * mult
                            entries.add(Entry(validIndex, p.toFloat()))
                            dates.add(sdf.format(Date(ts.getLong(i) * 1000L)))
                            validIndex++
                        }
                    }
                    
                    cache["${symbol}_$days"] = Pair(entries, dates)
                    
                    runOnUiThread {
                        val pStr = String.format(Locale("ru"), "€%,.0f", currentPrice * mult)
                        when(symbol) { "XAUEUR=X" -> tvG?.text = pStr; "XAGEUR=X" -> tvS?.text = pStr; "BTC-EUR" -> tvB?.text = pStr }
                        if (symbol == activeId) updateUI()
                        tvSt?.text = "База обновлена (Yahoo Finance)"
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvSt?.text = "Обработка данных..." }
                }
            }
        })
    }

    private fun updateUI() {
        val cached = cache["${activeId}_$days"]
        if (cached == null) return
        
        val entries = cached.first
        val dates = cached.second
        val color = when(activeId) { "XAUEUR=X" -> "#FFD700"; "XAGEUR=X" -> "#C0C0C0"; else -> "#F7931A" }
        
        val set = LineDataSet(entries, "").apply {
            this.color = Color.parseColor(color); setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.0f; setDrawFilled(true); fillColor = Color.parseColor(color); fillAlpha = 30
            mode = LineDataSet.Mode.LINEAR // Резкий биржевой график
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
