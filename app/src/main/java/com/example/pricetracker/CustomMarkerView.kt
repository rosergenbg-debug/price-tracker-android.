package com.example.pricetracker
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CustomMarkerView(
    context: Context,
    layoutResource: Int,
    private val basePrice: Float,
    private val unitSuffix: String,
    private val firstTimeMs: Long,
    selectedDays: Int
) : MarkerView(context, layoutResource) {
    private val tvContent: TextView? = findViewById(R.id.tvContent)
    private val dateFormat = if (selectedDays == 1) {
        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
    } else {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null && basePrice > 0f) {
            val value = e.y
            val percent = ((value - basePrice) / basePrice) * 100
            val sign = if (percent > 0) "+" else ""
            val price = if (value >= 1000f) {
                String.format(Locale.GERMAN, "EUR %,.0f%s", value, unitSuffix)
            } else {
                String.format(Locale.GERMAN, "EUR %,.2f%s", value, unitSuffix)
            }
            val time = firstTimeMs + (e.x * TimeUnit.HOURS.toMillis(1)).toLong()
            tvContent?.text = String.format(
                Locale.GERMAN,
                "%s\n%s\n%s%.2f%%",
                dateFormat.format(Date(time)),
                price,
                sign,
                percent
            )
        }
        super.refreshContent(e, highlight)
    }
    override fun getOffset(): MPPointF = MPPointF(-(width / 2f), -height.toFloat() - 25f)
}
