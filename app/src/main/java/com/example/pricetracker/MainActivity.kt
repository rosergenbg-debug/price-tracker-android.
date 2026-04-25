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
    private var tvSt: TextView? = null; private var chart: LineChart? = null
    private val client = OkHttpClient(); private var days = 7; private var activeId = "tether-gold"
    
    // КЭШ: Храним всё здесь
    private val dataCache = mutableMapOf<String, ArrayList<Entry>>()
    
    // Список задач для предзагрузки (Asset + Period)
    private val assets = listOf("tether-gold", "kinesis-silver", "bitcoin")
    private val periods = listOf(7, 30, 365, 1825)
    private var loadIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvSt = findViewById(R.id.tvStatus)
        chart = findViewById(R.id.lineChart)
        
        setupChart()

        // Кнопки работают ТОЛЬКО с кэшем (мгновенно)
        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; showData() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; showData() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; showData() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; showData() }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; showData() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; showData() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; showData() }

        // При нажатии "Обновить" — очищаем всё и качаем заново
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { 
            dataCache.clear(); loadIndex = 0; startFullSync() 
        }
        
        startFullSync()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Синхронизация данных...")
    }

    private fun startFullSync() {
        updateCurrentPrices()
        preFetchNext() // Запуск очереди загрузки графиков
    }

    // Очередь загрузки: качаем по одному файлу каждые 1.5 секунды
    private fun preFetchNext() {
        val totalTasks = assets.size * periods.size
        if (loadIndex >= totalTasks) {
            tvSt?.text = "База обновлена (100%)"
            return
        }

        val asset = assets[loadIndex / periods.size]
        val pDays = periods[loadIndex % periods.size]
        val key = "${asset}_$pDays"

        tvSt?.text = "Загрузка базы: ${loadIndex + 1}/$totalTasks"

        val url = "https://api.coingecko.com/api/v3/coins/$asset/market_chart?vs_currency=eur&days=$pDays"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { scheduleNext(2000) }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val pJson = JSONObject(r).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until pJson.length()) {
                            val pt = pJson.getJSONArray(i)
                            var v = pt.getDouble(1)
                            if (asset != "bitcoin") v *= 32.15
                            entries.add(Entry(pt.getLong(0).toFloat(), v.toFloat()))
                        }
                        dataCache[key] = entries
                        if (asset == activeId && pDays == days) runOnUiThread { showData() }
                    } catch(e: Exception) {}
                }
                loadIndex++
                scheduleNext(1500) // Пауза 1.5 сек для обхода лимитов
            }
        })
    }

    private fun scheduleNext(ms: Long) {
        Handler(Looper.getMainLooper()).postDelayed({ preFetchNext() }, ms)
    }

    private fun updateCurrentPrices() {
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

    private fun showData() {
        val key = "${activeId}_$days"
        if (dataCache.containsKey(key)) {
            render(dataCache[key]!!)
        } else {
            chart?.clear()
            chart?.setNoDataText("В очереди на загрузку...")
            chart?.invalidate()
        }
    }

    private fun render(entries: ArrayList<Entry>) {
        val color = when(activeId) {
            "tether-gold" -> Color.parseColor("#FFD700")
            "kinesis-silver" -> Color.parseColor("#C0C0C0")
            else -> Color.parseColor("#F7931A")
        }
        val set = LineDataSet(entries, "").apply {
            this.color = color; setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.5f; setDrawFilled(true); fillColor = color; fillAlpha = 35
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.data = LineData(set); chart?.invalidate()
    }
}
