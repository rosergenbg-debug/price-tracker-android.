package com.example.pricetracker
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.DecimalFormat

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {
    private val tvContent: TextView? = findViewById(R.id.tvContent)
    private val format = DecimalFormat("##0.00")
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { currentPoint ->
            val dataSet = chartView?.data?.getDataSetByIndex(highlight?.dataSetIndex ?: 0)
            val lastPoint = dataSet?.getEntryForIndex(dataSet.entryCount - 1)
            val diffPercent = if (lastPoint != null && lastPoint.y != 0f) {
                ((currentPoint.y - lastPoint.y) / lastPoint.y) * 100
            } else 0f
            val sign = if (diffPercent > 0) "+" else ""
            tvContent?.text = "€${format.format(currentPoint.y)}\nΔ: $sign${format.format(diffPercent)}%"
        }
        super.refreshContent(e, highlight)
    }
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 20f)
    }
}
