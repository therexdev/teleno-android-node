package com.koinos.teleno;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Pure-black OLED screensaver. All illuminated content moves together, even
 * when Android's animation scale is disabled. No RPC or storage on animation ticks. */
final class SilentModeView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density, fontScale;
    private final long origin=SystemClock.elapsedRealtime();
    private boolean running,attached;
    private long clockMinute=Long.MIN_VALUE;
    private String clock="",latest="No confirmed rewards",latestTime="",total="No production run",count="",status="Node stopped";
    private DateFormat timeFormat;
    private final RectF box=new RectF();
    private int tint;
    private final Runnable frame=new Runnable(){@Override public void run(){if(!running||!attached)return;updateClock();invalidate();postDelayed(this,100);}};

    SilentModeView(Context context,Runnable exit){
        super(context);density=getResources().getDisplayMetrics().density;
        fontScale=getResources().getDisplayMetrics().scaledDensity/density;
        setTag("silent-mode");setBackgroundColor(Color.BLACK);setFocusable(true);setClickable(true);setOnClickListener(v->exit.run());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setTextAlign(Paint.Align.CENTER);
        timeFormat=android.text.format.DateFormat.getTimeFormat(context);updateClock();
    }
    void foreground(boolean value){
        running=value;removeCallbacks(frame);
        if(value){timeFormat=android.text.format.DateFormat.getTimeFormat(getContext());clockMinute=Long.MIN_VALUE;updateClock();if(attached)post(frame);}
    }
    boolean animating(){return running&&attached;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(running){removeCallbacks(frame);post(frame);}}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(Build.VERSION.SDK_INT>=28){DisplayCutout cutout=insets.getDisplayCutout();setPadding(cutout==null?0:cutout.getSafeInsetLeft(),cutout==null?0:cutout.getSafeInsetTop(),cutout==null?0:cutout.getSafeInsetRight(),cutout==null?0:cutout.getSafeInsetBottom());}
        return insets;
    }
    void bind(NodeService.ViewState node,DashboardController data){
        WalletReader.Entry reward=data.rewards.isEmpty()?null:data.rewards.get(0);
        latest=reward==null?(data.account.isEmpty()?"No wallet selected":data.historyBusy?"Checking rewards…":"No confirmed rewards"):(reward.koin.startsWith("-")?"":"+")+WalletReader.amount(reward.koin)+" KOIN";
        latestTime=reward==null?"":reward.time>0?android.text.format.DateFormat.getMediumDateFormat(getContext()).format(new Date(reward.time))+" · "+timeFormat.format(new Date(reward.time)):"Reward time unavailable";
        total=data.runStartedAt<=0?"No production run":!data.runRewardsReady?"Checking rewards…":WalletReader.amount(data.runRewards.koin.toString())+" KOIN";
        count=data.runStartedAt>0&&data.runRewardsReady?String.format(Locale.getDefault(),"%d confirmed reward%s",data.runRewards.count,data.runRewards.count==1?"":"s"):"";
        long now=System.currentTimeMillis();
        boolean scoped=data.network.equals(node.network);
        boolean fresh=scoped&&node.hasSample&&now>=node.sampleAt&&now-node.sampleAt<30000;
        status=!node.active?(node.busy?"Node working":"Node stopped"):!fresh?"Checking node status":!node.ready?"Node syncing":node.peers==0?"No peers connected":NodeService.PRODUCER.equals(node.mode)?(node.production.advancing(SystemClock.elapsedRealtime())?"Producing":"Check production in Setup"):"Observer running";
        if(!data.historyError.isEmpty())status+=" · Saved rewards";
        accessibility();invalidate();
    }
    private void updateClock(){long now=System.currentTimeMillis();if(now/60000!=clockMinute){clockMinute=now/60000;clock=timeFormat.format(new Date(now));accessibility();}}
    private void accessibility(){setContentDescription("Silent Mode. "+clock+". Latest reward: "+latest+". "+latestTime+". Rewards this run: "+total+". "+count+". "+status+". Tap anywhere to return to the dashboard.");}
    /** Triangle reflection keeps the entire box inside the available display indefinitely. */
    private static float bounce(double elapsed,float distance,float speed,float phase){if(distance<=0)return 0;double p=(elapsed*speed+distance*phase)%(2*distance);return (float)(p<=distance?p:2*distance-p);}
    RectF boxAt(long elapsedMillis){
        float availableWidth=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight()),availableHeight=Math.max(1,getHeight()-getPaddingTop()-getPaddingBottom());
        float scale=Math.min(density,Math.min(availableWidth*.84f/280f,availableHeight*.74f/246f));
        float w=280*scale,h=246*scale,margin=Math.min(12*density,Math.min((availableWidth-w)/2,(availableHeight-h)/2));
        double seconds=Math.max(0,elapsedMillis)/1000.0;
        float x=getPaddingLeft()+margin+bounce(seconds,Math.max(0,availableWidth-w-2*margin),3.7f*density,.31f);
        float y=getPaddingTop()+margin+bounce(seconds,Math.max(0,availableHeight-h-2*margin),2.9f*density,.57f);
        return new RectF(x,y,x+w,y+h);
    }
    static int colorAt(long elapsedMillis){return Color.HSVToColor(new float[]{(150f+(Math.max(0,elapsedMillis)%720000)/2000f)%360f,.52f,.67f});}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(Color.BLACK);
        long elapsed=Math.max(0,SystemClock.elapsedRealtime()-origin);box.set(boxAt(elapsed));tint=colorAt(elapsed);
        float scale=box.width()/280f;
        paint.setColor(tint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,scale));canvas.drawRoundRect(box,12*scale,12*scale,paint);
        paint.setStyle(Paint.Style.FILL);
        line(canvas,clock,43,34,true,scale);
        line(canvas,"LATEST REWARD",72,10,false,scale);
        line(canvas,latest,98,19,true,scale);
        line(canvas,latestTime,119,11,false,scale);
        line(canvas,"REWARDS THIS RUN",151,10,false,scale);
        line(canvas,total,179,22,true,scale);
        line(canvas,count,199,11,false,scale);
        line(canvas,status,228,10,false,scale);
    }
    private void line(Canvas canvas,String text,float baseline,float size,boolean bold,float scale){
        paint.setTypeface(bold?Typeface.create("sans-serif-medium",Typeface.NORMAL):Typeface.create("sans-serif",Typeface.NORMAL));
        // Respect enlarged text where it fits; never truncate a token amount or timestamp.
        paint.setTextSize(size*scale*Math.min(fontScale,1.15f));float width=paint.measureText(text),max=box.width()-28*scale;
        if(width>max&&width>0)paint.setTextSize(paint.getTextSize()*max/width);
        paint.setColor(tint);canvas.drawText(text,box.centerX(),box.top+baseline*scale,paint);
    }
}
