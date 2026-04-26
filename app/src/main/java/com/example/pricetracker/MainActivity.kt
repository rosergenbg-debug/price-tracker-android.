package com.example.pricetracker

import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private var currentAsset = "bitcoin"
    private var tvPrice: TextView? = null
    private var vLineBtc: View? = null
    private var vLineGold: View? = null
    private var vLineSilver: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvPrice = findViewById(R.id.tvPrice)
        vLineBtc = findViewById(R.id.lineBtc)
        vLineGold = findViewById(R.id.lineGold)
        vLineSilver = findViewById(R.id.lineSilver)

        val btnBtc = findViewById<View>(R.id.layoutBtc)
        val btnGold = findViewById<View>(R.id.layoutGold)
        val btnSilver = findViewById<View>(R.id.layoutSilver)

        // Оживляем кнопку Биткоина
        btnBtc.setOnClickListener {
            currentAsset = "bitcoin"
            updateUI()
            fetchData()
        }

        btnGold.setOnClickListener {
            currentAsset = "tether-gold"
            updateUI()
            fetchData()
        }

        btnSilver.setOnClickListener {
            currentAsset = "kinesis-silver"
            updateUI()
            fetchData()
        }

        updateUI()
        fetchData()
    }

    private fun updateUI() {
        // Сбрасываем все полоски в серый цвет
        vLineBtc?.setBackgroundColor(Color.parseColor("#333333"))
        vLineGold?.setBackgroundColor(Color.parseColor("#333333"))
        vLineSilver?.setBackgroundColor(Color.parseColor("#333333"))

        // Подсвечиваем только активную нужным цветом
        when (currentAsset) {
            "bitcoin" -> vLineBtc?.setBackgroundColor(Color.parseColor("#F7931A")) // Оранжевый
            "tether-gold" -> vLineGold?.setBackgroundColor(Color.parseColor("#FFD700")) // Золотой
            "kinesis-silver" -> vLineSilver?.setBackgroundColor(Color.parseColor("#C0C0C0")) // Серебряный
        }
    }

    private fun fetchData() {
        // Здесь твоя рабочая функция запроса к Google Server
        // Она остается без изменений, так как работает хорошо
    }
}
