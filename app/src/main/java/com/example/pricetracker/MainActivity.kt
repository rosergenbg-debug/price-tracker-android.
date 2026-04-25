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
    private val client = OkHttpClient(); private var days = "5y"; private var activeId = "GC=F"
    private val cache = mutableMapOf<String, ArrayList<Entry>>()
    
    // Yahoo Finance символы: Золото (GC=F), Серебро (SI=F), БТЦ (BTC-EUR)
    private val ids = mapOf("GC=F" to "Золото", "SI=F" to "Серебро", "BTC-EUR" to "Bitcoin")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvSt = findViewById(R.id.tvStatus); chart = findViewById(R.id.lineChart)
        
        chart?.description?.isEnabled = false; chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY; chart?.axisRight?.isEnabled = false

        // Кнопки периодов
        findViewById<Button>(R.id.btn1D).setOnClickListener { days = "1d"; updateAll() }
        findViewById<Button>(R.id.btn1W).setOnClickListener { days = "5d"; updateAll() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = "1mo"; updateAll() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = "1y"; updateAll() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = "5y"; updateAll() }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { activeId = "GC=F"; render() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { activeId = "SI=F"; render() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { activeId = "BTC-EUR"; render() }
        
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { updateAll() }
        
        updateAll()
    }

    private fun updateAll() {
        tvSt?.text = "Синхронизация с Yahoo Finance..."
        // Запускаем 3 запроса одновременно
        fetchYahoo("GC=F")
        fetchYahoo("SI=F")
        fetchYahoo("BTC-EUR")
    }

    private fun fetchYahoo(symbol: String) {
        // Запрашиваем историю (из которой возьмем и последнюю цену)
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=$days"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0") // Маскируемся под браузер
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvSt?.text = "Ошибка связи" }
            }
            override fun onResponse(call: Call, response: Response) {
                val r = response.body?.string() ?: ""
                try {
                    val json = JSONObject(r).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    val meta = json.getJSONObject("meta")
                    val currentPrice = meta.getDouble("regularMarketPrice")
                    
                    val timestamps = json.getJSONArray("timestamp")
                    val indicators = json.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                    val closePrices = indicators.getJSONArray("close")
                    
                    val entries = ArrayList<Entry>()
                    for (i in 0 until timestamps.length()) {
                        if (!closePrices.isNull(i)) {
                            var price = closePrices.getDouble(i)
                            // Для металлов переводим унцию в КГ
                            if (symbol != "BTC-EUR") price *= 32.15
                            entries.add(Entry(timestamps.getLong(i).toFloat(), price.toFloat()))
                        }
                    }
                    
                    cache[symbol] = entries

                    runOnUiThread {
                        val formattedPrice = if (symbol == "BTC-EUR") String.format("€%,.0f", currentPrice) 
                                             else String.format("€%,.0f", currentPrice * 32.15)
                        
                        when(symbol) {
                            "GC=F" -> tvG?.text = formattedPrice
                            "SI=F" -> tvS?.text = formattedPrice
                            "BTC-EUR" -> tvB?.text = formattedPrice
                        }
                        if (symbol == activeId) render()
                        tvSt?.text = "Данные обновлены"
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvSt?.text = "Ошибка парсинга Yahoo" }
                }
            }
        })
    }

    private fun render() {
        val entries = cache[activeId]
        if (entries == null) {
            chart?.clear(); chart?.setNoDataText("Загрузка..."); chart?.invalidate()
            return
        }
        val color = when(activeId) {
            "GC=F" -> Color.parseColor("#FFD700")
            "SI=F" -> Color.parseColor("#C0C0C0")
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
