package com.example.pricetracker
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "БАЗА РАБОТАЕТ. ЕСЛИ ВИДИШЬ ЭТО - ПИШИ МНЕ!"
        tv.textSize = 24f
        setContentView(tv)
    }
}
