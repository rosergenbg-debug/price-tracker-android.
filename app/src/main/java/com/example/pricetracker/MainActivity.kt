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
    private val client = OkHttpClient(); private var days = 1; private var activeId = "tether-gold"
    private val cache = mutableMapOf<String, ArrayList<Entry>>()
    
    private var timeLeft = 80
    private val h = Handler(Looper.getMainLooper())
    
    private val qIds = listOf("tether-gold", "kinesis-silver", "bitcoin")
    private val qDays = listOf(1, 7, 30, 365, 1825)
    private var qI = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvT = findViewById(R.id.tvTimer)
        tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false

        findViewById<Button>(R.id.btn1D).setOnClickListener { days = 1; updateUI() }
        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; updateUI() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; updateUI() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; updateUI() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; updateUI() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { 
            cache.clear(); qI = 0; timeLeft = 80; fullSync() 
        }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; updateUI() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; updateUI() }
        
        startTimer()
        fullSync()
    }

    private fun startTimer() {
        h.post(object : Runnable {
            override fun run() {
                tvT?.text = "Обновление: ${timeLeft}с"
                if (timeLeft <= 0) {
                    timeLeft = 80
                    updatePrices()
                } else { timeLeft-- }
                h.postDelayed(this, 1000)
            }
        })
    }

    private fun fullSync() {
        updatePrices()
        qI = 0
        processNextInQueue()
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

    private fun processNextInQueue() {
        val total = qIds.size * qDays.size
        if (qI >= total) {
            tvSt?.text = "База готова (100%)"
            return
        }

        val id = qIds[qI / qDays.size]
        val d = qDays[qI % qDays.size]
        val key = "${id}_$d"

        tvSt?.text = "Синхронизация... (${qI + 1}/$total)"

        val url = "https://api.coingecko.com/api/v3/coins/$id/market_chart?vs_currency=eur&days=$d"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                qI++
                h.postDelayed({ processNextInQueue() }, 3000)
            }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val p = JSONObject(r).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until p.length()) {
                            val pt = p.getJSONArray(i)
                            var v = pt.getDouble(1)
                            if (id != "bitcoin") v *= 32.15
                            entries.add(Entry(pt.getLong(0).toFloat(), v.toFloat()))
                        }
                        cache[key] = entries
                        if (id == activeId && d == days) runOnUiThread { updateUI() }
                    } catch(e:Exception){}
                }
                qI++
                h.postDelayed({ processNextInQueue() }, 2000) // Пауза 2 сек, чтобы не злить API
            }
        })
    }

    private fun updateUI() {
        val key = "${activeId}_$days"
        if (cache.containsKey(key)) {
            val entries = cache[key]!!
            val color = when {
                activeId.contains("gold") -> Color.parseColor("#FFD700")
                activeId.contains("silver") -> Color.parseColor("#E0E0E0")
                else -> Color.parseColor("#F7931A")
            }
            val set = LineDataSet(entries, "").apply {
                this.color = color; setDrawCircles(false); setDrawValues(false)
                lineWidth = 2.5f; setDrawFilled(true); fillColor = color; fillAlpha = 35
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart?.data = LineData(set)
            chart?.invalidate()
            tvSt?.text = "Актив: $activeId ($days дн.)"
        } else {
            chart?.clear()
            chart?.setNoDataText("Загрузка данных для этого периода...")
            chart?.invalidate()
            tvSt?.text = "Ожидание синхронизации..."
        }
    }
}
