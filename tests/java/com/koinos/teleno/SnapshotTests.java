package com.koinos.teleno;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SnapshotTests {
    static int count;static final String ID="20261115-fixture";
    static void check(boolean b,String s){count++;if(!b)throw new AssertionError(s);}
    interface Run{void run()throws Exception;}
    static void rejects(Run r,String s)throws Exception{boolean no=false;try{r.run();}catch(Exception e){no=true;}check(no,s);}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static class Fixture implements SnapshotDownload.Source {
        JSONObject m=new JSONObject(),f=new JSONObject();Map<String,byte[]> content=new HashMap<>();int calls;boolean corrupt;String fail="";
        Fixture()throws Exception {
            JSONArray list=new JSONArray();String[] paths={"chain/genesis_data.json","db/CURRENT","db/MANIFEST-000001","db/000002.sst","config.yml"};
            byte[][] data={bytes("{\"a\":1,\"b\":[true,\"x\"]}"),bytes("MANIFEST-000001\n"),bytes("manifest"),new byte[2*1024*1024+19],bytes("block_producer: false")};
            new Random(1).nextBytes(data[3]);long total=0;
            for(int i=0;i<paths.length;i++){String hash=SnapshotPlan.hash(data[i]);content.put(hash,data[i]);total+=data[i].length;list.put(new JSONObject().put("path",paths[i]).put("sha256",hash).put("size_bytes",data[i].length).put("runtime_file",!paths[i].startsWith("db/")));}
            f.put("format","teleno-native-snapshot-files").put("version",1).put("backup_id",ID).put("files",list);
            m.put("format","teleno-native-rocksdb-snapshot").put("version",1).put("backup_id",ID).put("created_at","20261115T000000Z")
                .put("source",new JSONObject().put("network","mainnet").put("storage_layout","unified").put("chain_id","0x1220592bf18654fd07fdf5d500cde3e8402ecf7f81fa5dde8f14527b08bba8805f48"))
                .put("public_bootstrap",new JSONObject().put("network","mainnet").put("producer_mode",false))
                .put("restore",new JSONObject().put("start_as_observer_first",true).put("force_block_producer_disabled_on_first_start",true))
                .put("snapshot",new JSONObject().put("file_count",paths.length).put("object_count",paths.length).put("total_bytes",total));
        }
        SnapshotPlan plan()throws Exception{return new SnapshotPlan("mainnet",ID,m.toString(),f.toString());}
        public InputStream open(SnapshotPlan.Entry e)throws Exception{calls++;if(e.path.equals(fail))throw new IOException("network interrupted");byte[] b=content.get(e.hash).clone();if(corrupt)b[0]^=1;return new ByteArrayInputStream(b);}
    }
    static SnapshotDownload.Progress progress=(phase,done,total,files,all)->{if(done<0||done>total||files>all)throw new AssertionError("invalid progress");};
    public static void main(String[] args)throws Exception {
        Fixture fixture=new Fixture();SnapshotPlan plan=fixture.plan();check(plan.database.size()==3,"only database files restored");check(plan.id.equals(ID),"exact snapshot pinned");
        plan.verifyGenesis(fixture.content.get(plan.genesis.hash),bytes("{ \"b\": [true,\"x\"], \"a\":1.0 }"));check(true,"genesis semantic comparison accepts formatting changes");
        rejects(()->plan.verifyGenesis(bytes("{}"),bytes("{}")),"genesis digest mismatch blocked");
        rejects(()->plan.verifyGenesis(fixture.content.get(plan.genesis.hash),bytes("{\"a\":2}")),"different genesis blocked");
        rejects(()->SnapshotPlan.id("../../escape"),"ID traversal blocked");rejects(()->SnapshotPlan.id("https://evil"),"arbitrary source URL blocked");
        Fixture wrong=new Fixture();wrong.m.getJSONObject("source").put("chain_id","0x1220"+"0".repeat(64));rejects(wrong::plan,"wrong chain rejected");
        Fixture network=new Fixture();network.m.getJSONObject("source").put("network","testnet");rejects(network::plan,"wrong network rejected");
        Fixture traversal=new Fixture();traversal.f.getJSONArray("files").getJSONObject(3).put("path","db/../../private.key");rejects(traversal::plan,"file traversal rejected");
        Fixture secret=new Fixture();secret.f.getJSONArray("files").getJSONObject(3).put("path","block_producer/private.key");rejects(secret::plan,"key import rejected");
        Fixture duplicate=new Fixture();duplicate.f.getJSONArray("files").getJSONObject(3).put("path","db/CURRENT");rejects(duplicate::plan,"duplicate file rejected");
        Fixture sizes=new Fixture();sizes.m.getJSONObject("snapshot").put("total_bytes",1);rejects(sizes::plan,"inventory sum checked");
        Fixture floating=new Fixture();floating.f.getJSONArray("files").getJSONObject(3).put("size_bytes",1.5);rejects(floating::plan,"fractional size rejected");
        Fixture pointer=new Fixture();pointer.f.getJSONArray("files").getJSONObject(1).put("path","db/OTHER");rejects(pointer::plan,"missing CURRENT rejected");
        Fixture old=new Fixture();old.m.put("version",2);rejects(old::plan,"unsupported version rejected");
        File stage=Files.createTempDirectory("snapshot-test-").toFile();
        try {
            fixture.fail="db/000002.sst";rejects(()->SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress),"network interruption keeps generation inactive");
            check(new File(stage,"db/CURRENT").isFile()&&new File(stage,"db/MANIFEST-000001").isFile(),"completed files preserved");
            fixture.fail="";fixture.calls=0;SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress);check(fixture.calls==1,"resume reuses verified files");
            fixture.calls=0;SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress);check(fixture.calls==0,"fully downloaded resume uses no network");
            Files.write(new File(stage,"db/000002.sst").toPath(),bytes("damaged"));fixture.calls=0;SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress);check(fixture.calls==1,"corrupted saved file redownloaded");
            check(!new File(stage,"config.yml").exists()&&!new File(stage,"chain").exists(),"published runtime config never replaces app config");
            Files.delete(new File(stage,"db/000002.sst").toPath());fixture.calls=0;
            rejects(()->SnapshotDownload.run(plan,stage,fixture,()->SnapshotPlan.RESERVE,progress),"capacity checked before downloading");check(fixture.calls==0,"no network on low space");
            rejects(()->SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,(phase,done,total,f,a)->{if(done>100)throw new IOException("cancel");}),"cancellation checked during transfer");
            check(!new File(stage,"db/000002.sst").exists(),"cancelled file never promoted");
            fixture.corrupt=true;rejects(()->SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress),"download digest mismatch blocks publication");check(!new File(stage,"db/000002.sst").exists(),"bad download not treated as complete");
            fixture.corrupt=false;SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress);
            File link=new File(stage,"db/000002.sst");Files.delete(link.toPath());File sentinel=File.createTempFile("snapshot-sentinel-",".txt");Files.write(sentinel.toPath(),bytes("keep"));Files.createSymbolicLink(link.toPath(),sentinel.toPath());
            rejects(()->SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress),"database symlink rejected");check(new String(Files.readAllBytes(sentinel.toPath())).equals("keep"),"symlink target untouched");Files.delete(link.toPath());sentinel.delete();
            Files.write(new File(stage,"db/UNKNOWN").toPath(),bytes("keep"));rejects(()->SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,progress),"unknown staging contents preserved and rejected");
        }finally{StorageFiles.deleteTree(stage);}
        for(String n:new String[]{"mainnet","harbinger"}) {
            Path root=Paths.get(args[0]);String manifest=Files.readString(root.resolve(n+"-manifest.json")),files=Files.readString(root.resolve(n+"-files.json"));
            SnapshotPlan live=new SnapshotPlan(n,new JSONObject(manifest).getString("backup_id"),manifest,files);
            check(live.bytes>0&&live.database.size()>1,"published "+n+" metadata accepted");
            if(n.equals("harbinger"))rejects(()->live.verifyGenesis(Files.readAllBytes(root.resolve("harbinger-snapshot-genesis.json")),Files.readAllBytes(root.resolve("../../app/src/main/assets/harbinger-genesis.json"))),"published Harbinger genesis mismatch blocks fast sync");
        }
        System.out.println("PASS: "+count+" snapshot metadata, checksum, resume, cancellation, capacity and path-safety assertions (fixture downloads).");
    }
}
