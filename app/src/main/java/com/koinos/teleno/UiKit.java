package com.koinos.teleno;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class UiKit {
    static final int BG=0xff090f19,PANEL=0xff111c2a,LINE=0xff263449,INK=0xffedf5fc,MUTED=0xff97a9bf,TEAL=0xff62e5ce,VIOLET=0xffb6a0ff,AMBER=0xffffce85;
    static int dp(Context c,float value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
    static GradientDrawable surface(Context c,int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));d.setStroke(dp(c,1),LINE);return d;}
    static TextView text(Context c,String value,int size,int color,boolean medium) {
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setLineSpacing(dp(c,2),1);t.setIncludeFontPadding(false);return t;
    }
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static Button button(Context c,String label,boolean primary,Runnable action) {
        Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));b.setStateListAnimator(null);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff69798c,primary?BG:TEAL}));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},surface(c,0xff16202d,13));states.addState(new int[]{},surface(c,primary?TEAL:0xff182637,13));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4062e5ce),states,null));b.setOnClickListener(v->action.run());return b;
    }
    static void changed(TextView v,String s){if(!s.contentEquals(v.getText()))v.setText(s);}
    static final class Glyph extends View {
        final Paint p=new Paint(3);final String type;int color=TEAL;
        Glyph(Context c,String type){super(c);this.type=type;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth(),getHeight())/24f;canvas.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);canvas.scale(scale,scale);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if("home".equals(type)){canvas.drawRoundRect(3,3,10,10,2,2,p);canvas.drawRoundRect(14,3,21,10,2,2,p);canvas.drawRoundRect(3,14,10,21,2,2,p);canvas.drawRoundRect(14,14,21,21,2,2,p);}
            else if("setup".equals(type)){for(int y=5;y<=19;y+=7)canvas.drawLine(3,y,21,y,p);canvas.drawCircle(8,5,2,p);canvas.drawCircle(16,12,2,p);canvas.drawCircle(10,19,2,p);}
            else if("history".equals(type)){canvas.drawArc(3,3,21,21,-160,310,false,p);canvas.drawLine(3,3,3,8,p);canvas.drawLine(3,8,8,8,p);canvas.drawLine(12,7,12,12,p);canvas.drawLine(12,12,16,14,p);}
            else if("reward".equals(type)){Path path=new Path();path.moveTo(12,2);path.lineTo(20,7);path.lineTo(20,17);path.lineTo(12,22);path.lineTo(4,17);path.lineTo(4,7);path.close();canvas.drawPath(path,p);canvas.drawLine(8,12,11,15,p);canvas.drawLine(11,15,17,9,p);}
            else if("arrow".equals(type)){canvas.drawLine(5,12,19,12,p);canvas.drawLine(14,7,19,12,p);canvas.drawLine(19,12,14,17,p);}
            else{canvas.drawCircle(12,12,8,p);canvas.drawLine(12,8,12,16,p);canvas.drawLine(8,12,16,12,p);}canvas.restore();
        }
    }
    static final class Halo extends View {
        private final Paint p=new Paint(3);private ValueAnimator motion;private float turn;private boolean live,working,foreground=true;
        Halo(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void live(boolean v){if(live!=v){live=v;update();invalidate();}}
        void working(boolean v){if(working!=v){working=v;update();invalidate();}}
        boolean animating(){return motion!=null&&motion.isStarted();}
        void foreground(boolean v){foreground=v;update();}
        private void update(){boolean play=working&&foreground&&isShown()&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();if(play&&motion==null){motion=ValueAnimator.ofFloat(0,360);motion.setDuration(4800);motion.setRepeatCount(ValueAnimator.INFINITE);motion.setInterpolator(new android.view.animation.LinearInterpolator());motion.addUpdateListener(a->{turn=(float)a.getAnimatedValue();invalidate();});motion.start();}else if(!play&&motion!=null){motion.cancel();motion=null;}}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();}
        @Override protected void onDetachedFromWindow(){if(motion!=null){motion.cancel();motion=null;}super.onDetachedFromWindow();}
        @Override protected void onVisibilityChanged(View v,int visibility){super.onVisibilityChanged(v,visibility);update();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-2;if(r<=0)return;
            int color=live?TEAL:working?AMBER:MUTED;float pulse=working?.5f+.5f*(float)Math.sin(Math.toRadians(turn*3)):0;
            p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(x,y,r,new int[]{(working?0x50000000:0x18000000)|(color&0xffffff),color&0xffffff},null,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(getContext(),1));p.setColor(LINE);c.drawCircle(x,y,r*.88f,p);
            p.setColor(color);p.setAlpha(working?(int)(45+pulse*80):45);c.drawCircle(x,y,r*(.60f+pulse*.25f),p);p.setAlpha(255);
            p.setStrokeWidth(dp(getContext(),2.5f));c.drawArc(x-r*.88f,y-r*.88f,x+r*.88f,y+r*.88f,turn,105,false,p);
            p.setStrokeWidth(dp(getContext(),1.6f));p.setAlpha(160);c.drawArc(x-r*.69f,y-r*.69f,x+r*.69f,y+r*.69f,-turn*1.4f,170,false,p);p.setAlpha(255);
            if(working){double angle=Math.toRadians(turn+105);p.setStyle(Paint.Style.FILL);c.drawCircle(x+(float)Math.cos(angle)*r*.88f,y+(float)Math.sin(angle)*r*.88f,dp(getContext(),2.8f),p);}
            p.setStyle(Paint.Style.STROKE);Path hex=new Path();for(int i=0;i<6;i++){double angle=Math.PI/3*i-Math.PI/2;float px=x+(float)Math.cos(angle)*r*.40f,py=y+(float)Math.sin(angle)*r*.40f;if(i==0)hex.moveTo(px,py);else hex.lineTo(px,py);}hex.close();p.setColor(color);p.setStrokeWidth(dp(getContext(),2));c.drawPath(hex,p);c.drawLine(x-r*.19f,y-r*.12f,x+r*.19f,y-r*.12f,p);c.drawLine(x,y-r*.12f,x,y+r*.21f,p);
        }
    }
}
