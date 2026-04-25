package com.example.pricetracker
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var tvG: TextView? = null; private var tvS: TextView? = null; private var tvB: TextView? = null
    private var tvSt: TextView? = null; private var chart: LineChart? = null
    private val client = OkHttpClient()
    private val OZ_TO_KG = 32.1507
    private var days = 7
    private var selectedId = "tether-gold"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvG = findViewById(R.id.tvGoldPrice); tvS = findViewById(R.id.tvSilverPrice)
        tvB = findViewById(R.id.tvBitcoinPrice); tvSt = findViewById(R.id.tvStatus)
        chart = findViewById(R.id.lineChart)

        chart?.description?.isEnabled = false
        chart?.xAxis?.textColor = Color.GRAY
        chart?.axisLeft?.textColor = Color.GRAY
        chart?.axisRight?.isEnabled = false
        chart?.setNoDataText("Нажмите обновить...")

        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; loadChart() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; loadChart() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; loadChart() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; loadChart() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { updateAll() }
        
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { selectedId = "tether-gold"; loadChart() }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { selectedId = "kinesis-silver"; loadChart() }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { selectedId = "bitcoin"; loadChart() }
        
        updateAll()
    }

    private fun updateAll() {
        // Биткоин через Binance (100% стабильно)
        val reqBtc = Request.Builder().url("https://api.binance.com/api/v3/ticker/price?symbol=BTCEUR").build()
        client.newCall(reqBtc).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                runOnUiThread { try {
                    val price = JSONObject(res).getString("price").toDouble()
                    tvB?.text = String.format("€%,.0f", price)
                } catch(e:Exception) {} }
            }
        })

        // Золото и Серебро через CoinGecko (Цены)
        val reqMetals = Request.Builder().url("https://api.coingecko.com/api/v3/simple/price?ids=tether-gold,kinesis-silver&vs_currencies=eur").build()
        client.newCall(reqMetals).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: ""
                runOnUiThread { try {
                    val j = JSONObject(res)
                    tvG?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG)
                    tvS?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG)
                } catch(e:Exception) {} }
            }
        })
        loadChart()
    }

    private fun loadChart() {
        tvSt?.text = "Загрузка графика..."
        val url = "https://api.coingecko.com/api/v3/coins/$selectedId/market_chart?vs_currency=eur&days=$days"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvSt?.text = "Ошибка сети" } }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val p = JSONObject(data).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until p.length()) {
                            val pt = p.getJSONArray(i)
                            var valP = pt.getDouble(1)
                            if (selectedId != "bitcoin") valP *= OZ_TO_KG
                            entries.add(Entry(pt.getLong(0).toFloat(), valP.toFloat()))
                        }
                        draw(entries)
                        tvSt?.text = "Обновлено за $days дн."
                    } catch (e: Exception) { 
                        tvSt?.text = "Сервер занят (лимит)"
                        Toast.makeText(this@MainActivity, "Лимит API. Подождите 1 мин.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun draw(entries: ArrayList<Entry>) {
        val color = when(selectedId) {
            "tether-gold" -> Color.parseColor("#FFD700")
            "kinesis-silver" -> Color.parseColor("#C0C0C0")
            else -> Color.parseColor("#F7931A")
        }
        val set = LineDataSet(entries, "").apply {
            this.color = color; setDrawCircles(false); setDrawValues(false)
            lineWidth = 2.5f; setDrawFilled(true); fillColor = color; fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.data = LineData(set)
        chart?.xAxis?.valueFormatter = object : ValueFormatter() {
            val sdf = SimpleDateFormat("dd/MM", Locale.GERMANY)
            override fun getFormattedValue(v: Float) = sdf.format(Date(v.toLong()))
        }
        chart?.legend?.isEnabled = false
        chart?.invalidate()
    }
}
