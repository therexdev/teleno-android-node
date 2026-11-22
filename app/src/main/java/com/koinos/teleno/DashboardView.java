package com.koinos.teleno;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
import static com.koinos.teleno.UiKit.*;

/** One-screen overview with node controls and an optional awake screensaver. */
final class DashboardView extends LinearLayout {
    final Activity activity;
    final boolean landscape;
    final TextView clock,date,network,badge,phase,koin,vhp,mana,balanceState,block,peers,produced,temp,thermal,storage,memory,power,feedState;
    final ProgressBar manaBar;
    final Button toggle, silent;
    final Halo halo;
    final LinearLayout rewardFeed;
    private boolean foreground=true;
    private String feedKey="";

    DashboardView(Activity a,Runnable action,Runnable silentAction){
        super(a);activity=a;landscape=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        setOrientation(VERTICAL);setPadding(d(landscape?24:20),d(12),d(landscape?24:20),d(12));setTag("home-dashboard");
        LinearLayout header=row(a),identity=column(a),time=column(a);identity.setGravity(Gravity.CENTER_VERTICAL);time.setGravity(Gravity.RIGHT);
        TextView brand=label("TELENO LITE",TEAL);brand.setTextSize(12);brand.setLetterSpacing(.22f);identity.addView(brand);
        network=label("MAINNET",MUTED);network.setTextSize(10);gap(identity,5);identity.addView(network);
        header.addView(identity,new LayoutParams(0,-1,1));clock=value("—",28,"home-clock");clock.setGravity(Gravity.RIGHT);time.addView(clock,new LayoutParams(-1,d(32)));
        date=text(a,"",11,MUTED,false);date.setSingleLine(true);date.setGravity(Gravity.RIGHT);time.addView(date);
        header.addView(time,new LayoutParams(d(174),-1));addView(header,new LayoutParams(-1,d(49)));

        LinearLayout status=row(a);status.setTag("home-status");status.setGravity(Gravity.CENTER_VERTICAL);
        halo=new Halo(a);halo.setTag("home-running-animation");status.addView(halo,new LayoutParams(d(landscape?80:76),d(landscape?80:76)));
        LinearLayout words=column(a);words.setGravity(Gravity.CENTER_VERTICAL);LayoutParams wp=new LayoutParams(0,-1,1);wp.leftMargin=d(14);status.addView(words,wp);
        badge=label("STOPPED",MUTED);badge.setTag("home-health");words.addView(badge);gap(words,6);
        phase=text(a,"Ready when you are",landscape?22:21,INK,true);phase.setMaxLines(2);phase.setAutoSizeTextTypeUniformWithConfiguration(15,landscape?22:21,1,android.util.TypedValue.COMPLEX_UNIT_SP);phase.setTag("home-phase");words.addView(phase,new LayoutParams(-1,d(54)));

        LinearLayout wallet=column(a);wallet.setGravity(Gravity.CENTER_VERTICAL);LinearLayout kl=row(a);kl.addView(label("KOIN",TEAL),new LayoutParams(0,-2,1));balanceState=text(a,"",10,AMBER,false);kl.addView(balanceState);wallet.addView(kl);
        koin=value("—",landscape?40:42,"home-koin");wallet.addView(koin,new LayoutParams(-1,d(landscape?48:50)));gap(wallet,landscape?4:8);wallet.addView(label("VHP",VIOLET));
        vhp=value("—",landscape?30:30,"home-vhp");wallet.addView(vhp,new LayoutParams(-1,d(38)));

        LinearLayout manaBox=column(a);manaBox.setGravity(Gravity.CENTER_VERTICAL);LinearLayout mr=row(a);mr.addView(label("MANA",MUTED),new LayoutParams(0,-2,1));mana=text(a,"—",13,VIOLET,true);mana.setTag("home-mana");mr.addView(mana);manaBox.addView(mr);gap(manaBox,7);
        manaBar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);manaBar.setMax(100);manaBar.setProgressTintList(android.content.res.ColorStateList.valueOf(VIOLET));manaBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));manaBox.addView(manaBar,new LayoutParams(-1,d(4)));

        LinearLayout chain=row(a);chain.setGravity(Gravity.CENTER_VERTICAL);chain.setTag("home-chain");
        block=metric(chain,"CURRENT BLOCK",32,"home-block",2.05f);peers=metric(chain,"PEERS",26,"home-peers",.60f);produced=metric(chain,"BLOCKS / RUN",26,"home-produced",1.20f);
        produced.setContentDescription("Local blocks produced this run. Confirmed rewards are listed separately.");

        LinearLayout device=landscape?row(a):column(a);device.setGravity(landscape?Gravity.CENTER_VERTICAL:Gravity.CENTER_VERTICAL|Gravity.RIGHT);device.setTag("home-device");
        temp=deviceMetric(device,"BATTERY TEMP",24,"home-temperature");thermal=text(a,"",10,TEAL,false);
        // A short thermal state is kept with temperature, rather than a second device panel.
        // Thermal detail stays in the accessible temperature description.
        storage=deviceMetric(device,"STORAGE FREE",20,"home-storage");memory=deviceMetric(device,"RAM FREE",20,"home-memory");

        LinearLayout rewards=column(a);rewards.setTag("home-rewards");rewards.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout rh=row(a);rh.addView(label("REWARDS",MUTED),new LayoutParams(0,-2,1));feedState=text(a,"Confirmed",10,TEAL,false);rh.addView(feedState);rewards.addView(rh);gap(rewards,5);
        rewardFeed=column(a);rewards.addView(rewardFeed,new LayoutParams(-1,0,1));

        if(landscape){
            LinearLayout body=row(a),left=column(a),right=column(a);LayoutParams lp=new LayoutParams(0,-1,1.17f);lp.rightMargin=d(30);body.addView(left,lp);body.addView(right,new LayoutParams(0,-1,1));
            weighted(left,status,.45f);weighted(left,chain,.27f);weighted(left,device,.28f);
            weighted(right,wallet,.69f);right.addView(manaBox,new LayoutParams(-1,d(35)));weighted(right,rewards,.31f);
            weighted(this,body,1);
        }else{
            weighted(this,status,.24f);
            LinearLayout middle=row(a);middle.setGravity(Gravity.CENTER_VERTICAL);LayoutParams bp=new LayoutParams(0,-1,1.55f);bp.rightMargin=d(24);middle.addView(wallet,bp);middle.addView(device,new LayoutParams(0,-1,1));
            weighted(this,middle,.40f);addView(manaBox,new LayoutParams(-1,d(38)));weighted(this,chain,.19f);weighted(this,rewards,.17f);
        }
        LinearLayout footer=landscape?row(a):column(a);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(0,d(6),0,0);
        power=text(a,"",10,MUTED,false);power.setTag("home-power");power.setSingleLine(true);
        toggle=button(a,"Start node",true,action);toggle.setTag("home-toggle");
        silent=button(a,"Silent Mode",false,silentAction);silent.setTag("home-silent-mode");
        silent.setContentDescription("Silent Mode. Dim moving clock and rewards; keeps the screen awake. Tap anywhere to return.");
        LinearLayout actions=row(a);LayoutParams sp=new LayoutParams(0,d(48),1);sp.rightMargin=d(8);actions.addView(silent,sp);actions.addView(toggle,new LayoutParams(0,d(48),1));
        if(landscape){footer.addView(power,new LayoutParams(0,-2,1));footer.addView(actions,new LayoutParams(d(292),d(48)));addView(footer,new LayoutParams(-1,d(54)));}
        else{power.setGravity(Gravity.CENTER);footer.addView(power,new LayoutParams(-1,d(16)));footer.addView(actions,new LayoutParams(-1,d(48)));addView(footer,new LayoutParams(-1,d(70)));}
    }
    private int d(int n){return dp(activity,n);}
    private void gap(LinearLayout parent,int height){parent.addView(new View(activity),new LayoutParams(1,d(height)));}
    private void weighted(LinearLayout parent,View child,float weight){parent.addView(child,new LayoutParams(-1,0,weight));}
    private TextView label(String title,int color){TextView v=text(activity,title,10,color,true);v.setSingleLine(true);v.setLetterSpacing(.10f);return v;}
    private TextView value(String text,int max,String tag){TextView v=text(activity,text,max,INK,true);v.setTag(tag);v.setMaxLines(1);v.setGravity(Gravity.CENTER_VERTICAL);v.setAutoSizeTextTypeUniformWithConfiguration(Math.min(14,max-1),max,1,android.util.TypedValue.COMPLEX_UNIT_SP);return v;}
    private TextView metric(LinearLayout parent,String title,int max,String tag,float weight){LinearLayout c=column(activity);c.setGravity(Gravity.CENTER_VERTICAL);LayoutParams p=new LayoutParams(0,-1,weight);p.rightMargin=d(8);parent.addView(c,p);TextView name=label(title,MUTED);name.setHorizontallyScrolling(false);name.setMaxLines(1);name.setAutoSizeTextTypeUniformWithConfiguration(8,10,1,android.util.TypedValue.COMPLEX_UNIT_SP);c.addView(name,new LayoutParams(-1,d(13)));TextView v=value("—",max,tag);c.addView(v,new LayoutParams(-1,d(38)));return v;}
    private TextView deviceMetric(LinearLayout parent,String title,int size,String tag){LinearLayout c=column(activity);c.setGravity(landscape?Gravity.CENTER_VERTICAL:Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        parent.addView(c,landscape?new LayoutParams(0,-1,1):new LayoutParams(-1,0,1));TextView label=label(title,MUTED);if(!landscape)label.setGravity(Gravity.RIGHT);c.addView(label,new LayoutParams(-1,-2));TextView v=value("—",size,tag);if(!landscape)v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);c.addView(v,new LayoutParams(-1,d(29)));return v;}
    void foreground(boolean value){foreground=value;halo.foreground(value);}
    void bind(NodeService.ViewState node,DashboardController data,boolean canStart,boolean canStop,String selectedNetwork,String account,long free){
        long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();boolean scoped=selectedNetwork.equals(node.network);
        boolean fresh=scoped&&node.active&&node.hasSample&&now>=node.sampleAt&&now-node.sampleAt<30000;
        boolean producer=NodeService.PRODUCER.equals(node.mode),accountMatches=!producer||node.producerAccount.equals(account);
        boolean healthy=fresh&&node.ready&&accountMatches&&(!producer||node.production.advancing(elapsed));
        changed(clock,android.text.format.DateFormat.getTimeFormat(activity).format(new Date(now)));changed(date,new SimpleDateFormat("EEE, MMM d",Locale.getDefault()).format(new Date(now)));
        changed(network,"mainnet".equals(selectedNetwork)?"MAINNET":"HARBINGER");
        changed(badge,healthy?"●  HEALTHY · "+(producer?"PRODUCER":"OBSERVER"):node.active?"●  "+(node.readinessState==Readiness.State.NETWORK?"NETWORK CHECK":!fresh?"STATUS CHECK":!node.ready?node.readinessState.badge:"NEEDS ATTENTION"):node.busy?"●  WORKING":"○  STOPPED");
        badge.setTextColor(healthy?TEAL:node.active||node.busy?AMBER:MUTED);
        changed(phase,node.active?node.readinessState==Readiness.State.NETWORK?node.readinessState.title:!fresh?"Waiting for fresh status":!accountMatches?"Producer account differs":!node.ready?node.readinessState.title:producer?node.production.title(elapsed):"Validating the chain":node.busy?node.phase:"Ready when you are");
        halo.live(healthy);halo.working(node.active||node.busy);halo.foreground(foreground);
        changed(block,scoped&&node.hasSample?NumberFormat.getIntegerInstance().format(node.height):"—");block.setTextColor(fresh?INK:MUTED);
        changed(peers,scoped&&node.peerCountPending?"Live":scoped&&node.peers>=0?Integer.toString(node.peers):"—");
        peers.setContentDescription(node.peerCountPending?"A newer native connection event confirms a peer; the exact count is pending.":"Last reported connected peers: "+(node.peers<0?"unknown":node.peers));
        changed(produced,scoped?Long.toString(node.localBlocks):"—");
        changed(toggle,node.active?"Stop node":node.busy?"Stop task":"Start node");toggle.setEnabled(node.active||node.busy?canStop:canStart);
        toggle.setContentDescription(node.active?"Stop the local node":node.busy?"Stop the current node task":(LiteRuntime.ENABLED?"Start the compact local node and continue setup.":"Show Teleno Node Lite preview status. Compact mining is not available yet."));
        WalletReader.Balance b=data.balance;boolean saved=b!=null&&(!data.balanceError.isEmpty()||now-b.at>90000);
        changed(koin,b==null?"—":WalletReader.compact(b.koin));changed(vhp,b==null?"—":WalletReader.compact(b.vhp));changed(balanceState,saved?"Saved":b==null?"Awaiting wallet":"");
        int percent=b==null?0:WalletReader.percent(b.mana,b.koin);changed(mana,b==null?"—":percent+"%");manaBar.setProgress(percent,foreground&&ValueAnimator.areAnimatorsEnabled());
        device(free);
        changed(feedState,data.historyError.isEmpty()?"Confirmed":"Saved");feedState.setTextColor(data.historyError.isEmpty()?TEAL:AMBER);
        StringBuilder key=new StringBuilder(account).append(data.historyError).append(data.historyBusy);for(WalletReader.Entry e:data.rewards)key.append(e.id).append(e.koin).append(e.time);
        if(!key.toString().equals(feedKey)){feedKey=key.toString();rewardFeed.removeAllViews();
            if(data.rewards.isEmpty()){TextView empty=text(activity,account.isEmpty()?"Connect a wallet in Setup":data.historyBusy?"Checking rewards…":data.historyError.isEmpty()?"No confirmed rewards yet":"Rewards unavailable · see History",12,MUTED,false);empty.setGravity(Gravity.CENTER_VERTICAL);empty.setMaxLines(2);rewardFeed.addView(empty,new LayoutParams(-1,-1));}
            else for(int i=0;i<Math.min(2,data.rewards.size());i++){WalletReader.Entry e=data.rewards.get(i);LinearLayout row=row(activity);TextView when=text(activity,e.time>0?android.text.format.DateFormat.getTimeFormat(activity).format(new Date(e.time)):"Confirmed block",11,MUTED,false);when.setSingleLine(true);row.addView(when,new LayoutParams(0,-2,1));TextView amount=value((e.koin.startsWith("-")?"":"+")+WalletReader.compact(e.koin)+" KOIN",14,"home-reward-"+i);amount.setTextColor(TEAL);amount.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(amount,new LayoutParams(d(130),-1));rewardFeed.addView(row,new LayoutParams(-1,0,1));}
        }
    }
    private void device(long free){
        Intent battery=activity.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));int reading=battery==null?Integer.MIN_VALUE:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
        changed(temp,reading==Integer.MIN_VALUE?"—":String.format(Locale.getDefault(),"%.1f°C",reading/10.0));
        int heat=-1;try{if(Build.VERSION.SDK_INT>=29)heat=activity.getSystemService(PowerManager.class).getCurrentThermalStatus();}catch(Exception ignored){}
        String[] levels={"Normal","Warm","Moderate heat","High heat","Critical heat","Emergency","Stopping"};changed(thermal,heat>=0&&heat<levels.length?levels[heat]:"Unknown");thermal.setTextColor(heat>=2?AMBER:heat>=0?TEAL:MUTED);temp.setTextColor(heat>=2?AMBER:INK);temp.setContentDescription("Battery temperature "+temp.getText()+". "+thermal.getText());
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();activity.getSystemService(ActivityManager.class).getMemoryInfo(info);
        changed(storage,free<0?"—":String.format(Locale.getDefault(),"%.1f GiB",free/1073741824.0));changed(memory,info.totalMem<=0?"—":String.format(Locale.getDefault(),"%.1f GiB",info.availMem/1073741824.0));
        int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        changed(power,(level>=0&&scale>0?level*100/scale+"% battery":"Battery unavailable")+(battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0?" · Plugged in":" · On battery")+(heat>=1?" · "+levels[Math.min(heat,levels.length-1)]:""));
    }
}
