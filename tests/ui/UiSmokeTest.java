package com.koinos.teleno;
import java.lang.Process;
import android.app.Service;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiSmokeTest {
  Activity activity; DashboardController data; NodeShell shell; NodeService.ViewState fixtureNode;
  void create(String qualifiers,float... fontScale)throws Exception{
    RuntimeEnvironment.setQualifiers(qualifiers);
    org.robolectric.android.controller.ActivityController<Activity> c=Robolectric.buildActivity(Activity.class);
    activity=c.get();if(fontScale.length>0){activity.getResources().getConfiguration().fontScale=fontScale[0];activity.getResources().getDisplayMetrics().scaledDensity=activity.getResources().getDisplayMetrics().density*fontScale[0];}activity.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();
    ScrollView setup=new ScrollView(activity);LinearLayout list=UiKit.column(activity);list.setPadding(24,30,24,30);setup.addView(list);
    for(int i=1;i<=5;i++){TextView t=UiKit.text(activity,i+" · Setup task",22,UiKit.INK,true);t.setTag("setup-"+i);t.setPadding(20,30,20,30);list.addView(t);}
    shell=new NodeShell(activity,setup,()->{},()->{},()->{});activity.setContentView(shell.root);
    data=new DashboardController(activity,()->{});data.network="mainnet";byte[] b=new byte[21];b[1]=1;data.account=Base58.encodeChecked(b);
    data.balance=new WalletReader.Balance();data.balance.koin="12845250000";data.balance.vhp="24567812340000";data.balance.mana="11203905000";data.balance.source="Fixture · no live signing";data.balance.at=System.currentTimeMillis();
    WalletReader.Entry reward=new WalletReader.Entry();reward.id="0x1220"+"a".repeat(64);reward.kind="block";reward.title="Block reward";reward.detail="Irreversible on chain";reward.koin="425069912";reward.vhp="-409058262";reward.reward=true;reward.time=System.currentTimeMillis()-180000;reward.height=41000100;
    WalletReader.Entry older=new WalletReader.Entry();older.id="0x1220"+"b".repeat(64);older.kind="block";older.title="Block reward";older.koin="431001500";older.vhp="-410000000";older.reward=true;older.time=System.currentTimeMillis()-1800000;older.height=40999900;
    data.rewards=Arrays.asList(reward,older);data.rows=Arrays.asList(reward,older);data.total=2;
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(2));
    NodeService.ViewState node=new NodeService.ViewState();fixtureNode=node;node.network="mainnet";node.active=true;node.ready=true;node.hasSample=true;node.height=41000184;node.lib=41000124;node.peers=7;node.sampleAt=System.currentTimeMillis();node.mode=NodeService.PRODUCER;node.producerRunning=true;node.localBlocks=1;node.phase="Checking block opportunities";node.producerAccount=data.account;node.headTime=System.currentTimeMillis()-2000;node.readiness="Local chain is advancing.";
    ProductionStatus telemetry=new ProductionStatus();long stamp=SystemClock.elapsedRealtime();telemetry.accept(metrics(100),stamp-60000);telemetry.accept(metrics(5900),stamp);node.production=telemetry.snapshot();
    activity.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_TEMPERATURE,348).putExtra(BatteryManager.EXTRA_LEVEL,89).putExtra(BatteryManager.EXTRA_SCALE,100).putExtra(BatteryManager.EXTRA_PLUGGED,1));
    ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();memory.totalMem=8L*1024*1024*1024;memory.availMem=3L*1024*1024*1024;Shadows.shadowOf(activity.getSystemService(ActivityManager.class)).setMemoryInfo(memory);
    shell.foreground(false);shell.bind(node,data,false,true,"mainnet",data.account,true,82L*1024*1024*1024,"0.3.7");shell.foreground(false);
  }
  void render(String name,int width,int height)throws Exception{
    shell.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));shell.root.layout(0,0,width,height);shell.root.getViewTreeObserver().dispatchOnPreDraw();
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);shell.root.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("teleno.ui.output","teleno-android/evidence/ui"));Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
  }
  void navigationFits(boolean rail){
    Rect page=new Rect();shell.pages.getDrawingRect(page);shell.root.offsetDescendantRectToMyCoords(shell.pages,page);
    for(LinearLayout item:shell.navItems){
      Rect bounds=new Rect();item.getDrawingRect(bounds);shell.root.offsetDescendantRectToMyCoords(item,bounds);
      assertTrue("Tab stays in the viewport",bounds.left>=0&&bounds.top>=0&&bounds.right<=shell.root.getWidth()&&bounds.bottom<=shell.root.getHeight());
      assertTrue("Tab does not overlap content",rail?bounds.right<=page.left:bounds.top>=page.bottom);
    }
  }
  void dashboardFits(){
    View home=shell.screens.get(0);assertFalse("Home does not scroll",home instanceof ScrollView||home.canScrollVertically(1)||home.canScrollVertically(-1));
    allTextFits(home);
    assertEquals("Node control and Silent Mode are the dashboard actions",2,countClickable(home));assertNull(home.findViewWithTag("production-health"));
    for(String tag:new String[]{"home-clock","home-phase","home-koin","home-vhp","home-mana","home-block","home-peers","home-produced","home-temperature","home-storage","home-memory","home-toggle","home-silent-mode"}){
      View v=home.findViewWithTag(tag);assertNotNull(tag,v);Rect bounds=new Rect();v.getDrawingRect(bounds);((ViewGroup)home).offsetDescendantRectToMyCoords(v,bounds);
      assertTrue(tag+" fits horizontally "+bounds,bounds.left>=0&&bounds.right<=home.getWidth());assertTrue(tag+" fits vertically "+bounds,bounds.top>=0&&bounds.bottom<=home.getHeight());assertTrue(tag+" has room",v.getHeight()>0);
      if(v instanceof TextView){TextView t=(TextView)v;assertNotNull(t.getLayout());assertTrue(tag+" text is not clipped",t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom()+1);}
    }
    Rect k=new Rect(),v=new Rect();View kv=home.findViewWithTag("home-koin"),vv=home.findViewWithTag("home-vhp");kv.getDrawingRect(k);vv.getDrawingRect(v);((ViewGroup)home).offsetDescendantRectToMyCoords(kv,k);((ViewGroup)home).offsetDescendantRectToMyCoords(vv,v);
    assertTrue("VHP sits below KOIN",v.top>=k.bottom);assertEquals("Balances share a left edge",k.left,v.left);
  }
  static void allTextFits(View view){
    if(view instanceof TextView){TextView t=(TextView)view;if(t.length()>0){assertNotNull("Layout for "+t.getText(),t.getLayout());assertTrue("Visible text: "+t.getText()+" height="+t.getHeight()+" layout="+t.getLayout().getHeight(),t.getHeight()>=t.getLayout().getHeight()+t.getCompoundPaddingTop()+t.getCompoundPaddingBottom()-1);assertTrue("Text has width: "+t.getText(),t.getWidth()>0);for(int i=0;i<t.getLayout().getLineCount();i++)assertTrue("Full line fits: "+t.getText(),t.getLayout().getLineWidth(i)<=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+1);}}
    if(view.getParent() instanceof View){View p=(View)view.getParent();assertTrue("Contained child "+(view instanceof TextView?((TextView)view).getText():view.getTag())+" at "+view.getLeft()+","+view.getTop()+".."+view.getRight()+","+view.getBottom()+" in "+p.getWidth()+"x"+p.getHeight(),view.getLeft()>=0&&view.getTop()>=0&&view.getRight()<=p.getWidth()&&view.getBottom()<=p.getHeight());}
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)allTextFits(((ViewGroup)view).getChildAt(i));
  }
  static int countClickable(View v){int count=v.isClickable()?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)count+=countClickable(((ViewGroup)v).getChildAt(i));return count;}
  @Test public void compactPortraitFits()throws Exception{create("w360dp-h640dp-port-xhdpi");shell.root.setPadding(0,48,0,48);render("dashboard-compact-portrait",720,1280);navigationFits(false);dashboardFits();}
  @Test public void retroidLandscapeFits()throws Exception{create("w960dp-h540dp-land-xhdpi");shell.root.setPadding(0,48,0,48);render("dashboard-retroid-landscape",1920,1080);navigationFits(true);dashboardFits();}
  @Test public void smallLandscapeFits()throws Exception{create("w640dp-h360dp-land-xhdpi");shell.root.setPadding(0,32,0,32);render("dashboard-small-landscape",1280,720);navigationFits(true);dashboardFits();}
  @Test public void runningAnimationLifecycle()throws Exception{
    create("w412dp-h915dp-port-xhdpi");render("dashboard-animation",824,1830);UiKit.Halo halo=(UiKit.Halo)shell.root.findViewWithTag("home-running-animation");
    shell.foreground(true);assertTrue("Active node has a visible animation",halo.animating());shell.select(1,false);assertFalse("Setup stops Home animation",halo.animating());shell.select(0,false);assertTrue(halo.animating());
    shell.foreground(false);assertFalse("Background pauses animation",halo.animating());shell.foreground(true);fixtureNode.active=false;fixtureNode.mode="stopped";
    shell.bind(fixtureNode,data,true,false,"mainnet",data.account,true,82L*1024*1024*1024,"0.3.7");assertFalse("Stopped node has no running animation",halo.animating());
  }
  static boolean hasText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),text))return true;return false;}
  @After public void close(){if(data!=null)data.close();if(activity!=null)activity.finish();}
  @Test public void portrait()throws Exception{create("w412dp-h915dp-port-xhdpi");render("dashboard-portrait",824,1830);assertEquals(LinearLayout.VERTICAL,shell.root.getOrientation());navigationFits(false);dashboardFits();assertEquals(View.VISIBLE,shell.screens.get(0).getVisibility());shell.select(2,false);render("history-portrait",824,1830);assertEquals(View.GONE,shell.screens.get(0).getVisibility());assertEquals(View.VISIBLE,shell.screens.get(2).getVisibility());shell.select(1,false);assertEquals(View.VISIBLE,shell.setup.getVisibility());}
  @Test public void landscape()throws Exception{create("w915dp-h412dp-land-xhdpi");render("dashboard-landscape",1830,824);assertEquals(LinearLayout.HORIZONTAL,shell.root.getOrientation());navigationFits(true);dashboardFits();assertEquals(3,shell.navItems.size());}
  @Test public void largeText()throws Exception{create("w360dp-h800dp-port-xhdpi",1.3f);render("dashboard-large-text",720,1600);assertTrue(shell.root.getMeasuredWidth()==720);navigationFits(false);dashboardFits();}
  @Test public void historyPersistence()throws Exception{create("w412dp-h915dp-port-xhdpi");HistoryStore db=HistoryStore.get(activity);WalletReader.Page page=new WalletReader.Page();page.entries.add(data.rows.get(0));page.count=1;page.newest=page.oldest="5";db.savePage("mainnet",data.account,page,true);assertTrue(db.count("mainnet",data.account,"Rewards")>=1);assertEquals(0,db.count("harbinger",data.account,"Rewards"));assertEquals("-1",db.cache("mainnet",data.account,"cursor"));assertEquals("425069912",db.rows("mainnet",data.account,"Rewards",0,25).get(0).koin);}
  static String metrics(int count){return "INFO [metrics] producer_running=1 producer_pob_evaluations="+count+" producer_candidates_prepared=20 producer_errors=0 producer_head_retries=2 producer_mempool_retries=0";}
  @Test public void productionHealth()throws Exception{
    create("w412dp-h915dp-port-xhdpi");render("production-health-dashboard",824,1830);
    assertTrue(shell.productionReport().contains("Checks per second: 96.7"));
    assertTrue(shell.productionReport().contains("Producer account used at launch: "+data.account));
    shell.showProductionHealth();
    android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
    org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
    assertTrue(clipboard.getPrimaryClip().getItemAt(0).getText().toString().contains("Block eligibility checks: 5,900"));
    ProductionStatus stalled=new ProductionStatus();long stamp=SystemClock.elapsedRealtime();stalled.accept(metrics(100),stamp-60000);stalled.accept(metrics(100),stamp);fixtureNode.production=stalled.snapshot();
    shell.bind(fixtureNode,data,false,true,"mainnet",data.account,true,82L*1024*1024*1024,"0.3.7");
    render("production-health-stalled",824,1830);assertTrue(shell.productionReport().contains("No new block checks"));
  }
  @Test public void actualSetupActivity()throws Exception{
    RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);
    MainActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("shell");field.setAccessible(true);shell=(NodeShell)field.get(a);shell.foreground(false);shell.select(1,false);
    render("setup-portrait",824,1830);assertNotNull(shell.setup.findViewWithTag("setup-4"));assertNotNull(shell.setup.findViewWithTag("setup-5"));assertNotNull("Health report stays available in Setup",shell.setup.findViewWithTag("production-health"));
    View permission=shell.setup.findViewWithTag("review-vhp-permission");assertNotNull(permission);assertTrue(permission.isEnabled());permission.performClick();
    AlertDialog prerequisite=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(prerequisite.isShowing());
    assertTrue(hasText(prerequisite.getWindow().getDecorView(),"Mainnet"));prerequisite.dismiss();
    shell.setup.scrollTo(0,shell.setup.findViewWithTag("setup-5").getTop());render("setup-vhp-permission",824,1830);
    shell.navItems.get(2).performClick();assertEquals(2,shell.tab);shell.navItems.get(0).performClick();assertEquals(0,shell.tab);
    shell.navItems.get(2).performClick();
    android.content.res.Configuration landscape=new android.content.res.Configuration(a.getResources().getConfiguration());
    landscape.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;landscape.screenWidthDp=915;landscape.screenHeightDp=412;
    controller.configurationChange(landscape);a=controller.get();shell=(NodeShell)field.get(a);shell.foreground(false);
    render("history-landscape",1830,824);assertEquals(2,shell.tab);assertEquals(LinearLayout.HORIZONTAL,shell.root.getOrientation());navigationFits(true);
    controller.pause().stop().destroy();
  }

  @Test public void productionErrorDetails()throws Exception{
    create("w412dp-h915dp-port-xhdpi");ProductionStatus telemetry=new ProductionStatus();long stamp=SystemClock.elapsedRealtime();
    telemetry.accept(metrics(0),stamp-60000);telemetry.accept(metrics(3993).replace("producer_errors=0","producer_errors=2"),stamp);
    telemetry.accept("WARNING [block_producer] could not burn vhp secret-fixture-do-not-show",stamp);
    telemetry.accept("WARNING [block_producer] could not burn vhp secret-fixture-do-not-show",stamp);
    fixtureNode.production=telemetry.snapshot();shell.bind(fixtureNode,data,false,true,"mainnet",data.account,true,82L*1024*1024*1024,"0.3.7");
    render("production-health-burn-rejected",824,1830);navigationFits(false);
    assertTrue(shell.productionReport().contains("Last warning code: VHP_BURN_REJECTED"));
    assertFalse(((TextView)shell.root.findViewWithTag("home-health")).getText().toString().contains("HEALTHY"));
    assertTrue(shell.productionReport().contains("VHP_BURN_REJECTED · 2 occurrence(s)"));
    shell.showProductionHealth();
    android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
    String copied=((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString();
    assertTrue(copied.contains("VHP_BURN_REJECTED"));assertFalse(copied.contains("secret-fixture-do-not-show"));
  }

  @Test public void permissionReviewDetails()throws Exception{
    RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);
    MainActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    ProductionPermission.Snapshot snapshot=new ProductionPermission.Snapshot("1111111111111111111114oLvT2",new java.math.BigInteger("12000000000000"),new java.math.BigInteger("25000000000"),System.currentTimeMillis());
    a.showPermissionReview(snapshot,new java.math.BigInteger("100000000000"));
    AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
    String review=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
    assertTrue(review.contains("Network: Mainnet"));assertTrue(review.contains("Remaining allowance: 250 VHP"));assertTrue(review.contains("New allowance: 1000 VHP"));
    assertTrue(review.contains(ProductionPermission.POB));assertTrue(review.contains(ProductionPermission.VHP));assertTrue(review.contains("without asking for another passkey"));
    assertEquals("Review in KOIN Vault",dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(dialog.isShowing());
    a.showPermissionReview(snapshot,java.math.BigInteger.ZERO);dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("New allowance: 0 VHP"));
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();controller.pause().stop().destroy();
  }

  @Test public void screenshotProtectionIsLimitedToPrivateKeys()throws Exception{
    RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);
    MainActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    int secure=WindowManager.LayoutParams.FLAG_SECURE;
    assertEquals("Normal app screens allow screenshots",0,a.getWindow().getAttributes().flags&secure);
    java.lang.reflect.Method publicMessage=MainActivity.class.getDeclaredMethod("message",String.class,String.class);publicMessage.setAccessible(true);
    publicMessage.invoke(a,"Node status","Public support report fixture");
    AlertDialog status=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertTrue(status.isShowing());assertEquals("Public status messages can be shared",0,status.getWindow().getAttributes().flags&secure);status.dismiss();
    java.lang.reflect.Method privateMessage=MainActivity.class.getDeclaredMethod("message",String.class,String.class,boolean.class);privateMessage.setAccessible(true);
    privateMessage.invoke(a,"Signing key backup","PRIVATE-KEY-FIXTURE",true);
    AlertDialog backup=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View backupView=backup.getWindow().getDecorView();
    assertTrue(backup.isShowing());assertEquals("Explicit sensitivity protects export regardless of title",secure,backup.getWindow().getAttributes().flags&secure);
    assertTrue(hasText(backupView,"PRIVATE-KEY-FIXTURE"));backup.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();
    assertFalse("Closed backup text is cleared",hasText(backupView,"PRIVATE-KEY-FIXTURE"));
    java.lang.reflect.Method importKey=MainActivity.class.getDeclaredMethod("importKey");importKey.setAccessible(true);importKey.invoke(a);
    AlertDialog input=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View inputView=input.getWindow().getDecorView();
    assertEquals("Private key import remains protected",secure,input.getWindow().getAttributes().flags&secure);
    EditText field=findInput(inputView);assertNotNull(field);field.setText("PRIVATE-IMPORT-FIXTURE");
    input.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("",field.getText().toString());
    assertEquals("Closing a private dialog leaves normal screenshots available",0,a.getWindow().getAttributes().flags&secure);
    controller.pause().stop().destroy();
  }
  static EditText findInput(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=findInput(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}

  private static SilentMode silent(MainActivity a)throws Exception{
    java.lang.reflect.Field f=MainActivity.class.getDeclaredField("silent");f.setAccessible(true);return (SilentMode)f.get(a);
  }
  private static NodeShell shell(MainActivity a)throws Exception{
    java.lang.reflect.Field f=MainActivity.class.getDeclaredField("shell");f.setAccessible(true);return (NodeShell)f.get(a);
  }
  @Test public void silentModeKeepsAwakeAndRestoresWindow()throws Exception{
    RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-xhdpi");
    org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class);
    MainActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();
    Window w=a.getWindow();WindowManager.LayoutParams p=w.getAttributes();p.screenBrightness=.37f;w.setAttributes(p);
    int originalFlags=w.getAttributes().flags,originalUi=w.getDecorView().getSystemUiVisibility();
    NodeService.ViewState before=NodeService.snapshot();SilentMode mode=silent(a);NodeShell home=shell(a);
    assertTrue(home.root.findViewWithTag("home-silent-mode").performClick());
    assertTrue(mode.active());assertTrue(mode.view.animating());assertEquals(View.GONE,home.root.getVisibility());
    assertEquals(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,w.getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    assertEquals(.05f,w.getAttributes().screenBrightness,.0001f);
    assertTrue((w.getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)!=0);
    assertFalse(((UiKit.Halo)home.root.findViewWithTag("home-running-animation")).animating());
    c.pause();assertTrue(mode.active());assertFalse(mode.view.animating());assertEquals(originalFlags,w.getAttributes().flags);assertEquals(.37f,w.getAttributes().screenBrightness,.0001f);
    c.resume();assertTrue(mode.view.animating());assertEquals(.05f,w.getAttributes().screenBrightness,.0001f);
    assertTrue(mode.view.performClick());assertFalse(mode.active());assertFalse(mode.view.animating());
    assertEquals(View.VISIBLE,home.root.getVisibility());assertEquals(0,home.tab);
    assertEquals(.37f,w.getAttributes().screenBrightness,.0001f);assertEquals(originalFlags,w.getAttributes().flags);assertEquals(originalUi,w.getDecorView().getSystemUiVisibility());
    mode.enter();a.onBackPressed();assertFalse(mode.active());
    NodeService.ViewState after=NodeService.snapshot();assertEquals(before.active,after.active);assertEquals(before.runStartedAt,after.runStartedAt);assertEquals(before.localBlocks,after.localBlocks);
    c.pause().stop().destroy();
  }
  @Test public void silentModeRetainsExistingKeepAwakeAndLowBrightness()throws Exception{
    create("w412dp-h915dp-port-xhdpi");
    FrameLayout stage=new FrameLayout(activity);((ViewGroup)shell.root.getParent()).removeView(shell.root);stage.addView(shell.root);SilentMode mode=new SilentMode(activity,stage,shell.root,()->{});activity.setContentView(stage);
    Window w=activity.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);WindowManager.LayoutParams p=w.getAttributes();p.screenBrightness=.02f;w.setAttributes(p);
    mode.foreground(true);mode.enter();assertEquals(.02f,w.getAttributes().screenBrightness,.0001f);mode.exit();
    assertEquals(.02f,w.getAttributes().screenBrightness,.0001f);assertTrue((w.getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);mode.close();
  }
  @Test public void silentModeSurvivesRotationAndReleasesOnDestroy()throws Exception{
    RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");
    org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class);
    MainActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();silent(a).enter();
    SilentMode old=silent(a);android.content.res.Configuration land=new android.content.res.Configuration(a.getResources().getConfiguration());land.orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;land.screenWidthDp=960;land.screenHeightDp=540;
    c.configurationChange(land);a=c.get();SilentMode mode=silent(a);
    assertFalse("Old frame callback stops",old.view.animating());assertTrue(mode.active());assertEquals(View.GONE,shell(a).root.getVisibility());
    assertEquals(.05f,a.getWindow().getAttributes().screenBrightness,.0001f);
    mode.exit();assertEquals(-1f,a.getWindow().getAttributes().screenBrightness,.0001f);
    mode.enter();c.pause().stop().destroy();assertFalse(mode.view.animating());assertEquals(0,a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }
  private SilentMode fixtureSilent()throws Exception{
    FrameLayout stage=new FrameLayout(activity);((ViewGroup)shell.root.getParent()).removeView(shell.root);stage.addView(shell.root);
    SilentMode mode=new SilentMode(activity,stage,shell.root,()->{});activity.setContentView(stage);
    data.runStartedAt=System.currentTimeMillis()-3600000;data.runRewardsReady=true;data.runRewards.count=2;data.runRewards.koin=new java.math.BigInteger("856071412");
    mode.view.bind(fixtureNode,data);mode.enter();return mode;
  }
  private void renderSilent(SilentModeView view,String name,int width,int height)throws Exception{
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
    assertEquals("OLED background is true black",Color.BLACK,bitmap.getPixel(0,0));assertEquals(Color.BLACK,bitmap.getPixel(width-1,height-1));
    java.nio.file.Path dir=Paths.get(System.getProperty("teleno.ui.output"));Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve(name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    RectF first=view.boxAt(0),later=view.boxAt(60000);assertFalse("The whole box moves",first.equals(later));assertNotEquals("The color changes gradually",SilentModeView.colorAt(0),SilentModeView.colorAt(60000));
    for(long t=0;t<=7L*24*60*60*1000;t+=300013){RectF b=view.boxAt(t);assertTrue("Box remains on screen for a week of movement",b.left>=0&&b.top>=0&&b.right<=width&&b.bottom<=height);}
    String description=view.getContentDescription().toString();assertTrue(description.contains("4.25069912 KOIN"));assertTrue(description.contains("8.56071412 KOIN"));assertTrue(description.contains("2 confirmed rewards"));
  }
  @Test public void silentRetroidRenderingAndMotion()throws Exception{
    create("w960dp-h540dp-land-xhdpi");SilentMode mode=fixtureSilent();renderSilent(mode.view,"silent-retroid-landscape",1920,1080);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(2));renderSilent(mode.view,"silent-retroid-later",1920,1080);mode.close();
  }
  @Test public void silentPortraitAndLargeTextRendering()throws Exception{
    create("w360dp-h640dp-port-xhdpi",1.3f);SilentMode mode=fixtureSilent();renderSilent(mode.view,"silent-portrait",720,1280);mode.close();
  }
  @Test public void silentSmallLandscapeRendering()throws Exception{
    create("w640dp-h360dp-land-xhdpi");SilentMode mode=fixtureSilent();renderSilent(mode.view,"silent-small-landscape",1280,720);mode.close();
  }
  @Test public void runRewardTotalsAreExactScopedAndNotLimitedToTheFeed()throws Exception{
    create("w412dp-h915dp-port-xhdpi");HistoryStore db=HistoryStore.get(activity);long start=System.currentTimeMillis()-10000,end=start+5000;
    WalletReader.Page page=new WalletReader.Page();java.math.BigInteger atoms=new java.math.BigInteger("12345678901234567");
    for(int i=0;i<10;i++){WalletReader.Entry e=new WalletReader.Entry();e.id="run-fixture-"+i;e.kind="block";e.title="Block reward";e.reward=i!=9;e.koin=atoms.toString();e.time=i==0?start-1:i==8?end+1:start+i;e.seq=Integer.toString(i);page.entries.add(e);}
    page.count=10;page.newest="9";page.oldest="0";db.savePage("mainnet",data.account,page,true);db.savePage("mainnet",data.account,page,true);
    db.savePage("harbinger",data.account,page,true);db.savePage("mainnet","another-account",page,true);
    HistoryStore.RewardTotal total=db.rewardsForRun("mainnet",data.account,start,end);
    assertEquals("Duplicate reads do not double count; non-rewards and outside-run records excluded",7,total.count);assertEquals(atoms.multiply(java.math.BigInteger.valueOf(7)),total.koin);
    assertEquals("Current run includes every cached reward, not only the latest three",8,db.rewardsForRun("mainnet",data.account,start,0).count);
    assertEquals(0,db.rewardsForRun("mainnet",data.account,0,0).count);assertEquals(0,db.rewardsForRun("mainnet","",start,0).count);
    NodeService.ViewState v=new NodeService.ViewState();v.runStartedAt=start;v.runEndedAt=end;v.runNetwork="mainnet";v.runAccount=data.account;assertEquals(start,v.copy().runStartedAt);assertEquals(end,v.copy().runEndedAt);assertEquals("mainnet",v.copy().runNetwork);assertEquals(data.account,v.copy().runAccount);
    data.run(v);assertEquals(start,data.runStartedAt);data.run(v);assertEquals("Rebinding does not restart the run",start,data.runStartedAt);
    v.network="harbinger";data.run(v);assertEquals("Later status changes do not reassign the run",start,data.runStartedAt);
    v.runAccount="another-account";data.run(v);assertEquals(0,data.runStartedAt);v.runAccount=data.account;v.runNetwork="harbinger";data.run(v);assertEquals(0,data.runStartedAt);
  }


  static void setPrivate(Object object,String name,Object value)throws Exception {java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
  static Object getPrivate(Object object,String name)throws Exception {java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
  static Object invokePrivate(Object object,String name,Class<?>[] types,Object... args)throws Exception {java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
  static final class FakeNode extends Process {
    boolean alive,destroyed;final int exit;
    FakeNode(boolean alive,int exit){this.alive=alive;this.exit=exit;}
    public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
    public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
    public InputStream getErrorStream(){return getInputStream();}
    public int waitFor(){alive=false;return exit;}
    public int exitValue(){if(alive)throw new IllegalThreadStateException();return exit;}
    public void destroy(){destroyed=true;alive=false;}
    public boolean isAlive(){return alive;}
  }
  static void drainCommands(NodeService service)throws Exception {
    ((java.util.concurrent.ExecutorService)getPrivate(service,"commands")).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
  }
  @Test public void stickyRecoveryAndStopCancellation()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    try {
      service.getSharedPreferences("settings",0).edit().clear().putString("network","harbinger").putString("run-network","harbinger").putString("run-mode",NodeService.OBSERVER).putBoolean("run-wanted",true).commit();
      assertEquals(Service.START_STICKY,service.onStartCommand(null,0,1));assertTrue(NodeService.resumeRequested(service));assertTrue((Boolean)getPrivate(service,"recovering"));
      service.onStartCommand(new Intent(service,NodeService.class).setAction(NodeService.STOP),0,2);drainCommands(service);
      assertFalse("Stop persists cancellation before any recovery",NodeService.resumeRequested(service));assertFalse((Boolean)getPrivate(service,"wanted"));
      assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,3));
    }finally{controller.destroy();}
  }
  @Test public void nativeExitSchedulesRecovery()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    try {
      service.getSharedPreferences("settings",0).edit().clear().putString("network","harbinger").commit();
      invokePrivate(service,"rememberRun",new Class<?>[]{ProductionReview.class,long.class},null,0L);
      setPrivate(service,"process",new FakeNode(false,137));invokePrivate(service,"poll",new Class<?>[]{});drainCommands(service);
      assertTrue("Native process exit keeps user's run requested",NodeService.resumeRequested(service));assertTrue((Boolean)getPrivate(service,"recovering"));
      assertTrue(NodeService.snapshot().detail.contains("137"));assertTrue(NodeService.snapshot().detail.contains("Stop node"));
      service.onStartCommand(new Intent(service,NodeService.class).setAction(NodeService.STOP),0,2);drainCommands(service);assertFalse(NodeService.resumeRequested(service));
    }finally{controller.destroy();}
  }
  @Test public void stoppedStartCannotReenableRecovery()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    try {
      service.onStartCommand(new Intent(service,NodeService.class).setAction(NodeService.STOP),0,1);drainCommands(service);
      try{invokePrivate(service,"rememberRun",new Class<?>[]{ProductionReview.class,long.class},null,0L);fail("cancelled start must not be persisted");}
      catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause() instanceof java.io.InterruptedIOException);}
      assertFalse(NodeService.resumeRequested(service));
    }finally{controller.destroy();}
  }
  @Test public void changedNetworkCancelsStickyRecovery()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    try {
      service.getSharedPreferences("settings",0).edit().clear().putString("network","mainnet").putString("run-network","harbinger").putString("run-mode",NodeService.OBSERVER).putBoolean("run-wanted",true).commit();
      assertEquals(Service.START_NOT_STICKY,service.onStartCommand(null,0,1));assertFalse(NodeService.resumeRequested(service));
    }finally{controller.destroy();}
  }
  @Test public void wifiDropKeepsNativeProcessOpen()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    try {
      service.getSharedPreferences("settings",0).edit().clear().putString("network","harbinger").commit();
      service.getSharedPreferences("storage",0).edit().clear().commit();
      org.robolectric.shadows.ShadowStatFs.registerStats(service.getNoBackupFilesDir().getPath(),3000000,2000000,2000000);
      ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();memory.totalMem=8L*1024*1024*1024;memory.availMem=3L*1024*1024*1024;
      Shadows.shadowOf(service.getSystemService(ActivityManager.class)).setMemoryInfo(memory);Shadows.shadowOf(service.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
      invokePrivate(service,"rememberRun",new Class<?>[]{ProductionReview.class,long.class},null,0L);
      FakeNode process=new FakeNode(true,0);setPrivate(service,"process",process);setPrivate(service,"mode",NodeService.OBSERVER);
      invokePrivate(service,"poll",new Class<?>[]{});drainCommands(service);
      assertFalse("Wi-Fi loss does not destroy the miner",process.destroyed);assertTrue(NodeService.snapshot().phase.contains("Reconnecting"));assertTrue(NodeService.resumeRequested(service));
      service.onStartCommand(new Intent(service,NodeService.class).setAction(NodeService.STOP),0,2);drainCommands(service);
    }finally{controller.destroy();}
  }
  @Test public void burnReviewShowsExactCombinedTransaction()throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);MainActivity a=controller.get();
    a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    ProductionPermission.Snapshot permission=new ProductionPermission.Snapshot("1111111111111111111114oLvT2",new java.math.BigInteger("300000000"),new java.math.BigInteger("200000000"),System.currentTimeMillis());
    BurnKoin.Snapshot snapshot=new BurnKoin.Snapshot(permission,"19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK",new java.math.BigInteger("1000000000"),new java.math.BigInteger("500000000"));
    a.showBurnReview(snapshot,new java.math.BigInteger("123456789"),true);
    AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
    assertTrue(text.contains("Burn: 1.23456789 KOIN"));assertTrue(text.contains("2 → 4.23456789 VHP"));assertTrue(text.contains("irreversible"));assertTrue(text.contains(permission.account));
    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertFalse(dialog.isShowing());controller.pause().stop().destroy();
  }

  @Test public void mobileDataWarningRequiresExplicitDownloadApproval()throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);MainActivity a=controller.get();
    a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    String manifest=Files.readString(Paths.get("tests/fixtures/mainnet-manifest.json")),files=Files.readString(Paths.get("tests/fixtures/mainnet-files.json"));
    SnapshotPlan plan=new SnapshotPlan("mainnet",new org.json.JSONObject(manifest).getString("backup_id"),manifest,files);int[] started={0};
    a.showMobileDownloadReview(plan,plan.bytes/2,()->started[0]++);AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
    assertTrue(text.contains(DataUse.size(plan.bytes)));assertTrue(text.contains(DataUse.size(plan.bytes/2)));assertTrue(text.contains("carrier/roaming charges"));assertTrue(text.contains("does not enable ongoing"));
    assertEquals(0,started[0]);dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("Cancel transfers no data",0,started[0]);
    a.showMobileDownloadReview(plan,plan.bytes,()->started[0]++);dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    assertEquals("Download using mobile data",dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,started[0]);controller.pause().stop().destroy();
  }
  @Test public void cellularAndMeteredWifiAlwaysNeedConsent() {
    android.net.NetworkCapabilities wifi=new android.net.NetworkCapabilities().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    assertFalse(NetworkPolicy.costly(wifi));wifi.removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);assertTrue(NetworkPolicy.costly(wifi));
    android.net.NetworkCapabilities mobile=new android.net.NetworkCapabilities().addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    assertTrue("Even unlimited cellular needs a warning",NetworkPolicy.costly(mobile));
    android.net.NetworkCapabilities wired=new android.net.NetworkCapabilities().addTransportType(android.net.NetworkCapabilities.TRANSPORT_ETHERNET).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);assertFalse(NetworkPolicy.costly(wired));
  }
  @Test public void snapshotNetworkIsBoundAndMobileIsBlockedWithoutConsent()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();android.net.ConnectivityManager cm=context.getSystemService(android.net.ConnectivityManager.class);
    org.robolectric.shadows.ShadowConnectivityManager manager=Shadows.shadowOf(cm);
    manager.setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_MOBILE,0,true,true));
    android.net.Network active=cm.getActiveNetwork();assertNotNull(active);
    android.net.NetworkCapabilities mobile=new android.net.NetworkCapabilities().addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
    manager.setNetworkCapabilities(active,mobile);
    try {NetworkPolicy.downloadNetwork(context,false);fail("No mobile request before consent");}catch(IOException expected){assertTrue(expected.getMessage().contains("before using mobile"));}
    assertSame("Approved transfer uses the checked Android network",active,NetworkPolicy.downloadNetwork(context,true));
    mobile.removeTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    manager.setNetworkCapabilities(active,mobile);assertSame(active,NetworkPolicy.downloadNetwork(context,false));
  }
  static File savedChain(android.content.Context context,String network,String gen)throws Exception {
    File folder=new File(context.getNoBackupFilesDir(),"teleno-chain/"+network+"-"+gen);new File(folder,"db").mkdirs();new File(folder,"blockchain").mkdirs();
    Files.writeString(new File(folder,"storage.id").toPath(),gen);Files.writeString(new File(folder,"db/CURRENT").toPath(),"MANIFEST-000001\n");Files.writeString(new File(folder,"db/MANIFEST-000001").toPath(),"saved chain fixture");return folder;
  }
  @Test public void savedPhoneChainIsFoundAfterSelectionWasLost()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String gen="a".repeat(32);File folder=savedChain(context,"mainnet",gen);
    context.getSharedPreferences("storage",0).edit().clear().commit();assertFalse(NodeStorage.hasChain(context,"mainnet"));assertTrue(NodeStorage.hasRecoverableChain(context,"mainnet"));
    java.util.List<NodeStorage.Existing> found=NodeStorage.findExisting(context,"mainnet");assertEquals(1,found.size());assertEquals("phone",found.get(0).volume);assertEquals(gen,found.get(0).generation);
    assertEquals("MANIFEST-000001\n",Files.readString(new File(folder,"db/CURRENT").toPath()));assertTrue(new File(folder,"db/MANIFEST-000001").exists());
    assertEquals("Searching does not change the saved selection","",NodeStorage.generation(context,"mainnet"));
  }
  @Test public void discoveryExcludesUnverifiedDownloadsAndOtherNetworks()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();File incomplete=savedChain(context,"mainnet","b".repeat(32));new File(incomplete,"storage.id").delete();
    savedChain(context,"harbinger","c".repeat(32));assertTrue(NodeStorage.findExisting(context,"mainnet").isEmpty());
    assertTrue("Search preserves incomplete files",new File(incomplete,"db/CURRENT").exists());
  }
  @Test public void existingChainNextStepDoesNotRequestAnotherSnapshot()throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);MainActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);
    a.getSharedPreferences("settings",0).edit().clear().putString("network","mainnet").commit();File folder=savedChain(a,"mainnet","d".repeat(32));
    a.getSharedPreferences("storage",0).edit().putString("mainnet-volume","phone").putString("mainnet-generation","d".repeat(32)).commit();
    controller.create().start().resume().visible();assertTrue(NodeStorage.hasChain(a,"mainnet"));TextView next=(TextView)getPrivate(a,"next");
    assertTrue(next.getText().toString().contains("No new snapshot or backup cleanup"));assertTrue(new File(folder,"db/CURRENT").exists());controller.pause().stop().destroy();
  }
  @Test public void backupDeletionIsClearlyOptional()throws Exception {
    org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);MainActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();
    invokePrivate(a,"cleanStorage",new Class<?>[]{});AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();String text=((TextView)dialog.findViewById(android.R.id.message)).getText().toString();
    assertTrue(text.contains("OPTIONAL"));assertTrue(text.contains("never required to start"));dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();controller.pause().stop().destroy();
  }

  @org.robolectric.annotation.Implements(value=NativeFiles.class,isInAndroidSdk=false)
  public static class ShadowOwnership {
    static final java.util.Map<Integer,RandomAccessFile> handles=new java.util.HashMap<>();static int next=1;
    @org.robolectric.annotation.Implementation protected static void __staticInitializer__(){}
    @org.robolectric.annotation.Implementation protected static int lock(String path)throws IOException {
      RandomAccessFile file=new RandomAccessFile(path,"rw");try{if(file.getChannel().tryLock()==null)throw new IOException("busy");int id=next++;handles.put(id,file);return id;}catch(Exception e){file.close();throw e;}
    }
    @org.robolectric.annotation.Implementation protected static void unlock(int id)throws IOException {RandomAccessFile file=handles.remove(id);if(file!=null)file.close();}
  }
  /** Robolectric cannot fsync directories; data and link operations still use real host files. */
  @org.robolectric.annotation.Implements(android.system.Os.class)
  public static class ShadowDirectorySync {
    @org.robolectric.annotation.Implementation protected static java.io.FileDescriptor open(String path,int flags,int mode){return new java.io.FileDescriptor();}
    @org.robolectric.annotation.Implementation protected static void fsync(java.io.FileDescriptor fd){}
    @org.robolectric.annotation.Implementation protected static void close(java.io.FileDescriptor fd){}
  }
  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void reconnectRepairsLostSelectionWithoutChangingTheDatabase()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String gen="e".repeat(32);File folder=savedChain(context,"mainnet",gen);
    context.getSharedPreferences("storage",0).edit().clear().commit();NodeStorage.reconnect(context,"mainnet","phone",gen);
    assertEquals(gen,NodeStorage.generation(context,"mainnet"));assertTrue(NodeStorage.hasChain(context,"mainnet"));
    File link=new File(NodeConfig.root(context,"mainnet"),"db");assertTrue(Files.isSymbolicLink(link.toPath()));assertEquals(new File(folder,"db").toPath(),Files.readSymbolicLink(link.toPath()));
    assertEquals("saved chain fixture",Files.readString(new File(folder,"db/MANIFEST-000001").toPath()));assertFalse(SnapshotBootstrap.pending(context,"mainnet"));
  }
  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void updateRepairsMissingLinksForTheSavedGeneration()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String gen="f".repeat(32);File folder=savedChain(context,"mainnet",gen);
    context.getSharedPreferences("storage",0).edit().putString("mainnet-volume","phone").putString("mainnet-generation",gen).commit();
    assertFalse(new File(NodeConfig.root(context,"mainnet"),"db").exists());NodeStorage.ensureReady(context,"mainnet");
    assertTrue(Files.isSymbolicLink(new File(NodeConfig.root(context,"mainnet"),"db").toPath()));assertTrue(new File(folder,"db/CURRENT").exists());assertTrue(NodeStorage.hasChain(context,"mainnet"));
  }
  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void cleanupDeletesOnlyTheSeparateValidatedBackup()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String active="1".repeat(32),old="2".repeat(32);File current=savedChain(context,"mainnet",active),backup=savedChain(context,"mainnet",old);
    NodeStorage.reconnect(context,"mainnet","phone",active);
    String retained=new org.json.JSONObject().put("volume","phone").put("generation",old).put("backup",active).toString();
    context.getSharedPreferences("storage",0).edit().putString("mainnet-retained",retained).putString("mainnet-validated",active).commit();
    File keySentinel=new File(context.getNoBackupFilesDir(),"signer-sentinel.gcm");Files.writeString(keySentinel.toPath(),"keep");
    NodeStorage.clean(context,"mainnet");assertFalse(backup.exists());assertTrue(new File(current,"db/CURRENT").exists());assertTrue(NodeStorage.hasChain(context,"mainnet"));assertEquals("keep",Files.readString(keySentinel.toPath()));
  }
  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void cleanupRefusesUnreadableActiveChainAndPreservesTheBackup()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String active="3".repeat(32),old="4".repeat(32);File current=savedChain(context,"mainnet",active),backup=savedChain(context,"mainnet",old);
    NodeStorage.reconnect(context,"mainnet","phone",active);
    context.getSharedPreferences("storage",0).edit().putString("mainnet-retained",new org.json.JSONObject().put("volume","phone").put("generation",old).put("backup",active).toString()).putString("mainnet-validated",active).commit();
    new File(current,"db/CURRENT").delete();try{NodeStorage.clean(context,"mainnet");fail("No backup deletion when active chain is unreadable");}catch(IOException expected){assertTrue(expected.getMessage().contains("blocked"));}
    assertTrue(new File(backup,"db/CURRENT").exists());assertTrue(NodeStorage.retained(context,"mainnet"));
  }

  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void discardKeepsACompletedChainWhenItsSelectionWasLost()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String gen="5".repeat(32);File saved=savedChain(context,"mainnet",gen);
    context.getSharedPreferences("storage",0).edit().clear().commit();
    File record=new File(context.getNoBackupFilesDir(),"chain-download-mainnet.json");Files.writeString(record.toPath(),new org.json.JSONObject().put("volume","phone").put("generation",gen).toString());
    SnapshotBootstrap.discard(context,"mainnet");assertFalse(SnapshotBootstrap.pending(context,"mainnet"));assertTrue("Published chain is never discarded as a partial download",new File(saved,"db/CURRENT").exists());assertTrue(NodeStorage.hasRecoverableChain(context,"mainnet"));
  }
  @Test @Config(shadows={ShadowOwnership.class,ShadowDirectorySync.class}) public void completedRestoreRecordIsReconciledWithoutAnotherDownload()throws Exception {
    android.content.Context context=RuntimeEnvironment.getApplication();String gen="6".repeat(32);File saved=savedChain(context,"mainnet",gen);
    context.getSharedPreferences("storage",0).edit().putString("mainnet-volume","phone").putString("mainnet-generation",gen).commit();
    File record=new File(context.getNoBackupFilesDir(),"chain-download-mainnet.json");Files.writeString(record.toPath(),new org.json.JSONObject().put("volume","phone").put("generation",gen).toString());
    SnapshotBootstrap.finishCompleted(context,"mainnet");assertFalse(SnapshotBootstrap.pending(context,"mainnet"));assertTrue(new File(saved,"db/CURRENT").exists());assertTrue(NodeStorage.hasChain(context,"mainnet"));
  }

  static void readyForBatteryTest(NodeService service)throws Exception {
    ((java.util.concurrent.ScheduledExecutorService)getPrivate(service,"monitor")).shutdownNow();
    service.getSharedPreferences("settings",0).edit().clear().putString("network","harbinger").commit();service.getSharedPreferences("storage",0).edit().clear().commit();
    org.robolectric.shadows.ShadowStatFs.registerStats(service.getNoBackupFilesDir().getPath(),3000000,2000000,2000000);
    ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();memory.totalMem=8L<<30;memory.availMem=3L<<30;
    Shadows.shadowOf(service.getSystemService(ActivityManager.class)).setMemoryInfo(memory);Shadows.shadowOf(service.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
    android.net.ConnectivityManager cm=service.getSystemService(android.net.ConnectivityManager.class);org.robolectric.shadows.ShadowConnectivityManager manager=Shadows.shadowOf(cm);
    manager.setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true));
    manager.setNetworkCapabilities(cm.getActiveNetwork(),new android.net.NetworkCapabilities().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
    Shadows.shadowOf(service.getSystemService(PowerManager.class)).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE);
  }
  @Test public void observerAndProducerCanStartAndRemainEligibleOnBattery()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    try {
      readyForBatteryTest(service);
      // A missing battery broadcast also cannot impose a charger requirement.
      invokePrivate(service,"requireEnvironment",new Class<?>[]{boolean.class},true);
      for(int plugged:new int[]{0,1,0}){
        service.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,plugged).putExtra(BatteryManager.EXTRA_LEVEL,65).putExtra(BatteryManager.EXTRA_SCALE,100));
        invokePrivate(service,"requireEnvironment",new Class<?>[]{boolean.class},false);
        invokePrivate(service,"requireEnvironment",new Class<?>[]{boolean.class},true);
      }
    }finally{controller.destroy();}
  }
  @Test public void runningOnBatteryStillEnforcesHeatAndStorageProtection()throws Exception {
    org.robolectric.android.controller.ServiceController<NodeService> controller=Robolectric.buildService(NodeService.class).create();NodeService service=controller.get();
    try {
      readyForBatteryTest(service);service.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,0));
      Shadows.shadowOf(service.getSystemService(PowerManager.class)).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE);
      try{invokePrivate(service,"requireEnvironment",new Class<?>[]{boolean.class},true);fail("Overheating still pauses production");}
      catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause().getMessage().contains("too hot"));}
      Shadows.shadowOf(service.getSystemService(PowerManager.class)).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE);
      org.robolectric.shadows.ShadowStatFs.registerStats(service.getNoBackupFilesDir().getPath(),3000000,1,1);
      try{invokePrivate(service,"requireEnvironment",new Class<?>[]{boolean.class},true);fail("Low storage still pauses production");}
      catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause().getMessage().contains("2 GiB"));}
    }finally{controller.destroy();}
  }
}
