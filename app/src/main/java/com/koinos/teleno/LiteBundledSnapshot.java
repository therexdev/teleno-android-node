package com.koinos.teleno;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** The signed APK carries its compact checkpoint. No full-chain fallback. */
final class LiteBundledSnapshot {
    static final long MAX_BYTES=1024L*1024*1024;
    final JSONObject manifest;final String hash,id;final long bytes,height;
    final List<SnapshotPlan.Entry> files=new ArrayList<>();
    LiteBundledSnapshot(byte[] data,String expected)throws Exception {
        hash=SnapshotPlan.hash(data);
        if(!hash.equals(expected))throw new IOException("The bundled compact manifest failed its integrity check.");
        manifest=new JSONObject(new String(data,StandardCharsets.UTF_8));
        if(manifest.getInt("version")!=1||!"teleno-lite-state-snapshot".equals(manifest.getString("format"))
            ||!"lite-v1".equals(manifest.getString("engine_profile"))||manifest.getBoolean("producer_enabled")
            ||!"0x1220592bf18654fd07fdf5d500cde3e8402ecf7f81fa5dde8f14527b08bba8805f48".equals(manifest.getString("chain_id")))
            throw new IOException("This is not a supported Mainnet Lite checkpoint.");
        height=SnapshotPlan.number(manifest,"state_revision");long floor=SnapshotPlan.number(manifest,"retained_from"),window=SnapshotPlan.number(manifest,"retention_target");
        if(height<1||floor<1||floor>height||window<64||window>1000000||height-floor+1!=SnapshotPlan.number(manifest,"retained_blocks"))throw new IOException("Invalid compact retention window.");
        id=SnapshotPlan.id("lite-"+height+"-"+hash.substring(0,12));
        Set<String> paths=new HashSet<>();JSONArray list=manifest.getJSONArray("files");long total=0;boolean current=false,index=false,state=false,genesis=false;
        if(list.length()==0||list.length()>1024)throw new IOException("Invalid compact file count.");
        for(int i=0;i<list.length();i++){
            JSONObject row=list.getJSONObject(i);String path=row.getString("path"),digest=row.getString("sha256");long size=SnapshotPlan.number(row,"size_bytes");
            if(!paths.add(path)||!digest.matches("[a-f0-9]{64}")||size>MAX_BYTES)throw new IOException("Invalid compact file entry.");
            if(!path.equals("genesis_data.json")&&!path.matches("db/(CURRENT|IDENTITY|MANIFEST-[0-9]+|OPTIONS-[0-9]+|[0-9]+\\.(sst|log)|rocksdict-config\\.json)"))throw new IOException("Unsupported compact file path.");
            current|=path.equals("db/CURRENT");index|=path.matches("db/MANIFEST-[0-9]+");state|=path.endsWith(".sst");genesis|=path.equals("genesis_data.json");
            total=Math.addExact(total,size);if(total>MAX_BYTES)throw new IOException("Compact checkpoint exceeds the supported size.");files.add(new SnapshotPlan.Entry(path,digest,size));
        }
        if(!current||!index||!state||!genesis||total!=SnapshotPlan.number(manifest,"download_bytes"))throw new IOException("Incomplete compact checkpoint.");
        bytes=total;
    }
    static LiteBundledSnapshot read(Context c,String n)throws Exception {
        if(!"mainnet".equals(n))throw new IOException("The bundled compact checkpoint is for Mainnet. A Harbinger Lite checkpoint is not available yet.");
        return new LiteBundledSnapshot(NodeConfig.asset(c,"compact/lite-manifest.json"),LiteRuntime.MANIFEST_SHA256);
    }
    static void requireRestored(Context c,String n)throws Exception {
        NodeConfig.checkNetwork(n);File marker=new File(NodeConfig.root(c,n),"db/LITE-PROFILE");
        if(!NodeStorage.hasChain(c,n)||!marker.isFile())throw new IOException("Restore the compact checkpoint in Setup step 3 before starting Lite.");
        JSONObject saved;try(InputStream in=new FileInputStream(marker)){saved=new JSONObject(new String(NodeConfig.readBounded(in,4096),StandardCharsets.UTF_8));}
        if(saved.getInt("version")!=1||!n.equals(saved.getString("network"))||!saved.getString("manifest_sha256").matches("[a-f0-9]{64}"))throw new IOException("The Lite checkpoint marker is invalid. Existing data was preserved.");
    }
    static void restore(Context c,String n,String reviewed,SnapshotDownload.Progress progress)throws Exception {
        LiteBundledSnapshot plan=read(c,n);if(!plan.hash.equals(reviewed))throw new IOException("The bundled checkpoint changed. Review it again.");
        NodeStorage.ensureReady(c,n);File base=NodeConfig.root(c,n);base.mkdirs();int lock=NativeFiles.lock(new File(base,"node.owner.lock").getPath());
        String selected=NodeStorage.volume(c,n),gen=UUID.randomUUID().toString().replace("-","");File fresh=null;boolean activating=false;
        try {
            if(NodeStorage.retained(c,n))throw new IOException("Validate and remove the previous retained backup before replacing this chain.");
            if(NodeStorage.free(c,n)<plan.bytes+NodeStorage.RESERVE)throw new IOException("Free "+DataUse.size(plan.bytes+NodeStorage.RESERVE)+" before restoring, including the reserve.");
            fresh=NodeStorage.prepareSnapshot(c,n,selected,gen);
            File database=new File(fresh,"db");SnapshotDownload.plain(database,true);
            if(!database.mkdir()&&!database.isDirectory())throw new IOException("Could not create the compact checkpoint database directory.");
            long done=0;int count=0;
            for(SnapshotPlan.Entry entry:plan.files){
                progress.update("Restoring compact checkpoint",done,plan.bytes,count,plan.files.size());
                if(entry.path.equals("genesis_data.json")){
                    byte[] genesis=NodeConfig.asset(c,"compact/"+entry.path);
                    if(genesis.length!=entry.size||!SnapshotPlan.hash(genesis).equals(entry.hash)||!Arrays.equals(genesis,NodeConfig.asset(c,n+"-genesis.json")))throw new IOException("Compact genesis verification failed.");
                }else {
                    File output=new File(fresh,entry.path);SnapshotDownload.plain(output.getParentFile(),true);SnapshotDownload.plain(output,false);MessageDigest hash=MessageDigest.getInstance("SHA-256");long copied=0;
                    try(InputStream in=c.getAssets().open("compact/"+entry.path);FileOutputStream out=new FileOutputStream(output)){
                        byte[] buffer=new byte[65536];int length;
                        while((length=in.read(buffer))!=-1){copied+=length;if(copied>entry.size)throw new IOException("Compact file exceeds its declared size.");out.write(buffer,0,length);hash.update(buffer,0,length);progress.update("Restoring compact checkpoint",done+copied,plan.bytes,count,plan.files.size());}
                        out.getFD().sync();
                    }
                    if(copied!=entry.size||!SnapshotPlan.hex(hash.digest()).equals(entry.hash))throw new IOException("Compact file checksum failed: "+entry.path);
                }
                done+=entry.size;count++;
            }
            NodeConfig.writePrivate(new File(fresh,"db/LITE-PROFILE"),new JSONObject().put("version",1).put("network",n).put("manifest_sha256",plan.hash).toString().getBytes(StandardCharsets.UTF_8));
            NodeConfig.prepare(c,n,null);activating=true;NodeStorage.activateSnapshot(c,n,selected,gen,plan.id);
            progress.update("Compact checkpoint restored",plan.bytes,plan.bytes,count,count);
        } finally {
            // Once journal activation begins, recovery owns the staged directory.
            try {if(!activating&&fresh!=null){NodeStorage.protectReferences(c,n,fresh);StorageFiles.deleteTree(fresh);}}
            finally {NativeFiles.unlock(lock);}
        }
    }
}
