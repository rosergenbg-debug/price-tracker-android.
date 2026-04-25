package com.example.pricetracker
import android.graphics.Color
import android.os.Bundle
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
    private var chart: LineChart? = null
    private val client = OkHttpClient()
    private var days = 7
    private var activeId = "tether-gold"
    
    // КЭШ для хранения данных графиков: ключ - "id_days"
    private val dataCache = mutableMapOf<String, ArrayList<Entry>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); chart = findViewById(R.id.lineChart)
        
        setupChart()

        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; loadData() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; loadData() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; loadData() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; loadData() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { dataCache.clear(); updatePrices(); loadData() }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "tether-gold"; loadData() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "kinesis-silver"; loadData() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "bitcoin"; loadData() }
        
        updatePrices()
        loadData()
    }

    private fun setupChart() {
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false
    }

    private fun updatePrices() {
        // Биткоин через Binance (стабильно)
        client.newCall(Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=BTCEUR").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                runOnUiThread { try { tvB?.text = String.format("€%,.0f", JSONObject(r).getString("price").toDouble()) } catch(e:Exception){} }
            }
        })
        // Металлы
        client.newCall(Request.Builder().url("https://api.coingecko.com/api/v3/simple/price?ids=tether-gold,kinesis-silver&vs_currencies=eur").build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                runOnUiThread { try {
                    val j = JSONObject(r)
                    tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * 32.15)
                    tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * 32.15)
                } catch(e:Exception){} }
            }
        })
    }

    private fun loadData() {
        val cacheKey = "${activeId}_${days}"
        if (dataCache.containsKey(cacheKey)) {
            render(dataCache[cacheKey]!!)
            return
        }

        findViewById<TextView>(R.id.tvStatus).text = "Загрузка из сети..."
        val url = "https://api.coingecko.com/api/v3/coins/$activeId/market_chart?vs_currency=eur&days=$days"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                if (response.code == 429) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Лимит! Использую кэш или ждите.", Toast.LENGTH_SHORT).show() }
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
                    dataCache[cacheKey] = entries
                    render(entries)
                    findViewById<TextView>(R.id.tvStatus).text = "Обновлено (Кэш активен)"
                } catch(e:Exception){ findViewById<TextView>(R.id.tvStatus).text = "Сервер занят" } }
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
        chart?.data = LineData(set); chart?.legend?.isEnabled = false; chart?.invalidate()
    }
}
