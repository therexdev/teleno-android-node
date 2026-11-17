package com.koinos.teleno;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Durable exact-snapshot attempt. Never replaces the selected database until verification passes. */
final class SnapshotBootstrap {
    private static AtomicFile record(Context c,String n){NodeConfig.checkNetwork(n);return new AtomicFile(new File(c.getNoBackupFilesDir(),"chain-download-"+n+".json"));}
    static boolean pending(Context c,String n){File f=record(c,n).getBaseFile();return f.exists()||new File(f+".bak").exists();}
    private static JSONObject load(Context c,String n)throws Exception {
        try(InputStream in=record(c,n).openRead()){return new JSONObject(new String(SnapshotDownload.read(in,2*SnapshotPlan.METADATA_LIMIT+1024*1024),StandardCharsets.UTF_8));}
    }
    private static SnapshotPlan plan(String n,JSONObject j)throws Exception{return new SnapshotPlan(n,j.getString("id"),j.getString("manifest"),j.getString("files"));}
    static SnapshotPlan review(Context c,String n)throws Exception {
        if(pending(c,n))return plan(n,load(c,n));
        return new SnapshotSource(n).plan(null,NodeConfig.asset(c,n+"-genesis.json"));
    }
    static void run(Context c,String n,String id,String manifestHash,String filesHash,SnapshotDownload.Progress progress)throws Exception {
        NodeStorage.ensureReady(c,n);File base=NodeConfig.root(c,n);base.mkdirs();
        int lock=NativeFiles.lock(new File(base,"node.owner.lock").getPath());
        try {
            JSONObject j;SnapshotPlan plan;
            if(pending(c,n)){j=load(c,n);plan=plan(n,j);}
            else {
                if(NodeStorage.retained(c,n))throw new IOException("First run the current observer until it advances, then stop it and remove the previous database copy.");
                progress.update("Checking snapshot",0,0,0,0);
                plan=new SnapshotSource(n).plan(SnapshotPlan.id(id),NodeConfig.asset(c,n+"-genesis.json"));
                if(!plan.manifestHash.equals(manifestHash)||!plan.filesHash.equals(filesHash))throw new IOException("Snapshot metadata changed since review. Check the download size again before starting.");
                j=new JSONObject().put("id",plan.id).put("manifest",plan.manifestText).put("files",plan.filesText)
                    .put("volume",NodeStorage.volume(c,n)).put("generation",UUID.randomUUID().toString().replace("-",""));
                NodeConfig.writePrivate(record(c,n).getBaseFile(),j.toString().getBytes(StandardCharsets.UTF_8));
            }
            if(!plan.id.equals(id)||!plan.manifestHash.equals(manifestHash)||!plan.filesHash.equals(filesHash))throw new IOException("The saved download differs from the reviewed snapshot. Reopen its review.");
            String selected=j.getString("volume"),gen=j.getString("generation");
            if(gen.equals(NodeStorage.generation(c,n))){record(c,n).delete();return;} // Recover a completed switch after process death.
            if(!selected.equals(NodeStorage.volume(c,n)))throw new IOException("Re-select the original chain storage before resuming this download.");
            if(NodeStorage.retained(c,n))throw new IOException("A previous database copy must be validated and removed before another restore.");
            File fresh=NodeStorage.prepareSnapshot(c,n,selected,gen);
            SnapshotDownload.run(plan,fresh,new SnapshotSource(n),()->NodeStorage.resolve(c,selected).free(),progress);
            progress.update("Restoring verified snapshot",plan.bytes,plan.bytes,plan.database.size(),plan.database.size());
            NodeConfig.prepare(c,n,null);
            NodeStorage.activateSnapshot(c,n,selected,gen,plan.id);
            record(c,n).delete();
        }finally{NativeFiles.unlock(lock);}
    }
    static void discard(Context c,String n)throws Exception {
        NodeStorage.ensureReady(c,n);if(!pending(c,n))return;
        File base=NodeConfig.root(c,n);base.mkdirs();int lock=NativeFiles.lock(new File(base,"node.owner.lock").getPath());
        try {
            JSONObject j=load(c,n);String gen=j.getString("generation");
            if(!gen.equals(NodeStorage.generation(c,n)))StorageFiles.deleteTree(NodeStorage.folder(c,n,j.getString("volume"),gen));
            record(c,n).delete();
        }finally{NativeFiles.unlock(lock);}
    }
}
