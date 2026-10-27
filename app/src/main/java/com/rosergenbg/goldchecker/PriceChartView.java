package com.rosergenbg.goldchecker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Compact Android Canvas chart, without an extra library, and without simulated values. */
public final class PriceChartView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private HistoryData.Series series;
    private final int strokeColor;
    private boolean german;
    private final float density;
    public PriceChartView(Context context, int strokeColor, boolean german) {
        super(context); this.strokeColor=strokeColor; this.german=german;
        density=getResources().getDisplayMetrics().density;setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    public void setSeries(HistoryData.Series data) { series=data;invalidate(); }
    public void setGerman(boolean de) { german=de;invalidate(); }
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((int)(168*density), MeasureSpec.EXACTLY));
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        final float left=43*density,right=getWidth()-10*density,top=12*density,bottom=getHeight()-25*density;
        p.setTypeface(android.graphics.Typeface.DEFAULT);p.setTextSize(10*density);p.setStrokeWidth(1*density);
        if(series==null || series.prices.length<2){p.setColor(0xFF92B4A4);p.setStyle(Paint.Style.FILL);c.drawText(german?"Kursdaten nicht verfügbar":"Price history unavailable",left,top+40*density,p);return;}
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(double v:series.prices){lo=Math.min(lo,v);hi=Math.max(hi,v);}
        double pad=Math.max((hi-lo)*0.12,Math.max(hi,1)*0.002);
        lo-=pad;hi+=pad;
        p.setColor(0xFF315545);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(0.8f*density);
        for(int i=0;i<=3;i++){float y=top+(bottom-top)*i/3f;c.drawLine(left,y,right,y,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(0xFF9BB9AA);p.setTextSize(9*density);
        for(int i=0;i<=3;i++){
            double value=hi-(hi-lo)*i/3.0;
            c.drawText(String.format(Locale.US,value>=1000?"%.0f":value>=100?"%.0f":"%.1f",value),3*density,top+(bottom-top)*i/3f+3*density,p);
        }
        long first=series.times[0],last=series.times[series.times.length-1];
        double xscale=(right-left)/Math.max(1.,last-first);
        Path path=new Path();
        for(int i=0;i<series.times.length;i++){
            float x=left+(float)((series.times[i]-first)*xscale);
            float y=top+(float)((hi-series.prices[i])/(hi-lo)*(bottom-top));
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        p.setColor(strokeColor);p.setStrokeWidth(2.2f*density);p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);p.setColor(strokeColor);
        float lastX=right,lastY=top+(float)((hi-series.prices[series.prices.length-1])/(hi-lo)*(bottom-top));
        c.drawCircle(lastX,lastY,3.2f*density,p);
        p.setColor(0xFF9BB9AA);p.setTextSize(9*density);
        SimpleDateFormat fmt=new SimpleDateFormat((last-first)<72L*3600_000L?"HH:mm":"dd.MM.yy",german?Locale.GERMANY:Locale.US);
        c.drawText(fmt.format(new Date(first)),left,bottom+15*density,p);
        String end=fmt.format(new Date(last));c.drawText(end,right-p.measureText(end),bottom+15*density,p);
    }
}