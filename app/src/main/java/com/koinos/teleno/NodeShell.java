package com.koinos.teleno;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;
import static com.koinos.teleno.UiKit.*;

/** Adaptive native dashboard, setup and history. Every value comes from a labelled source. */
final class NodeShell {
    final Activity activity;final LinearLayout root;final FrameLayout pages;final ScrollView setup;
    final List<View> screens=new ArrayList<>();final List<LinearLayout> navItems=new ArrayList<>();final List<Glyph> navIcons=new ArrayList<>();final List<TextView> navLabels=new ArrayList<>();final List<Button> filterButtons=new ArrayList<>();
    int tab;private boolean foreground=true;private DashboardController data;private NodeService.ViewState node;
    private TextView historyNote,pageLabel;
    private LinearLayout historyList;private Button historyOlder,previous,nextPage;
    private String historyKey="",displayVersion="";
    private final Runnable toggleAction,refreshAction,silentAction;
    private DashboardView home;
    NodeShell(Activity a,ScrollView setup,Runnable toggle,Runnable refresh,Runnable silentAction) {
        activity=a;this.setup=setup;toggleAction=toggle;refreshAction=refresh;this.silentAction=silentAction;
        boolean rail=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        root=rail?row(a):column(a);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff101a2a,BG,0xff0c1422});root.setBackground(bg);
        pages=new FrameLayout(a);LinearLayout navigation=buildNavigation(rail);
        if(rail){root.addView(navigation,new LinearLayout.LayoutParams(d(64),-1));root.addView(pages,new LinearLayout.LayoutParams(0,-1,1));}
        else{root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));root.addView(navigation,new LinearLayout.LayoutParams(-1,d(70)));}
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        screens.add(dashboard());screens.add(setup);screens.add(history());for(View page:screens){pages.addView(page,new FrameLayout.LayoutParams(-1,-1));page.setVisibility(View.GONE);}select(0,false);
    }
    int d(int n){return dp(activity,n);}
    private TextView t(String s,int size,int color,boolean bold){return text(activity,s,size,color,bold);}
    private void gap(LinearLayout l,int n){l.addView(new View(activity),new LinearLayout.LayoutParams(1,d(n)));}
    private LinearLayout panel(LinearLayout parent){LinearLayout l=column(activity);l.setPadding(d(17),d(17),d(17),d(17));l.setBackground(surface(activity,PANEL,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(12);parent.addView(l,p);return l;}
    private TextView eyebrow(LinearLayout parent,String title,int color){TextView v=t(title,10,color,true);v.setLetterSpacing(.16f);parent.addView(v);return v;}
    private void addButton(LinearLayout box,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(10);box.addView(b,p);}
    private ScrollView scroll(LinearLayout content){ScrollView s=new ScrollView(activity);s.setFillViewport(true);s.setClipToPadding(false);s.setVerticalScrollBarEnabled(false);content.setPadding(d(16),d(22),d(16),d(26));s.addView(content);return s;}
    private LinearLayout buildNavigation(boolean rail){LinearLayout nav=rail?column(activity):row(activity);nav.setGravity(rail?Gravity.TOP|Gravity.CENTER_HORIZONTAL:Gravity.CENTER);nav.setPadding(d(5),d(rail?12:4),d(5),d(8));nav.setBackgroundColor(0xff0b1320);
        boolean railBrand=rail&&activity.getResources().getConfiguration().screenHeightDp>=360;
        if(railBrand){TextView logo=t("T",23,TEAL,true);logo.setGravity(Gravity.CENTER);logo.setBackground(surface(activity,0xff173033,14));nav.addView(logo,new LinearLayout.LayoutParams(d(42),d(36)));gap(nav,16);}
        String[] names={"Home","Setup","History"},icons={"home","setup","history"};
        for(int i=0;i<3;i++){final int target=i;LinearLayout item=column(activity);item.setGravity(Gravity.CENTER);item.setPadding(d(2),d(6),d(2),d(6));Glyph icon=new Glyph(activity,icons[i]);item.addView(icon,new LinearLayout.LayoutParams(d(22),d(22)));gap(item,6);TextView label=t(names[i],10,MUTED,true);label.setGravity(Gravity.CENTER);item.addView(label);item.setContentDescription(names[i]+" tab");item.setFocusable(true);item.setClickable(true);item.setOnClickListener(v->select(target,true));
            LinearLayout.LayoutParams p=rail?new LinearLayout.LayoutParams(-1,d(64)):new LinearLayout.LayoutParams(0,-1,1);if(rail)p.bottomMargin=d(6);else{p.leftMargin=d(4);p.rightMargin=d(4);}nav.addView(item,p);navItems.add(item);navIcons.add(icon);navLabels.add(label);}
        if(railBrand){View space=new View(activity);nav.addView(space,new LinearLayout.LayoutParams(1,0,1));TextView local=t("ON\nDEVICE",8,MUTED,true);local.setGravity(Gravity.CENTER);local.setLetterSpacing(.1f);nav.addView(local);gap(nav,8);}return nav;
    }
    void select(int selected,boolean animate){tab=Math.max(0,Math.min(2,selected));for(int i=0;i<screens.size();i++){View v=screens.get(i);v.animate().cancel();v.setAlpha(1);v.setTranslationY(0);v.setVisibility(i==tab?View.VISIBLE:View.GONE);}for(int i=0;i<3;i++){boolean active=i==tab;navItems.get(i).setSelected(active);navItems.get(i).setBackground(surface(activity,active?0xff183338:0xff0b1320,16));navIcons.get(i).color=active?TEAL:MUTED;navIcons.get(i).invalidate();navLabels.get(i).setTextColor(active?TEAL:MUTED);}
        if(animate&&ValueAnimator.areAnimatorsEnabled()){View v=screens.get(tab);v.setAlpha(0);v.setTranslationY(d(10));v.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
        if(home!=null)home.foreground(foreground&&tab==0);if(data!=null&&tab==2)data.refreshRows();
    }
    void foreground(boolean value){foreground=value;if(home!=null)home.foreground(value&&tab==0);}
    void setup(int step){select(1,true);View anchor=setup.findViewWithTag("setup-"+step);if(anchor!=null)setup.post(()->setup.smoothScrollTo(0,Math.max(0,anchor.getTop()-d(12))));}
    private View dashboard(){home=new DashboardView(activity,toggleAction,silentAction);return home;}
    private View history(){LinearLayout main=column(activity);eyebrow(main,"YOUR NODE / TIMELINE",TEAL);gap(main,12);main.addView(t("History",30,INK,true));gap(main,8);main.addView(t("Rewards, wallet activity and the actions taken on this device.",13,MUTED,false));gap(main,18);
        LinearLayout filters=row(activity);for(String name:new String[]{"All","Rewards","Actions"}){Button b=button(activity,name,false,()->{if(data!=null){data.filter(name);historyKey="";}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=d(5);filters.addView(b,p);b.setTag(name);filterButtons.add(b);}main.addView(filters);gap(main,12);
        historyNote=t("",11,MUTED,false);main.addView(historyNote);gap(main,12);historyList=column(activity);main.addView(historyList);
        LinearLayout paging=row(activity);previous=button(activity,"Previous",false,()->{if(data!=null)data.page(-1);});nextPage=button(activity,"Next",false,()->{if(data!=null)data.page(1);});paging.addView(previous,new LinearLayout.LayoutParams(0,-2,1));pageLabel=t("",11,MUTED,true);pageLabel.setGravity(Gravity.CENTER);paging.addView(pageLabel,new LinearLayout.LayoutParams(d(76),-2));paging.addView(nextPage,new LinearLayout.LayoutParams(0,-2,1));main.addView(paging);gap(main,10);
        historyOlder=button(activity,"Load older chain records",false,()->{if(data!=null)data.loadHistory(true);});main.addView(historyOlder,new LinearLayout.LayoutParams(-1,-2));addButton(main,button(activity,"Refresh history",false,refreshAction));gap(main,16);main.addView(t("App actions are saved from version 0.3.0 onward. Chain records come from the selected network’s irreversible account history. Tap a row for exact amounts and its ID. Older records load in pages; no signing keys are stored in history.",11,MUTED,false));return scroll(main);
    }
    void bind(NodeService.ViewState v,DashboardController d,boolean canStart,boolean canStop,String selectedNetwork,String account,boolean keyPresent,long free,String version){
        node=v;data=d;displayVersion=version;
        home.bind(v,d,canStart,canStop,selectedNetwork,account,free);renderHistory();
    }
    private String key(List<WalletReader.Entry> list){StringBuilder s=new StringBuilder();for(WalletReader.Entry e:list)s.append(e.id).append(e.koin).append(e.vhp);return s.toString();}
    private void renderHistory(){if(data==null)return;
        for(Button b:filterButtons){boolean chosen=data.filter.equals(b.getTag());b.setSelected(chosen);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(chosen?TEAL:0xff182637));b.setTextColor(chosen?BG:TEAL);}
        changed(historyNote,data.filter+" · "+data.total+" saved records"+(data.historyBusy?"\nReading chain history…":"")+(!data.historyError.isEmpty()?"\n"+data.historyError:"")+(!HistoryStore.storageError.isEmpty()?"\n"+HistoryStore.storageError:""));
        previous.setEnabled(data.offset>0);nextPage.setEnabled(data.offset+25<data.total);changed(pageLabel,(data.offset/25+1)+" / "+Math.max(1,(data.total+24)/25));
        changed(historyOlder,"-1".equals(data.cursor)?"All available chain history loaded":data.historyBusy?"Loading chain history…":"Load older chain records");historyOlder.setEnabled(!data.account.isEmpty()&&!data.historyBusy&&!"-1".equals(data.cursor));
        String key=data.network+data.account+data.filter+data.offset+key(data.rows);if(key.equals(historyKey))return;historyKey=key;historyList.removeAllViews();
        if(data.rows.isEmpty()){LinearLayout empty=panel(historyList);empty.addView(t(data.account.isEmpty()?"Connect a wallet for on-chain history. Device actions appear here as you use the app.":"No records in this view yet. Load older chain records or choose another filter.",13,MUTED,false));}
        else for(WalletReader.Entry e:data.rows)entry(historyList,e,false);
    }
    private void entry(LinearLayout parent,WalletReader.Entry e,boolean compact){LinearLayout box=compact?column(activity):panel(parent);if(compact){box.setPadding(0,d(12),0,d(12));parent.addView(box);}
        LinearLayout r=row(activity);Glyph icon=new Glyph(activity,e.reward?"reward":"history");icon.color=e.reward?TEAL:MUTED;r.addView(icon,new LinearLayout.LayoutParams(d(23),d(23)));LinearLayout words=column(activity);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=d(10);r.addView(words,wp);
        TextView title=t(e.title,13,INK,true);words.addView(title);gap(words,5);String when=e.time>0?new SimpleDateFormat("MMM d · h:mm a",Locale.getDefault()).format(new Date(e.time)):"Block time unavailable";words.addView(t(e.height>0?"Block "+NumberFormat.getIntegerInstance().format(e.height)+" · "+when:when,10,MUTED,false));box.addView(r);
        if(!"0".equals(e.koin)||!"0".equals(e.vhp)){gap(box,9);String s=(!"0".equals(e.koin)?signed(e.koin)+" KOIN":"")+(!"0".equals(e.vhp)?(!"0".equals(e.koin)?"\n":"")+signed(e.vhp)+" VHP":"");box.addView(t(s,14,e.reward?TEAL:INK,true));}
        if(!compact){gap(box,8);box.addView(t(e.detail,10,MUTED,false));}box.setFocusable(true);box.setClickable(true);box.setContentDescription(e.title+". "+when+". Open details.");box.setOnClickListener(v->entryDetails(e));
    }
    private String signed(String n){return(n.startsWith("-")?"":"+")+WalletReader.amount(n);}
    String productionReport(){
        if(node==null)return "No local node status is available yet.";
        long now=System.currentTimeMillis();String age=node.hasSample&&node.headTime>0?Long.toString((now-node.headTime)/1000)+"s":"unavailable";
        return "Teleno "+displayVersion+"\nCaptured: "+DateFormat.getDateTimeInstance().format(new Date(now))
            +"\nNode network: "+node.network+"\nNode mode: "+node.mode+"\nNode active: "+node.active
            +"\nProducer account used at launch: "+(node.producerAccount.isEmpty()?"no producer launched":node.producerAccount)
            +"\nSelected wallet: "+(data==null||data.account.isEmpty()?"not selected":data.account)
            +"\nLocal block: "+node.height+"\nIrreversible: "+node.lib+"\nLocal block age: "+age
            +"\nPeers: "+(node.peers<0?"not reported":node.peers)+"\nLocal blocks this run: "+node.localBlocks
            +"\nChain readiness: "+node.readiness
            +"\nAutomatic recovery requested: "+NodeService.resumeRequested(activity)
            +"\nBattery optimization exempt: "+activity.getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(activity.getPackageName())
            +"\nLast interruption / stop: "+activity.getSharedPreferences("settings",0).getString("last-run-reason","not recorded")
            +(node.hasSample?"\nChain sample age: "+((now-node.sampleAt)/1000)+"s":"\nNo chain sample yet")
            +"\n\n"+(node.active&&NodeService.PRODUCER.equals(node.mode)?"Current production telemetry":"Retained telemetry / production is not active")
            +"\n"+node.production.report(SystemClock.elapsedRealtime())
            +"\n\nThis report contains public addresses and fixed status/counter values. No signing key, wallet credential or raw log is included.";
    }
    void showProductionHealth(){
        final String report=productionReport();
        new AlertDialog.Builder(activity).setTitle("Production health").setMessage(report)
            .setPositiveButton("Copy report",(a,b)->{((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Teleno production health",report));Toast.makeText(activity,"Production report copied",Toast.LENGTH_SHORT).show();})
            .setNegativeButton("Close",null).show();
    }
    private void entryDetails(WalletReader.Entry e){String detail=e.title+"\n\n"+e.detail+(e.time>0?"\n"+DateFormat.getDateTimeInstance().format(new Date(e.time)):"\nBlock time unavailable")+(e.height>0?"\nBlock "+e.height:"")+(!"0".equals(e.koin)?"\n"+signed(e.koin)+" KOIN":"")+(!"0".equals(e.vhp)?"\n"+signed(e.vhp)+" VHP":"");
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle("Activity details").setMessage(detail+(WalletReader.id(e.id)?"\n\n"+e.id:"")).setPositiveButton("Done",null);
        if(WalletReader.id(e.id))dialog.setNeutralButton("Copy ID",(a,b)->((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Public chain record",e.id)));dialog.show();
    }
    void showWalletDetails(){if(data==null||data.account.isEmpty()){setup(3);return;}WalletReader.Balance b=data.balance;String message=data.account+(b==null?"\n\nBalances unavailable.":"\n\nKOIN: "+WalletReader.amount(b.koin)+"\nVHP: "+WalletReader.amount(b.vhp)+"\nAvailable mana: "+WalletReader.amount(b.mana)+"\n\nLast read: "+DateFormat.getDateTimeInstance().format(new Date(b.at))+"\nSource: "+b.source+"\n\nDashboard amounts are shortened; these values show full precision.");
        new AlertDialog.Builder(activity).setTitle("Node wallet · "+data.network).setMessage(message).setPositiveButton("Done",null).setNeutralButton("Copy address",(a,c)->((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Producer address",data.account))).setNegativeButton("Wallet setup",(a,c)->setup(3)).show();
    }
}
