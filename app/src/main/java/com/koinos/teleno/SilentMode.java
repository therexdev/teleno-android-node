package com.koinos.teleno;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Activity-only screen policy. Never starts/stops the node or changes device settings. */
final class SilentMode {
    final SilentModeView view;
    private final Activity activity;
    private final View dashboard;
    private final Runnable changed;
    private boolean active,foreground,applied,hadKeepScreenOn;
    private float previousBrightness;
    private int previousSystemUi,previousBarsBehavior,previousVisibleBars;
    private Runnable unregisterBack;

    SilentMode(Activity activity,FrameLayout stage,View dashboard,Runnable changed) {
        this.activity=activity;this.dashboard=dashboard;this.changed=changed;
        view=new SilentModeView(activity,this::exit);
        view.setVisibility(View.GONE);
        stage.addView(view,new FrameLayout.LayoutParams(-1,-1));
    }
    boolean active(){return active;}
    void enter(){if(active)return;active=true;dashboard.setVisibility(View.GONE);view.setVisibility(View.VISIBLE);apply();changed.run();}
    void exit(){if(!active)return;active=false;view.foreground(false);restore();view.setVisibility(View.GONE);dashboard.setVisibility(View.VISIBLE);changed.run();}
    void foreground(boolean value){foreground=value;if(value)apply();else{view.foreground(false);restore();}}
    void close(){view.foreground(false);restore();}
    void focus(){if(active&&foreground)hideBars();}
    private void apply(){
        if(!active||!foreground)return;
        Window window=activity.getWindow();
        if(!applied){
            WindowManager.LayoutParams p=window.getAttributes();
            previousBrightness=p.screenBrightness;
            hadKeepScreenOn=(p.flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
            previousSystemUi=window.getDecorView().getSystemUiVisibility();
            if(Build.VERSION.SDK_INT>=30){
                previousVisibleBars=WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars();
                WindowInsets insets=window.getDecorView().getRootWindowInsets();
                if(insets!=null){previousVisibleBars=0;if(insets.isVisible(WindowInsets.Type.statusBars()))previousVisibleBars|=WindowInsets.Type.statusBars();if(insets.isVisible(WindowInsets.Type.navigationBars()))previousVisibleBars|=WindowInsets.Type.navigationBars();}
                WindowInsetsController controller=window.getInsetsController();
                if(controller!=null)previousBarsBehavior=controller.getSystemBarsBehavior();
            }
            p.flags|=WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // Cap only this window at 5%; retain an already lower explicit brightness.
            p.screenBrightness=previousBrightness>=0?Math.min(previousBrightness,.05f):.05f;
            window.setAttributes(p);applied=true;
            if(Build.VERSION.SDK_INT>=33)unregisterBack=ModernBack.register(activity,this::exit);
        }
        hideBars();view.foreground(true);
    }
    @SuppressWarnings("deprecation") private void hideBars(){
        Window window=activity.getWindow();
        window.getDecorView().setSystemUiVisibility(previousSystemUi|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=window.getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}
    }
    @SuppressWarnings("deprecation") private void restore(){
        if(!applied)return;
        if(unregisterBack!=null){unregisterBack.run();unregisterBack=null;}
        Window window=activity.getWindow();
        WindowManager.LayoutParams p=window.getAttributes();p.screenBrightness=previousBrightness;
        if(hadKeepScreenOn)p.flags|=WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;else p.flags&=~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        window.setAttributes(p);window.getDecorView().setSystemUiVisibility(previousSystemUi);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=window.getInsetsController();if(c!=null){c.setSystemBarsBehavior(previousBarsBehavior);c.hide(WindowInsets.Type.systemBars()&~previousVisibleBars);c.show(previousVisibleBars);}}
        applied=false;
    }
    /** Kept separate so Android 9–12 never need to resolve Android 13 classes. */
    private static final class ModernBack {
        static Runnable register(Activity activity,Runnable exit){
            android.window.OnBackInvokedDispatcher dispatcher=activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback=exit::run;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);
            return ()->dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}
