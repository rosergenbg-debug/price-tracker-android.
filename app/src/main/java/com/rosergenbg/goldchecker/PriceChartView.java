package com.rosergenbg.goldchecker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Chart with both grid axes, period timestamps and genuine range statistics. */
public final class PriceChartView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private HistoryData.Series series;
    private final int strokeColor;
    private boolean german;
    private String period = "24h", currency = "EUR", unit = "kg";
    private double usdToEur = Double.NaN;
    private final float density;

    public PriceChartView(Context context,int color,boolean de){
        super(context);strokeColor=color;german=de;
        density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    public void setSeries(HistoryData.Series s){series=s;invalidate();}
    public void setPeriod(String value){period=value;invalidate();}
    public void setDisplay(String curr,String weight,double fx){
        currency=curr;unit=weight;usdToEur=fx;invalidate();
    }
    public void setGerman(boolean de){german=de;invalidate();}
    private float dp(float n){return n*density;}
    private double factor(){
        double grams = "kg".equals(unit) ? 1000.0 : "g".equals(unit) ? 1.0
            : "dwt".equals(unit) ? MetalMath.GRAMS_PER_TROY_OUNCE/20.0 : MetalMath.GRAMS_PER_TROY_OUNCE;
        double fx = "EUR".equals(currency) ? usdToEur : 1.0;
        if(!Double.isFinite(fx)||fx<=0)return Double.NaN;
        return grams/MetalMath.GRAMS_PER_TROY_OUNCE*fx;
    }
    private String priceString(double n){
        NumberFormat f=NumberFormat.getNumberInstance(german?Locale.GERMANY:Locale.US);
        f.setGroupingUsed(true);
        int digits=n>=1000?0:n>=10?2:3;
        f.setMaximumFractionDigits(digits);f.setMinimumFractionDigits(digits);
        return f.format(n);
    }
    private String timeLabel(long when){
        String pattern="24h".equals(period)?"HH:mm":"7d".equals(period)||"1mo".equals(period)?"dd.MM":"MM.yy";
        SimpleDateFormat fmt=new SimpleDateFormat(pattern,german?Locale.GERMANY:Locale.US);
        return fmt.format(new Date(when));
    }
    @Override protected void onMeasure(int w,int h){
        int height=MeasureSpec.makeMeasureSpec((int)dp(249),MeasureSpec.EXACTLY);
        super.onMeasure(w,height);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float left=dp(73),right=getWidth()-dp(9),top=dp(10),bottom=getHeight()-dp(33);
        if(right<=left+dp(60))return;
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setStrokeWidth(dp(1));
        if(series==null||series.prices.length<2){
            p.setColor(0xFF9CB6A5);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(12));
            c.drawText(german?"Keine historischen Daten":"No historical data",left,top+dp(48),p);
            return;
        }
        double mult=factor();
        if(!Double.isFinite(mult)){
            p.setColor(0xFFF0C080);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(11));
            c.drawText(german?"Währungskurs wird geladen":"Loading FX rate",left,top+dp(42),p);
            return;
        }
        double rawMin=Double.POSITIVE_INFINITY,rawMax=Double.NEGATIVE_INFINITY;
        for(double value:series.prices){rawMin=Math.min(rawMin,value);rawMax=Math.max(rawMax,value);}
        double current=series.prices[series.prices.length-1]*mult;
        double actualMin=rawMin*mult,actualMax=rawMax*mult;
        double padding=Math.max((actualMax-actualMin)*0.12,Math.max(actualMax,1)*0.002);
        double axisMin=actualMin-padding,axisMax=actualMax+padding;
        long start=series.times[0],end=series.times[series.times.length-1];
        float width=right-left,height=bottom-top;

        // Both horizontal and vertical guide lines, like the original Price Monitor.
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(0.85f));p.setColor(0xFF456054);
        for(int i=0;i<=4;i++){
            float y=top+height*i/4f;
            c.drawLine(left,y,right,y,p);
            float x=left+width*i/4f;
            c.drawLine(x,top,x,bottom,p);
        }
        p.setStyle(Paint.Style.FILL);p.setTextSize(dp(9.4f));p.setColor(0xFFAABCB2);
        for(int i=0;i<=4;i++){
            double value=axisMax-(axisMax-axisMin)*i/4.0;
            String label=priceString(value);
            c.drawText(label,left-dp(6)-p.measureText(label),top+height*i/4f+dp(3),p);
        }
        p.setTextSize(dp(9.2f));
        for(int i=0;i<=4;i++){
            long stamp=start+Math.round((end-start)*i/4.0);
            String label=timeLabel(stamp);
            float x=left+width*i/4f;
            float xText=Math.max(dp(2),Math.min(getWidth()-p.measureText(label)-dp(2),x-p.measureText(label)/2f));
            c.drawText(label,xText,bottom+dp(17),p);
        }

        Path curve=new Path();
        for(int i=0;i<series.prices.length;i++){
            float x=left+(float)((series.times[i]-start)/(double)Math.max(1,end-start))*width;
            float y=top+(float)((axisMax-series.prices[i]*mult)/(axisMax-axisMin))*height;
            if(i==0)curve.moveTo(x,y);else curve.lineTo(x,y);
        }
        Path fill=new Path(curve);
        fill.lineTo(right,bottom);fill.lineTo(left,bottom);fill.close();
        p.setStyle(Paint.Style.FILL);p.setColor((strokeColor & 0x00FFFFFF)|0x24000000);c.drawPath(fill,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.3f));
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(strokeColor);c.drawPath(curve,p);
        p.setStyle(Paint.Style.FILL);
        float lastY=top+(float)((axisMax-current)/(axisMax-axisMin))*height;
        c.drawCircle(right,lastY,dp(3.6f),p);

        // Opaque inset legend keeps all values readable without obscuring the curve too much.
        String[] legend={
            (german?"MIN ":"MIN ")+priceString(actualMin),
            (german?"MAX ":"MAX ")+priceString(actualMax),
            (german?"JETZT ":"NOW ")+priceString(current)
        };
        p.setTextSize(dp(10.2f));
        float w=dp(112);
        for(String s:legend)w=Math.max(w,p.measureText(s)+dp(18));
        float insetL=left+dp(6),insetT=top+dp(7),insetH=dp(58);
        p.setColor(0xEE0B1D17);
        c.drawRoundRect(new RectF(insetL,insetT,insetL+w,insetT+insetH),dp(8),dp(8),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(0.8f));p.setColor(0xFF678273);
        c.drawRoundRect(new RectF(insetL,insetT,insetL+w,insetT+insetH),dp(8),dp(8),p);
        p.setStyle(Paint.Style.FILL);p.setTextSize(dp(10.2f));
        for(int i=0;i<legend.length;i++){
            p.setColor(i==2?strokeColor:0xFFF0F7F2);
            c.drawText(legend[i],insetL+dp(8),insetT+dp(15+17*i),p);
        }
    }
}