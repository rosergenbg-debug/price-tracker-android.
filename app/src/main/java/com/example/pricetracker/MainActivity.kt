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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Находим все элементы на экране по их ID
        tvGoldPrice = findViewById(R.id.tvGoldPrice)
        tvSilverPrice = findViewById(R.id.tvSilverPrice)
        tvBitcoinPrice = findViewById(R.id.tvBitcoinPrice)
        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        // Вешаем слушатель нажатий на кнопку
        btnRefresh.setOnClickListener {
            fetchPrices()
        }

        // Автоматически запускаем загрузку при старте приложения
        fetchPrices()
    }

    private fun fetchPrices() {
        tvStatus.text = "Обновление..."
        btnRefresh.isEnabled = false // Блокируем кнопку, чтобы не спамить запросами

        // Ссылка на бесплатный API CoinGecko
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,tether-gold,kinesis-silver&vs_currencies=usd"

        val request = Request.Builder().url(url).build()

        // Делаем фоновый асинхронный запрос в интернет
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Если нет интернета или сервер упал
                runOnUiThread {
                    tvStatus.text = "Ошибка сети: проверьте интернет"
                    btnRefresh.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                
                // Чтобы менять текст на экране, мы обязаны вернуться в UI-поток (runOnUiThread)
                runOnUiThread {
                    if (response.isSuccessful && responseData != null) {
                        try {
                            // Парсим JSON ответ от сервера
                            val json = JSONObject(responseData)
                            
                            val btcPrice = json.getJSONObject("bitcoin").getDouble("usd")
                            val goldPrice = json.getJSONObject("tether-gold").getDouble("usd")
                            val silverPrice = json.getJSONObject("kinesis-silver").getDouble("usd")

                            // Красиво форматируем числа с запятыми и знаком доллара
                            tvBitcoinPrice.text = String.format(Locale.US, "$ %,.2f", btcPrice)
                            tvGoldPrice.text = String.format(Locale.US, "$ %,.2f", goldPrice)
                            tvSilverPrice.text = String.format(Locale.US, "$ %,.2f", silverPrice)
                            
                            tvStatus.text = "Успешно обновлено"
                        } catch (e: Exception) {
                            tvStatus.text = "Ошибка обработки данных"
                        }
                    } else {
                        tvStatus.text = "Ошибка сервера"
                    }
                    btnRefresh.isEnabled = true // Разблокируем кнопку
                }
            }
        })
    }
}
