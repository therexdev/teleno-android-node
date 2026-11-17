package com.koinos.teleno;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Strict reader for the pinned Teleno native object-store format. Never imports wallet/config files. */
final class SnapshotPlan {
    static final int METADATA_LIMIT=8*1024*1024;
    static final long RESERVE=2L*1024*1024*1024, MAX_BYTES=4L*1024*1024*1024*1024;
    static final class Entry {
        final String path,hash;final long size;
        Entry(String p,String h,long s){path=p;hash=h;size=s;}
        String objectPath(){return "objects/sha256/"+hash.substring(0,2)+"/"+hash.substring(2,4)+"/"+hash;}
    }
    final String network,id,created,manifestText,filesText,manifestHash,filesHash;
    final long bytes,head;final List<Entry> database;final Entry genesis;
    static String base(String n)throws IOException {
        if("mainnet".equals(n))return "https://seed.koinosfoundation.org/backups/prodnet/teleno-bootstrap/";
        if("harbinger".equals(n))return "https://testnet.koinosfoundation.org/backups/testnet/teleno-bootstrap/";
        throw new IOException("Unknown snapshot network.");
    }
    static String id(String id)throws IOException {if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,159}"))throw new IOException("Invalid snapshot identifier.");return id;}
    static void format(JSONObject j,String format)throws Exception {if(!format.equals(j.getString("format"))||number(j,"version")!=1)throw new IOException("Unsupported snapshot format. Existing chain data was preserved.");}
    static long number(JSONObject j,String field)throws Exception {
        Object value=j.get(field);if(!(value instanceof Number)||!value.toString().matches("[0-9]+"))throw new IOException("Invalid snapshot size or version.");
        long n=Long.parseLong(value.toString());if(n>MAX_BYTES)throw new IOException("Snapshot size is outside the supported range.");return n;
    }
    SnapshotPlan(String n,String expectedId,String manifest,String files)throws Exception {
        base(n);network=n;id=id(expectedId);manifestText=manifest;filesText=files;
        if(manifest.getBytes(StandardCharsets.UTF_8).length>METADATA_LIMIT||files.getBytes(StandardCharsets.UTF_8).length>METADATA_LIMIT)throw new IOException("Snapshot metadata is too large.");
        manifestHash=hash(manifest.getBytes(StandardCharsets.UTF_8));filesHash=hash(files.getBytes(StandardCharsets.UTF_8));
        JSONObject m=new JSONObject(manifest),f=new JSONObject(files);format(m,"teleno-native-rocksdb-snapshot");format(f,"teleno-native-snapshot-files");
        if(!id.equals(m.getString("backup_id"))||!id.equals(f.getString("backup_id")))throw new IOException("Snapshot identifiers do not match.");
        JSONObject source=m.getJSONObject("source"),pub=m.getJSONObject("public_bootstrap"),restore=m.getJSONObject("restore");
        String wanted=n.equals("mainnet")?"mainnet":"testnet";
        if(!wanted.equals(source.getString("network"))||!wanted.equals(pub.getString("network"))||!"unified".equals(source.getString("storage_layout")))throw new IOException("Snapshot is for a different network or storage layout.");
        if(pub.getBoolean("producer_mode")||!restore.getBoolean("start_as_observer_first")||!restore.getBoolean("force_block_producer_disabled_on_first_start"))throw new IOException("Snapshot is not an observer bootstrap.");
        String expected=n.equals("mainnet")?"0x1220592bf18654fd07fdf5d500cde3e8402ecf7f81fa5dde8f14527b08bba8805f48":"0x122067703e2929121059ca3f591aa8e50fb19d747b26e3b73ed66ff7cde629d28749";
        for(JSONObject obj:Arrays.asList(source,pub)) {String chain=obj.optString("chain_id","");if(!chain.isEmpty()&&!expected.equals(chain))throw new IOException("Snapshot chain identity does not match the selected network.");}
        if(n.equals("mainnet")&&!expected.equals(source.optString("chain_id")))throw new IOException("Mainnet snapshot has no verified chain identity.");
        created=m.optString("created_at","");if(!created.matches("[0-9TZ:-]{0,32}"))throw new IOException("Invalid snapshot date.");
        head=source.has("head_height")?number(source,"head_height"):0;
        JSONArray list=f.getJSONArray("files");JSONObject summary=m.getJSONObject("snapshot");
        if(list.length()==0||list.length()>50000||list.length()!=number(summary,"file_count"))throw new IOException("Snapshot file count does not match.");
        long total=0,dbBytes=0;Entry genesisEntry=null;boolean current=false,hasManifest=false;
        Set<String> paths=new HashSet<>();Map<String,Long> objects=new HashMap<>();List<Entry> db=new ArrayList<>();
        for(int i=0;i<list.length();i++) {
            JSONObject row=list.getJSONObject(i);String path=row.getString("path"),digest=row.getString("sha256");long size=number(row,"size_bytes");
            if(!digest.matches("[a-f0-9]{64}")||!paths.add(path))throw new IOException("Snapshot contains a duplicate path or invalid checksum.");
            Long previous=objects.put(digest,size);if(previous!=null&&previous!=size)throw new IOException("Snapshot object sizes conflict.");
            Entry e=new Entry(path,digest,size);total=Math.addExact(total,size);
            if(path.matches("db/[A-Za-z0-9][A-Za-z0-9_.-]{0,159}")) {
                if(row.getBoolean("runtime_file"))throw new IOException("Snapshot database file is mislabeled.");
                if(path.equals("db/LOCK")||path.endsWith(".download-part"))throw new IOException("Snapshot contains a reserved database path.");
                current|=path.equals("db/CURRENT");hasManifest|=path.matches("db/MANIFEST-[0-9]+");db.add(e);dbBytes=Math.addExact(dbBytes,size);
            } else if(Arrays.asList("chain/genesis_data.json","config.yml","jsonrpc/descriptors/koinos_descriptors.pb").contains(path)) {
                if(!row.getBoolean("runtime_file"))throw new IOException("Snapshot runtime file is mislabeled.");
                if(path.equals("chain/genesis_data.json")){if(size>METADATA_LIMIT)throw new IOException("Genesis is too large.");genesisEntry=e;}
            } else throw new IOException("Snapshot contains an unsupported file path. No active data was changed.");
        }
        if(!current||!hasManifest||genesisEntry==null||dbBytes==0||total>MAX_BYTES||total!=number(summary,"total_bytes")||objects.size()!=number(summary,"object_count"))throw new IOException("Snapshot inventory is incomplete or inconsistent.");
        database=Collections.unmodifiableList(db);genesis=genesisEntry;bytes=dbBytes;
    }
    static String hash(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    void verifyGenesis(byte[] downloaded,byte[] bundled)throws Exception {
        if(downloaded.length!=genesis.size||!hash(downloaded).equals(genesis.hash))throw new IOException("Snapshot genesis checksum failed.");
        if(!canonical(new JSONObject(new String(downloaded,StandardCharsets.UTF_8)),0).equals(canonical(new JSONObject(new String(bundled,StandardCharsets.UTF_8)),0)))throw new IOException("The published snapshot has a different genesis from this app's "+network+" network. Use Start observer to sync from genesis, or wait for a matching snapshot. No chain data was downloaded or replaced.");
    }
    private static String canonical(Object value,int depth)throws Exception {
        if(depth>40)throw new IOException("Genesis nesting is too deep.");
        if(value instanceof JSONObject){JSONObject j=(JSONObject)value;List<String> keys=new ArrayList<>();j.keys().forEachRemaining(keys::add);Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String key:keys)s.append(JSONObject.quote(key)).append(':').append(canonical(j.get(key),depth+1)).append(',');return s.append('}').toString();}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder s=new StringBuilder("[");for(int i=0;i<a.length();i++)s.append(canonical(a.get(i),depth+1)).append(',');return s.append(']').toString();}
        if(value instanceof String)return JSONObject.quote((String)value);
        if(value instanceof Number)return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }
}
