package com.koinos.teleno;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiteUiTest {
    org.robolectric.android.controller.ActivityController<MainActivity> controller;
    MainActivity app;
    static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static Object invoke(Object object,String name,Class<?>[] types,Object... args)throws Exception{java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    static boolean text(View view,String match){if(view instanceof TextView&&((TextView)view).getText().toString().contains(match))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),match))return true;return false;}
    void open(){RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");controller=Robolectric.buildActivity(MainActivity.class);app=controller.get();app.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}

    @Test public void liteBrandAndPreviewAreVisible(){
        open();assertTrue(text(app.getWindow().getDecorView(),"TELENO LITE"));
        NodeShell shell;try{shell=(NodeShell)field(app,"shell");}catch(Exception e){throw new AssertionError(e);}shell.select(1,false);
        assertTrue(text(shell.setup,"Teleno Node Lite"));assertTrue(text(shell.setup,LitePreview.STATUS));
        for(int i=1;i<=4;i++)assertNotNull(shell.setup.findViewWithTag("setup-"+i));
    }
    @Test public void homeAndSetupStartCannotLaunchTheStandardEngine()throws Exception{
        open();NodeShell shell=(NodeShell)field(app,"shell");
        ((Button)shell.root.findViewWithTag("home-toggle")).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(text(dialog.getWindow().getDecorView(),"compact engine and snapshot"));dialog.dismiss();
        ((Button)field(app,"production")).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(text(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),LitePreview.EXPLANATION));
        assertNull(Shadows.shadowOf((Application)app.getApplication()).getNextStartedService());
    }
    @Test public void snapshotAndObserverEntryPointsExplainPreviewWithoutStartingWork()throws Exception{
        open();invoke(app,"reviewSnapshot",new Class<?>[]{boolean.class},false);
        assertTrue(text(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"does not download"));
        assertFalse((Boolean)field(app,"snapshotBusy"));
        invoke(app,"runNode",new Class<?>[]{String.class},NodeService.OBSERVER);
        invoke(app,"findExistingChain",new Class<?>[]{});
        assertNull(Shadows.shadowOf((Application)app.getApplication()).getNextStartedService());
        assertFalse((Boolean)field(app,"findingChain"));assertNull(field(app,"notificationStart"));
    }
    @Test public void serviceRejectsDownloadsStartsMaintenanceAndRecovery(){
        org.robolectric.android.controller.ServiceController<NodeService> c=Robolectric.buildService(NodeService.class).create();NodeService service=c.get();
        try{
            for(String action:new String[]{null,NodeService.RECOVER,NodeService.OBSERVER,NodeService.PRODUCER,NodeService.DOWNLOAD_CHAIN,NodeService.MOVE_STORAGE,NodeService.CLEAN_STORAGE,NodeService.DISCARD_DOWNLOAD,NodeService.RECONNECT_STORAGE}){
                service.getSharedPreferences("settings",0).edit().putBoolean("run-wanted",true).commit();
                assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(action==null?null:new Intent().setAction(action).putExtra("network","mainnet"),0,1));
                assertFalse(NodeService.resumeRequested(service));assertFalse(NodeService.snapshot().active);assertFalse(NodeService.snapshot().busy);
            }
        }finally{c.destroy();}
    }
    static final class AppFiles extends ContextWrapper {
        final File root;AppFiles(Context base,File root){super(base);this.root=root;root.mkdirs();}
        @Override public File getNoBackupFilesDir(){return root;}
        @Override public File[] getExternalFilesDirs(String type){return new File[0];}
    }
    @Test public void liteDiscoveryAndKeyPathsCannotSelectStandardAppData()throws Exception{
        Context base=RuntimeEnvironment.getApplication();File temp=Files.createTempDirectory("lite-isolation").toFile();
        Context standard=new AppFiles(base,new File(temp,"standard/no_backup")),lite=new AppFiles(base,new File(temp,"lite/no_backup"));
        File old=new File(NodeConfig.root(standard,"mainnet"),"db");old.mkdirs();Files.writeString(new File(old,"CURRENT").toPath(),"MANIFEST-1\n");Files.writeString(new File(old,"MANIFEST-1").toPath(),"standard data");
        assertTrue(ChainData.usable(old));assertTrue(NodeStorage.findExisting(lite,"mainnet").isEmpty());
        assertNotEquals(NodeConfig.root(standard,"mainnet"),NodeConfig.root(lite,"mainnet"));
        assertNotEquals(field(new KeyVault(standard,"mainnet"),"file"),field(new KeyVault(lite,"mainnet"),"file"));
        assertNotEquals(field(new VaultStore(standard),"file"),field(new VaultStore(lite),"file"));
        assertEquals("standard data",Files.readString(new File(old,"MANIFEST-1").toPath()));
        assertNotEquals(18085,NodeConfig.RPC_PORT);assertNotEquals(18888,NodeConfig.P2P_PORT);
    }
    @Test public void burnReviewDisclosesThatLiteMiningIsNotAvailable()throws Exception{
        open();String owner="1111111111111111111114oLvT2";
        BurnKoin.Snapshot s=new BurnKoin.Snapshot(new ProductionPermission.Snapshot(owner,new java.math.BigInteger("300000000"),java.math.BigInteger.ZERO,System.currentTimeMillis()),"19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK",new java.math.BigInteger("1000000000"),new java.math.BigInteger("500000000"));
        app.showBurnReview(s,new java.math.BigInteger("100000000"),true);
        AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(text(dialog.getWindow().getDecorView(),"does not enable Lite mining"));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(Shadows.shadowOf((Application)app.getApplication()).getNextStartedService());
    }
    @Test public void liteWalletPairingUsesDistinctNameWithoutChangingProtocol()throws Exception{
        final JSONObject[] sent={null};final JSONObject[] disk={new JSONObject()};long now=1000000;
        VaultClient.Transport transport=(route,body)->{
            if(route.equals("config"))return new JSONObject().put("demo",false).put("network","mainnet").put("features",new JSONObject().put("kaiProducer",true));
            if(route.equals("dapp/create")){sent[0]=body;return new JSONObject().put("sessionId","A".repeat(24)).put("secret","B".repeat(43)).put("expiresAt",now+1800000).put("protocolVersion",2);}
            throw new AssertionError(route);
        };
        VaultClient client=new VaultClient(transport,new VaultClient.Store(){public JSONObject load(){return disk[0];}public void save(JSONObject state){disk[0]=state;}},()->now);
        client.connect();assertTrue(sent[0].getString("name").contains("Teleno Node Lite"));assertEquals(2,sent[0].getInt("protocolVersion"));assertTrue(client.openUrl().startsWith("https://koinvault.app/#connect="));
    }
}
