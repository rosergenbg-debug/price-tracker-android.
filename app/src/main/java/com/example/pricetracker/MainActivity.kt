package com.example.pricetracker
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private var lineChart: LineChart? = null
    private var tvGoldPrice: TextView? = null
    private var tvSilverPrice: TextView? = null
    private var tvBitcoinPrice: TextView? = null
    private var tvStatus: TextView? = null
    private var btnRefresh: Button? = null
    
    private val scriptUrl = "https://script.google.com/macros/s/AKfycbzH0t-zQzLuMbZs_mlPMlicir7BZNumgVOMkx7yjsHgN6qLNz17KxFGr9znketNLxjL/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        lineChart = findViewById(R.id.lineChart)
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)
        lineChart?.marker = CustomMarkerView(this, R.layout.marker_view)
        
        val timeButtons = listOf(
            findViewById<Button>(R.id.btn1D),
            findViewById<Button>(R.id.btn1W),
            findViewById<Button>(R.id.btn1M),
            findViewById<Button>(R.id.btn1Y),
            findViewById<Button>(R.id.btn3Y)
        )
        timeButtons.forEach { button ->
            button?.setOnClickListener { clickedBtn ->
                timeButtons.forEach { it?.setBackgroundColor(Color.parseColor("#AB47BC")) }
                clickedBtn.setBackgroundColor(Color.parseColor("#4CAF50"))
                tvStatus?.text = "Lade Daten..."
                fetchData()
            }
        }
        btnRefresh?.setOnClickListener {
            tvStatus?.text = "Aktualisiere..."
            fetchData()
        }
    }
    
    private fun fetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // TODO: логика запроса к серверу
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "Aktualisiert"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "Fehler"
                }
            }
        }
    }
}
