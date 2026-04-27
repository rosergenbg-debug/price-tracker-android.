package com.example.pricetracker
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.util.Locale

class CustomMarkerView(context: Context, layoutResource: Int, private val currentPrice: Float) : MarkerView(context, layoutResource) {
    private val tvContent: TextView = findViewById(R.id.tvContent)
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null && currentPrice > 0) {
            val value = e.y
            val percent = ((value - currentPrice) / currentPrice) * 100
            val sign = if (percent > 0) "+" else ""
            tvContent.text = String.format(Locale.GERMAN, "€%,.0f\n%s%.2f%%", value, sign, percent)
        }
        super.refreshContent(e, highlight)
    }
    override fun getOffset(): MPPointF = MPPointF(-(width / 2f), -height.toFloat() - 20f)
}
