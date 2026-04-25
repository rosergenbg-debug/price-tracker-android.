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
    private var tvGold: TextView? = null
    private var tvSilver: TextView? = null
    private var tvBtc: TextView? = null
    private var tvStat: TextView? = null
    private var chart: LineChart? = null
    
    private val client = OkHttpClient()
    private val OZ_TO_KG = 32.1507
    private var days = 7
    
    // Переменная для хранения текущего выбранного актива для графика
    // По умолчанию: Золото
    private var selectedCoinId = "tether-gold"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Связываем UI
        tvGold = findViewById(R.id.tvGoldPrice)
        tvSilver = findViewById(R.id.tvSilverPrice)
        tvBtc = findViewById(R.id.tvBitcoinPrice)
        tvStat = findViewById(R.id.tvStatus)
        chart = findViewById(R.id.lineChart)

        setupChart()
        
        // Кнопки ПЕРИОДОВ (внизу)
        findViewById<Button>(R.id.btn1W).setOnClickListener { days = 7; updateChartOnly() }
        findViewById<Button>(R.id.btn1M).setOnClickListener { days = 30; updateChartOnly() }
        findViewById<Button>(R.id.btn1Y).setOnClickListener { days = 365; updateChartOnly() }
        findViewById<Button>(R.id.btn5Y).setOnClickListener { days = 1825; updateChartOnly() }
        
        // Кнопка ОБНОВИТЬ (всё сразу)
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { updateAll() }
        
        // ИНТЕРАКТИВНЫЕ ИКОНКИ (вверху)
        findViewById<LinearLayout>(R.id.layoutGold).setOnClickListener { switchAsset("tether-gold") }
        findViewById<LinearLayout>(R.id.layoutSilver).setOnClickListener { switchAsset("kinesis-silver") }
        findViewById<LinearLayout>(R.id.layoutBtc).setOnClickListener { switchAsset("bitcoin") }
        
        updateAll() // Первый запуск
    }

    private fun setupChart() {
        chart?.let {
            it.description.isEnabled = false; it.xAxis.textColor = Color.WHITE
            it.axisLeft.textColor = Color.WHITE; it.axisRight.isEnabled = false
            it.legend.textColor = Color.WHITE; it.setNoDataText("Загрузка графика...")
        }
    }

    private fun updateAll() {
        fetchPrices()    // Шаг 1: Текущие цены
        updateChartOnly() // Шаг 2: График выбранного актива
    }
    
    private fun switchAsset(coinId: String) {
        selectedCoinId = coinId
        chart?.clear() // Очищаем старый график перед загрузкой
        tvStat?.text = "Загрузка графика..."
        updateChartOnly()
    }

    private fun fetchPrices() {
        val urlP = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"
        client.newCall(Request.Builder().url(urlP).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Ошибка цен", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val j = JSONObject(data)
                        // Биткоин - целое евро
                        tvBtc?.text = String.format("€%,.0f", j.getJSONObject("bitcoin").getDouble("eur"))
                        // Металлы - Kg евро
                        tvGold?.text = String.format("€%,.0f", j.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG)
                        tvSilver?.text = String.format("€%,.0f", j.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Ошибка обработки цен", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun updateChartOnly() {
        fetchHistory(selectedCoinId)
    }

    private fun fetchHistory(coinId: String) {
        findViewById<Button>(R.id.btnRefresh).isEnabled = false
        tvStat?.text = "Загрузка..."
        
        val urlH = "https://api.coingecko.com/api/v3/coins/$coinId/market_chart?vs_currency=eur&days=$days"
        client.newCall(Request.Builder().url(urlH).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { 
                    tvStat?.text = "Ошибка графика"
                    Toast.makeText(this@MainActivity, "Нет сети для графика", Toast.LENGTH_SHORT).show()
                    findViewById<Button>(R.id.btnRefresh).isEnabled = true
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string() ?: ""
                runOnUiThread {
                    findViewById<Button>(R.id.btnRefresh).isEnabled = true
                    try {
                        val p = JSONObject(data).getJSONArray("prices")
                        val entries = ArrayList<Entry>()
                        for (i in 0 until p.length()) {
                            val pt = p.getJSONArray(i)
                            // Если это металл - пересчитываем унцию в КГ. Если БТЦ - оставляем цену.
                            var priceValue = pt.getDouble(1)
                            if (coinId.contains("tether") || coinId.contains("kinesis")) {
                                priceValue *= OZ_TO_KG
                            }
                            entries.add(Entry(pt.getLong(0).toFloat(), priceValue.toFloat()))
                        }
                        showChart(entries, coinId)
                        tvStat?.text = "Период: $days дн."
                    } catch (e: Exception) {
                        tvStat?.text = "Ошибка истории"
                    }
                }
            }
        })
    }

    private fun showChart(entries: ArrayList<Entry>, coinId: String) {
        // Выбираем цвет линии в зависимости от металла
        val lineColor = when {
            coinId.contains("gold") -> Color.parseColor("#FFD700") // Золотой
            coinId.contains("silver") -> Color.parseColor("#E0E0E0") // Серебряный
            else -> Color.parseColor("#F7931A") // Биткоин - Оранжевый
        }
        
        val set = LineDataSet(entries, "").apply {
            color = lineColor; setDrawCircles(false); setDrawValues(false)
            lineWidth = 2f; setDrawFilled(true); fillColor = lineColor; fillAlpha = 45
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart?.let {
            it.data = LineData(set)
            it.xAxis.valueFormatter = object : ValueFormatter() {
                val sdf = SimpleDateFormat("dd/MM", Locale.GERMANY)
                override fun getFormattedValue(v: Float) = sdf.format(Date(v.toLong()))
            }
            it.legend.isEnabled = false // Отключаем легенду, так как у нас интерактивные иконки
            it.invalidate()
        }
    }
}
