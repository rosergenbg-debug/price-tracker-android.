package com.example.pricetracker

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvGoldPrice: TextView
    private lateinit var tvSilverPrice: TextView
    private lateinit var tvBitcoinPrice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button

    private val client = OkHttpClient()
    // Коэффициент пересчета: сколько тройских унций в 1 килограмме
    private val OZ_TO_KG = 32.1507466

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener { fetchPrices() }
        fetchPrices()
    }

    private fun fetchPrices() {
        tvStatus.text = "Связь с биржей..."
        btnRefresh.isEnabled = false

        // Теперь запрашиваем цены именно в EUR
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=eur"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvStatus.text = "Ошибка: нет сети"
                    btnRefresh.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                
                runOnUiThread {
                    if (response.isSuccessful && responseData != null) {
                        try {
                            val json = JSONObject(responseData)
                            
                            // 1. Биткоин просто в евро
                            val btcPriceEur = json.getJSONObject("bitcoin").getDouble("eur")
                            
                            // 2. Золото и серебро: берем цену за унцию и умножаем на 32.1507
                            val goldPriceEurKg = json.getJSONObject("tether-gold").getDouble("eur") * OZ_TO_KG
                            val silverPriceEurKg = json.getJSONObject("kinesis-silver").getDouble("eur") * OZ_TO_KG

                            // Выводим результат с символом Евро
                            tvBitcoinPrice.text = String.format(Locale.GERMANY, "€ %,.2f", btcPriceEur)
                            tvGoldPrice.text = String.format(Locale.GERMANY, "€ %,.0f", goldPriceEurKg)
                            tvSilverPrice.text = String.format(Locale.GERMANY, "€ %,.2f", silverPriceEurKg)
                            
                            tvStatus.text = "Цены актуальны (Евро/КГ)"
                        } catch (e: Exception) {
                            tvStatus.text = "Ошибка обработки JSON"
                        }
                    } else {
                        tvStatus.text = "Сервер занят"
                    }
                    btnRefresh.isEnabled = true
                }
            }
        })
    }
}
