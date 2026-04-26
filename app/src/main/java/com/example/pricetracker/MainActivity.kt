package com.example.pricetracker
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private var tvG: TextView? = null; private var tvS: TextView? = null; private var tvB: TextView? = null
    private var tvT: TextView? = null; private var tvSt: TextView? = null; private var chart: LineChart? = null
    private val client = OkHttpClient(); private var days = 30; private var activeId = "GC=F"
    private val cache = mutableMapOf<String, ArrayList<Entry>>()
    private val h = Handler(Looper.getMainLooper())
    private var timeLeft = 80

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false

        val btns = listOf<Button>(findViewById(R.id.btn1D), findViewById(R.id.btn1W), 
                                 findViewById(R.id.btn1M), findViewById(R.id.btn1Y), findViewById(R.id.btn5Y))

        fun selectBtn(b: Button, d: Int) {
            btns.forEach { it.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BA68C8")) }
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            days = d; updateUI()
        }

        findViewById<Button>(R.id.btn1D).setOnClickListener { selectBtn(it as Button, 1) }
        findViewById<Button>(R.id.btn1W).setOnClickListener { selectBtn(it as Button, 7) }
        findViewById<Button>(R.id.btn1M).setOnClickListener { selectBtn(it as Button, 30) }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { selectBtn(it as Button, 365) }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { selectBtn(it as Button, 1825) }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "GC=F"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "SI=F"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "BTC-EUR"; updateUI() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { fullSync() }

        // По умолчанию 1M активна
        selectBtn(findViewById(R.id.btn1M), 30)
        startTimer()
        fullSync()
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
        tvSt?.text = "Синхронизация Yahoo Finance..."
        listOf("GC=F", "SI=F", "BTC-EUR").forEach { fetchYahoo(it) }
    }

    private fun fetchYahoo(symbol: String) {
        val range = when(days) { 1->"1d"; 7->"5d"; 30->"1mo"; 365->"1y"; else->"5y" }
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=$range"
        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                try {
                    val json = JSONObject(r).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    val currentPrice = json.getJSONObject("meta").getDouble("regularMarketPrice")
                    val ts = json.getJSONArray("timestamp")
                    val close = json.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                    val entries = ArrayList<Entry>()
                    for (i in 0 until ts.length()) {
                        if (!close.isNull(i)) {
                            var p = close.getDouble(i)
                            if (symbol != "BTC-EUR") p *= 32.15
                            entries.add(Entry(ts.getLong(i).toFloat(), p.toFloat()))
                        }
                    }
                    cache["${symbol}_$days"] = entries
                    runOnUiThread {
                        val pStr = if (symbol == "BTC-EUR") String.format("€%,.0f", currentPrice) 
                                   else String.format("€%,.0f", currentPrice * 32.15)
                        when(symbol) { "GC=F" -> tvG?.text = pStr; "SI=F" -> tvS?.text = pStr; "BTC-EUR" -> tvB?.text = pStr }
                        if (symbol == activeId) updateUI()
                        tvSt?.text = "Данные обновлены"
                    }
                } catch (e: Exception) {}
            }
        })
    }

    private fun updateUI() {
        val entries = cache["${activeId}_$days"]
        if (entries == null) { fetchYahoo(activeId); return }
        val color = when(activeId) { "GC=F" -> "#FFD700"; "SI=F" -> "#C0C0C0"; else -> "#F7931A" }
        val set = LineDataSet(entries, "").apply {
            this.color = Color.parseColor(color); setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.5f; setDrawFilled(true); fillColor = Color.parseColor(color); fillAlpha = 35
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.data = LineData(set); chart?.invalidate()
    }
}
