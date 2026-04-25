package com.example.pricetracker
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "СИСТЕМА ЗАПУЩЕНА\nВерсия 1.2"
        tv.textSize = 24f
        tv.setPadding(50, 50, 50, 50)
        setContentView(tv)
    }
}
