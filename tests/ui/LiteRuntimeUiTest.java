package com.koinos.teleno;

import android.app.*;
import android.content.*;
import android.graphics.*;
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
public class LiteRuntimeUiTest {
    org.robolectric.android.controller.ActivityController<MainActivity> controller;MainActivity app;
    static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static boolean text(View view,String match){if(view instanceof TextView&&((TextView)view).getText().toString().contains(match))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(text(((ViewGroup)view).getChildAt(i),match))return true;return false;}
    static byte[] manifest()throws Exception{return Files.readAllBytes(Paths.get("build/apk/assets/compact/lite-manifest.json"));}
    static LiteBundledSnapshot changed(java.util.function.Consumer<JSONObject> edit)throws Exception{JSONObject json=new JSONObject(new String(manifest(),java.nio.charset.StandardCharsets.UTF_8));edit.accept(json);byte[] bytes=json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);return new LiteBundledSnapshot(bytes,SnapshotPlan.hash(bytes));}
    void open(){RuntimeEnvironment.setQualifiers("w412dp-h915dp-port-xhdpi");controller=Robolectric.buildActivity(MainActivity.class);app=controller.get();app.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    @Test public void runtimeShowsFourStepsAndLocalRestore()throws Exception{
        assertTrue(LitePreview.ready());open();NodeShell shell=(NodeShell)field(app,"shell");shell.select(1,false);
        for(int i=1;i<=4;i++)assertNotNull(shell.setup.findViewWithTag("setup-"+i));
        assertTrue(text(shell.setup,"included in this APK"));assertTrue(text(shell.setup,"no additional network data"));
        assertFalse(text(shell.setup,"compact mining is not available"));assertEquals("Restore compact checkpoint…",((Button)field(app,"downloadChain")).getText().toString());
        int width=824,height=1830;shell.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));shell.root.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);shell.root.draw(new Canvas(bitmap));java.nio.file.Path dir=Paths.get(System.getProperty("teleno.ui.output"));Files.createDirectories(dir);try(OutputStream out=Files.newOutputStream(dir.resolve("lite-setup.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    @Test public void realBundledManifestIsPinnedAndCompact()throws Exception{
        LiteBundledSnapshot plan=new LiteBundledSnapshot(manifest(),LiteRuntime.MANIFEST_SHA256);
        assertEquals(41258090,plan.height);assertTrue(plan.bytes>50000000&&plan.bytes<70000000);assertTrue(plan.files.size()>5&&plan.files.size()<100);
    }
    @Test public void changedManifestCannotPassPinnedHash()throws Exception{
        byte[] data=manifest();data[data.length-2]^=1;
        try{new LiteBundledSnapshot(data,LiteRuntime.MANIFEST_SHA256);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("integrity"));}
    }
    @Test public void pathTraversalAndWrongNetworkFailBeforeWriting()throws Exception{
        try{changed(j->{try{j.getJSONArray("files").getJSONObject(0).put("path","db/../../private.key");}catch(Exception e){throw new RuntimeException(e);}});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("path"));}
        try{changed(j->{try{j.put("chain_id","0x1220"+"00".repeat(32));}catch(Exception e){throw new RuntimeException(e);}});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Mainnet"));}
    }
    @Test public void incompleteRetentionAndInventoryAreRejected()throws Exception{
        try{changed(j->{try{j.put("retained_from",j.getLong("state_revision")+1);}catch(Exception e){throw new RuntimeException(e);}});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("retention"));}
        try{changed(j->{try{j.put("download_bytes",j.getLong("download_bytes")+1);}catch(Exception e){throw new RuntimeException(e);}});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Incomplete"));}
    }
    @Test public void startingWithoutCompactRestoreDoesNotFallBackToGenesis()throws Exception{
        Context c=new ContextWrapper(RuntimeEnvironment.getApplication()){
            final File root=Files.createTempDirectory("lite-missing").toFile();
            @Override public File getNoBackupFilesDir(){return root;}
        };
        try{LiteBundledSnapshot.requireRestored(c,"mainnet");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("step 3"));}
    }
    @Test public void burnReviewDoesNotStopRunningMiner()throws Exception{
        open();NodeService.ViewState state=(NodeService.ViewState)field(nullOwner(),"VIEW");boolean old=state.active;String mode=state.mode;
        try{
            state.active=true;state.mode=NodeService.PRODUCER;
            String owner="1111111111111111111114oLvT2";
            BurnKoin.Snapshot snapshot=new BurnKoin.Snapshot(new ProductionPermission.Snapshot(owner,new java.math.BigInteger("300000000"),java.math.BigInteger.ZERO,System.currentTimeMillis()),"19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK",new java.math.BigInteger("1000000000"),new java.math.BigInteger("500000000"));
            app.showBurnReview(snapshot,new java.math.BigInteger("100000000"),true);AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertFalse(text(dialog.getWindow().getDecorView(),"does not enable Lite mining"));dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();assertTrue(state.active);assertEquals(NodeService.PRODUCER,state.mode);assertNull(Shadows.shadowOf((Application)app.getApplication()).getNextStartedService());
        }finally{state.active=old;state.mode=mode;}
    }
    private static Object nullOwner(){return new NodeService();}
}
