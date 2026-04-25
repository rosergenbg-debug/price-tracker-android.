package com.example.pricetracker
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
    private var tvGold: TextView? = null
    private var tvSilver: TextView? = null
    private var tvBtc: TextView? = null
    private var tvStat: TextView? = null
    private var chart: LineChart? = null
    
    private val client = OkHttpClient()
    private val OZ_TO_KG = 32.1507
    private var days = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvGold = findViewById(R.id.tvGoldPrice)
        tvSilver = findViewById(R.id.tvSilverPrice)
        tvBtc = findViewById(R.id.tvBitcoinPrice)
        tvStat = findViewById(R.id.tvStatus)
        chart = findViewById(R.id.lineChart)

        setupChart()
        
        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; update() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; update() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; update() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; update() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { update() }
        
        update()
    }

    private fun setupChart() {
        chart?.let {
            it.description.isEnabled = false
            it.xAxis.textColor = Color.WHITE
            it.axisLeft.textColor = Color.WHITE
            it.axisRight.isEnabled = false
            it.legend.textColor = Color.WHITE
            it.setNoDataText("Загрузка...")
        }
    }

    private fun update() {
        tvStat?.text = "Связь..."
        val urlP = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        val urlH = "https://api.coingecko.com/api/v3/coins/tether-gold/market_chart?vs_currency=eur&days=$days"

        client.newCall(Request.Builder().url(urlP).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { tvStat?.text = "Сеть недоступна" } }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val j = JSONObject(data)
                        tvBtc?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
                        tvGold?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG)
                        tvSilver?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG)
                        loadH(urlH)
                    } catch (e: Exception) { tvStat?.text = "Ошибка API" }
                }
            }
        })
    }

    private fun loadH(url: String) {
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val p = JSONObject(data).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until p.length()) {
                            val pt = p.getJSONArray(i)
                            entries.add(Entry(pt.getLong(0).toFloat(), (pt.getDouble(1) * OZ_TO_KG).toFloat()))
                        }
                        showChart(entries)
                        tvStat?.text = "Период: $days дн."
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun showChart(entries: ArrayList<Entry>) {
        val colorGold = Color.parseColor("#FFD700")
        val set = LineDataSet(entries, "Золото (€/КГ)").apply {
            color = colorGold; setDrawCircles(false); setDrawValues(false)
            lineWidth = 2f; setDrawFilled(true); fillColor = colorGold; fillAlpha = 45
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.let {
            it.data = LineData(set)
            it.xAxis.valueFormatter = object : ValueFormatter() {
                val sdf = SimpleDateFormat("dd/MM", Locale.GERMANY)
                override fun getFormattedValue(v: Float) = sdf.format(Date(v.toLong()))
            }
            it.invalidate()
        }
    }
}
