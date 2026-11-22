package com.koinos.teleno;

import android.content.*;
import android.content.res.AssetManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowStatFs;
import static org.junit.Assert.*;

/** Executes the actual Android restore against assets in the built APK. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,shadows={LiteRestoreTest.Ownership.class,LiteRestoreTest.DirectorySync.class})
public class LiteRestoreTest {
    File root;Context context;
    @Implements(value=NativeFiles.class,isInAndroidSdk=false)
    public static class Ownership {
        static final Map<Integer,RandomAccessFile> handles=new HashMap<>();static int next=1;
        @Implementation protected static void __staticInitializer__(){}
        @Implementation protected static int lock(String path)throws Exception {
            RandomAccessFile f=new RandomAccessFile(path,"rw");
            try {if(f.getChannel().tryLock()==null)throw new IOException("busy");int id=next++;handles.put(id,f);return id;}
            catch(Exception e){f.close();throw e;}
        }
        @Implementation protected static void unlock(int id)throws Exception{RandomAccessFile f=handles.remove(id);if(f!=null)f.close();}
    }
    @Implements(android.system.Os.class)
    public static class DirectorySync {
        @Implementation protected static FileDescriptor open(String path,int flags,int mode){return new FileDescriptor();}
        @Implementation protected static void fsync(FileDescriptor fd){}
        @Implementation protected static void close(FileDescriptor fd){}
    }
    @Before public void setup()throws Exception {
        root=Files.createTempDirectory("lite-apk-restore-").toFile();
        Context app=RuntimeEnvironment.getApplication();AssetManager assets=app.getAssets();
        int cookie=(Integer)AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,Paths.get("build/apk/aligned.apk").toAbsolutePath().toString());
        assertTrue("Real built APK assets are accessible",cookie!=0);
        context=new ContextWrapper(app){
            @Override public File getNoBackupFilesDir(){return root;}
            @Override public File[] getExternalFilesDirs(String type){return new File[0];}
        };
        context.getSharedPreferences("storage",0).edit().clear().commit();
        ShadowStatFs.registerStats(root.getPath(),3000000,2000000,2000000);
    }
    @After public void cleanup()throws Exception {
        assertTrue("All ownership locks released",Ownership.handles.isEmpty());
        if(root!=null){
            // Only remove this test's links; database deletion itself refuses links.
            for(String n:new String[]{"mainnet","harbinger"})for(String slot:new String[]{"db","chain/blockchain"}){
                Path p=new File(NodeConfig.root(context,n),slot).toPath();if(Files.isSymbolicLink(p))Files.delete(p);
            }
            StorageFiles.deleteTree(root);
        }
    }
    @Test public void freshInstallRestoresEveryBundledDatabaseFileAndActivatesIt()throws Exception {
        LiteBundledSnapshot plan=LiteBundledSnapshot.read(context,"mainnet");
        assertFalse(new File(NodeConfig.root(context,"mainnet"),"db").exists());
        LiteBundledSnapshot.restore(context,"mainnet",plan.hash,(phase,done,total,files,count)->{});
        LiteBundledSnapshot.requireRestored(context,"mainnet");assertTrue(NodeStorage.hasChain(context,"mainnet"));
        File base=NodeConfig.root(context,"mainnet");assertTrue(Files.isSymbolicLink(new File(base,"db").toPath()));
        for(SnapshotPlan.Entry entry:plan.files)if(entry.path.startsWith("db/")){
            byte[] data=Files.readAllBytes(new File(base,entry.path).toPath());assertEquals(entry.size,data.length);assertEquals(entry.hash,SnapshotPlan.hash(data));
        }
        File restored=NodeStorage.folder(context,"mainnet","phone",NodeStorage.generation(context,"mainnet"));
        // Prove the exact real Java-restored files can also be opened by the native engine.
        Path output=Paths.get(System.getProperty("teleno.ui.output"),"restored-checkpoint");Files.createDirectories(output.getParent());
        StorageFiles.copy(new File[]{new File(restored,"db")},new File[]{output.resolve("db").toFile()},(done,total,stage)->{});
        Files.copy(new File(base,"chain/genesis_data.json").toPath(),output.resolve("genesis_data.json"));
        for(String name:new String[]{"config.yml",".backup-just-restored",".teleno-android-restore.json"})
            Files.copy(new File(base,name).toPath(),output.resolve(name));
    }
    @Test public void cancellationLeavesExistingChainAndWalletFilesUnchangedAndRetrySucceeds()throws Exception {
        File base=NodeConfig.root(context,"mainnet");File old=new File(base,"db");old.mkdirs();
        Files.writeString(new File(old,"CURRENT").toPath(),"MANIFEST-000001\n");Files.writeString(new File(old,"MANIFEST-000001").toPath(),"existing database");
        File wallet=new File(root,"vault-mainnet.gcm");Files.writeString(wallet.toPath(),"wallet sentinel");
        File signer=new File(root,"signer-mainnet.gcm");Files.writeString(signer.toPath(),"signing sentinel");
        LiteBundledSnapshot plan=LiteBundledSnapshot.read(context,"mainnet");
        try{LiteBundledSnapshot.restore(context,"mainnet",plan.hash,(phase,done,total,files,count)->{if(done>1024*1024)throw new IOException("test cancellation");});fail("Cancelled copy must not activate");}
        catch(IOException expected){assertEquals("test cancellation",expected.getMessage());}
        assertEquals("",NodeStorage.generation(context,"mainnet"));assertEquals("existing database",Files.readString(new File(old,"MANIFEST-000001").toPath()));
        File generations=new File(root,"teleno-lite-chain");assertTrue(!generations.exists()||generations.list().length==0);
        assertEquals("wallet sentinel",Files.readString(wallet.toPath()));assertEquals("signing sentinel",Files.readString(signer.toPath()));
        LiteBundledSnapshot.restore(context,"mainnet",plan.hash,(phase,done,total,files,count)->{});LiteBundledSnapshot.requireRestored(context,"mainnet");
        assertTrue(NodeStorage.retained(context,"mainnet"));
        assertEquals("wallet sentinel",Files.readString(wallet.toPath()));assertEquals("signing sentinel",Files.readString(signer.toPath()));
        File[] backups=base.listFiles(f->f.getName().startsWith(".previous-")&&f.getName().endsWith("-db"));
        assertEquals(1,backups.length);assertEquals("existing database",Files.readString(new File(backups[0],"MANIFEST-000001").toPath()));
    }
}
