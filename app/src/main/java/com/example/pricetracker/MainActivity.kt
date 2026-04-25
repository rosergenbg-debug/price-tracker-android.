package com.example.pricetracker
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
    private val client = OkHttpClient(); private var days = 7; private var activeId = "tether-gold"
    private val cache = mutableMapOf<String, ArrayList<Entry>>()
    
    private var secondsLeft = 80
    private val timerHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false

        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; loadData() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; loadData() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; loadData() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; loadData() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { forceRefresh() }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; loadData() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; loadData() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; loadData() }
        
        startTimer()
        forceRefresh()
    }

    private fun startTimer() {
        timerHandler.post(object : Runnable {
            override fun run() {
                if (secondsLeft <= 0) {
                    forceRefresh()
                    secondsLeft = 80
                }
                tvT?.text = "Обновление через: ${secondsLeft}с"
                secondsLeft--
                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun forceRefresh() {
        updatePrices()
        loadData()
    }

    private fun updatePrices() {
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                runOnUiThread { try {
                    val j = JSONObject(r)
                    tvB?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
                    tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * 32.15)
                    tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * 32.15)
                } catch(e:Exception){} }
            }
        })
    }

    private fun loadData() {
        val key = "${activeId}_${days}"
        if (cache.containsKey(key)) render(cache[key]!!)

        tvSt?.text = "Запрос к сети..."
        val url = "https://api.coingecko.com/api/v3/coins/$activeId/market_chart?vs_currency=eur&days=$days"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvSt?.text = "Сеть недоступна" } }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.code == 429) {
                    runOnUiThread { tvSt?.text = "Лимит API. Ждём таймер." }
                    return
                }
                runOnUiThread { try {
                    val p = JSONObject(r).getJSONArray("prices")
                    val entries = ArrayList<Entry>()
                    for (i in 0 until p.length()) {
                        val pt = p.getJSONArray(i)
                        var v = pt.getDouble(1)
                        if (activeId != "bitcoin") v *= 32.15
                        entries.add(Entry(pt.getLong(0).toFloat(), v.toFloat()))
                    }
                    cache[key] = entries
                    render(entries)
                    tvSt?.text = "Данные обновлены"
                } catch(e:Exception){ tvSt?.text = "Ошибка сервера" } }
            }
        })
    }

    private fun render(entries: ArrayList<Entry>) {
        val color = when(activeId) {
            "tether-gold" -> Color.parseColor("#FFD700")
            "kinesis-silver" -> Color.parseColor("#C0C0C0")
            else -> Color.parseColor("#F7931A")
        }
        val set = LineDataSet(entries, "").apply {
            this.color = color; setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.5f; setDrawFilled(true); fillColor = color; fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.data = LineData(set); chart?.invalidate()
    }
}
