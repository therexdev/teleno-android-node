package com.koinos.teleno;

import android.content.*;
import android.os.*;
import android.os.storage.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Only database bytes move. Native ownership, configuration, P2P identity and keys stay private. */
final class NodeStorage {
    static final long RESERVE = 2L * 1024 * 1024 * 1024;
    static final class Choice {
        final String id, label; final File root;
        Choice(String id, String label, File root) { this.id=id; this.label=label; this.root=root; }
        long free() { return new StatFs(root.getPath()).getAvailableBytes(); }
    }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("storage", Context.MODE_PRIVATE); }
    static List<Choice> choices(Context c) {
        List<Choice> out = new ArrayList<>(); out.add(new Choice("phone", "Phone storage", c.getNoBackupFilesDir()));
        StorageManager manager = c.getSystemService(StorageManager.class);
        for (File file : c.getExternalFilesDirs(null)) {
            if (file == null) continue;
            try {
                StorageVolume volume = manager.getStorageVolume(file);
                if (volume == null || !volume.isRemovable() || volume.getUuid() == null || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(file))) continue;
                out.add(new Choice("sd:" + volume.getUuid(), "SD card · " + volume.getDescription(c), file));
            } catch (RuntimeException ignored) { /* unavailable volume is not selectable */ }
        }
        return out;
    }
    static Choice resolve(Context c, String id) throws IOException {
        for (Choice choice : choices(c)) if (choice.id.equals(id)) return choice;
        throw new IOException("The selected SD card is unavailable. Reinsert the same card and wait for Android to mount it. Phone storage will not be used as a fallback.");
    }
    static String volume(Context c, String n) { return prefs(c).getString(n + "-volume", "phone"); }
    static String generation(Context c, String n) { return prefs(c).getString(n + "-generation", ""); }
    static String label(Context c, String n) { return prefs(c).getString(n + "-label", "Phone storage"); }
    static long free(Context c, String n) throws IOException { return resolve(c, volume(c,n)).free(); }
    private static String token(String value) throws IOException { if (!value.matches("[a-f0-9]{32}")) throw new IOException("Invalid storage record. Data was preserved."); return value; }
    static File folder(Context c, String n, String volume, String gen) throws IOException {
        NodeConfig.checkNetwork(n); return new File(resolve(c,volume).root, "teleno-lite-chain/" + n + "-" + token(gen));
    }
    private static File[] slots(Context c, String n) { File base = NodeConfig.root(c,n); return new File[]{new File(base,"db"),new File(base,"chain/blockchain")}; }
    private static File[] data(File directory) { return new File[]{new File(directory,"db"),new File(directory,"blockchain")}; }
    private static AtomicFile journal(Context c, String n) { return new AtomicFile(new File(c.getNoBackupFilesDir(),"storage-switch-" + n + ".json")); }
    private static boolean hasJournal(Context c, String n) { return journal(c,n).getBaseFile().exists() || new File(journal(c,n).getBaseFile()+".bak").exists(); }
    static boolean retained(Context c, String n) { return prefs(c).contains(n + "-retained"); }
    static boolean canClean(Context c, String n) { return retained(c,n) && generation(c,n).equals(prefs(c).getString(n + "-validated", "none")); }
    static void validated(Context c, String n) { if (!generation(c,n).isEmpty() && !generation(c,n).equals(prefs(c).getString(n + "-validated", ""))) prefs(c).edit().putString(n + "-validated",generation(c,n)).apply(); }
    private static void marker(File directory, String gen) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(directory,"storage.id"))) { out.write(gen.getBytes(StandardCharsets.US_ASCII)); out.getFD().sync(); }
    }
    private static void checkMarker(File directory, String gen) throws Exception {
        try (InputStream in = new FileInputStream(new File(directory,"storage.id"))) {
            if (!gen.equals(new String(NodeConfig.readBounded(in,64),StandardCharsets.US_ASCII))) throw new IOException("The selected chain directory does not match its saved identity.");
        }
        for (File path : data(directory)) if (!Files.isDirectory(path.toPath(),LinkOption.NOFOLLOW_LINKS)) throw new IOException("The selected database directory is missing. Original copies were preserved.");
    }
    static void ensureReady(Context c, String n) throws Exception {
        if (hasJournal(c,n)) {
            NodeConfig.root(c,n).mkdirs(); int lock=NativeFiles.lock(new File(NodeConfig.root(c,n),"node.owner.lock").getPath());
            try { JSONObject j = new JSONObject(new String(journal(c,n).readFully(),StandardCharsets.UTF_8)); finishSwitch(c,n,j); }
            finally { NativeFiles.unlock(lock); }
        }
        String gen=generation(c,n); Choice selected=resolve(c,volume(c,n));
        if (!gen.isEmpty()) {
            File directory=folder(c,n,selected.id,gen); checkMarker(directory,gen); File[] targets=data(directory), links=slots(c,n);
            boolean rebase=false;
            for (int i=0;i<links.length;i++) {
                if(!Files.exists(links[i].toPath(),LinkOption.NOFOLLOW_LINKS)){rebase=true;continue;}
                if(!Files.isSymbolicLink(links[i].toPath()))throw new IOException("Storage routing needs attention. Use Find existing chain data; do not reset app data or download again yet.");
                Path old=Files.readSymbolicLink(links[i].toPath());
                if(!old.equals(targets[i].toPath())) {
                    if(!old.endsWith(Paths.get("teleno-lite-chain",n+"-"+gen,targets[i].getName())))throw new IOException("Unexpected database routing. Data was preserved.");
                    rebase=true;
                }
            }
            if(rebase) {
                File base=NodeConfig.root(c,n);if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Could not repair the private node folder. The saved chain is preserved.");
                int lock=NativeFiles.lock(new File(NodeConfig.root(c,n),"node.owner.lock").getPath());
                try{StorageLinks.publish(links,targets,gen,index->syncPrivateDirectory(links[index].getParent()));}finally{NativeFiles.unlock(lock);}
            }
        } else for (File f:slots(c,n)) if (Files.isSymbolicLink(f.toPath())) throw new IOException("Unexpected database link. Data was preserved.");
    }
    static void checkMounted(Context c, String n) throws Exception {
        Choice choice=resolve(c,volume(c,n));
        String gen=generation(c,n); if(!gen.isEmpty()) checkMarker(folder(c,n,choice.id,gen),gen);
        if (choice.free() < RESERVE) throw new IOException("The selected chain storage has less than 2 GiB free. Free space on " + choice.label + " before restarting.");
    }
    static boolean hasChain(Context c,String n) {
        try {
            String gen=generation(c,n);
            return ChainData.usable(gen.isEmpty()?slots(c,n)[0]:data(folder(c,n,volume(c,n),gen))[0]);
        }catch(Exception e){return false;}
    }
    static final class Existing {
        final String volume,generation,label;final File db;final long bytes;
        Existing(String volume,String generation,String label,File db,long bytes){this.volume=volume;this.generation=generation;this.label=label;this.db=db;this.bytes=bytes;}
        String description(){return label+" · "+SnapshotDownload.gib(bytes)+" GiB\n"+db.getPath();}
    }
    static List<Existing> findExisting(Context c,String n)throws Exception {
        NodeConfig.checkNetwork(n);List<Existing> out=new ArrayList<>();
        File legacy=slots(c,n)[0];
        if(ChainData.usable(legacy))out.add(new Existing("phone","","Phone storage · original database",legacy,StorageFiles.size(new File[]{legacy})));
        for(Choice choice:choices(c)){
            File parent=new File(choice.root,"teleno-lite-chain");File[] dirs=parent.listFiles();if(dirs==null)continue;
            for(File f:dirs){
                String prefix=n+"-";if(!f.getName().matches(java.util.regex.Pattern.quote(prefix)+"[a-f0-9]{32}")||Files.isSymbolicLink(f.toPath()))continue;
                String gen=f.getName().substring(prefix.length());
                try{checkMarker(f,gen);File db=data(f)[0];if(ChainData.usable(db))out.add(new Existing(choice.id,gen,choice.label,db,StorageFiles.size(new File[]{db})));}
                catch(IOException ignored){/* Incomplete downloads are never offered as reusable chains. */}
            }
        }
        return out;
    }
    static boolean hasRecoverableChain(Context c,String n) {
        try {
            if(hasChain(c,n))return false;
            File legacy=slots(c,n)[0];if(Files.isSymbolicLink(legacy.toPath()))return true;
            for(Choice choice:choices(c)){
                File[] dirs=new File(choice.root,"teleno-lite-chain").listFiles();if(dirs==null)continue;
                for(File f:dirs)if(f.getName().matches(java.util.regex.Pattern.quote(n+"-")+"[a-f0-9]{32}")&&ChainData.usable(new File(f,"db")))return true;
            }
        }catch(Exception ignored){}return false;
    }
    static void reconnect(Context c,String n,String selected,String gen)throws Exception {
        NodeConfig.checkNetwork(n);File base=NodeConfig.root(c,n);base.mkdirs();
        int lock=NativeFiles.lock(new File(base,"node.owner.lock").getPath());
        try {
            if(hasJournal(c,n))throw new IOException("An interrupted storage switch must finish first. Try Start observer to complete its saved recovery; do not delete the database.");
            Choice choice=resolve(c,selected);
            if(gen.isEmpty()){
                if(!"phone".equals(selected)||!ChainData.usable(slots(c,n)[0]))throw new IOException("The original database is not available. No files were changed.");
            }else{
                File target=folder(c,n,selected,gen);checkMarker(target,gen);
                if(!ChainData.usable(data(target)[0]))throw new IOException("This folder does not contain a complete RocksDB manifest. No files were changed.");
                for(File slot:slots(c,n))if(Files.exists(slot.toPath(),LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(slot.toPath()))
                    throw new IOException("An original database still occupies the node folder. Keep it and select that original copy; recovery will not overwrite it.");
                // All targets were inspected before publishing. Existing directories are never deleted.
                StorageLinks.publish(slots(c,n),data(target),gen,index->syncPrivateDirectory(slots(c,n)[index].getParent()));
            }
            SharedPreferences.Editor edit=prefs(c).edit().putString(n+"-volume",selected).putString(n+"-label",choice.label).putString(n+"-generation",gen).remove(n+"-validated");
            JSONObject retained=new JSONObject(prefs(c).getString(n+"-retained","{}"));
            if(gen.equals(retained.optString("generation","-"))&&selected.equals(retained.optString("volume","-")))edit.remove(n+"-retained");
            if(!edit.commit())throw new IOException("The existing chain was found, but its selection could not be saved. No database files were deleted. Retry recovery.");
            if(!hasChain(c,n))throw new IOException("The selected chain could not be read. Existing files were preserved.");
        }finally{NativeFiles.unlock(lock);}
    }
    static boolean completeGeneration(Context c,String n,String selected,String gen) {
        try{File directory=folder(c,n,selected,gen);checkMarker(directory,gen);return ChainData.usable(data(directory)[0]);}
        catch(Exception e){return false;}
    }
    static void protectReferences(Context c,String n,File removal)throws IOException {
        for(File slot:slots(c,n))if(Files.exists(slot.toPath(),LinkOption.NOFOLLOW_LINKS))ChainData.protectActive(slot,removal);
        if(!generation(c,n).isEmpty())ChainData.protectActive(folder(c,n,volume(c,n),generation(c,n)),removal);
    }
    static String report(Context c,String n) {
        return "Chain storage: "+label(c,n)+"\nSaved storage volume: "+volume(c,n)+"\nSaved generation: "+generation(c,n)
            +"\nChain manifest present: "+hasChain(c,n)+"\nOther saved chain found: "+hasRecoverableChain(c,n)
            +"\nNode folder: "+NodeConfig.root(c,n).getPath()+"\nBackup retained: "+retained(c,n)+"\nSnapshot record present: "+SnapshotBootstrap.pending(c,n);
    }
    private static void probe(File directory) throws Exception {
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("Could not create chain storage on this device.");
        File a=new File(directory,".write-probe"), b=new File(directory,".rename-probe");
        try {
            try (RandomAccessFile f=new RandomAccessFile(a,"rw"); FileLock lock=f.getChannel().tryLock()) {
                if(lock==null) throw new IOException("The card cannot lock database files.");
                f.writeLong(0x54454c454e4fL); f.getFD().sync();
            }
            Files.move(a.toPath(),b.toPath());
            try(RandomAccessFile f=new RandomAccessFile(b,"r")){ if(f.readLong()!=0x54454c454e4fL) throw new IOException("The card failed its storage check."); }
        } finally { a.delete(); b.delete(); }
    }
    static void change(Context c, String n, String destination, StorageFiles.Progress progress) throws Exception {
        ensureReady(c,n);
        if(retained(c,n)) throw new IOException("Validate the observer on the current storage, stop it, then remove the retained previous copy before another move.");
        Choice target=resolve(c,destination); if(target.id.equals(volume(c,n))) throw new IOException("This storage is already selected.");
        File base=NodeConfig.root(c,n); base.mkdirs(); int lock=NativeFiles.lock(new File(base,"node.owner.lock").getPath());
        File fresh=null; boolean committedJournal=false;
        try {
            String gen=UUID.randomUUID().toString().replace("-",""); fresh=folder(c,n,target.id,gen);
            probe(fresh);
            String previous=generation(c,n); File[] source=previous.isEmpty()?slots(c,n):data(folder(c,n,volume(c,n),previous));
            long bytes=StorageFiles.size(source);
            if(target.free()<bytes+RESERVE) throw new IOException("The destination needs room for the current database plus a 2 GiB reserve.");
            final String chosen=target.id;
            StorageFiles.copy(source,data(fresh),(copied,total,stage)->{
                if(resolve(c,chosen).free()<RESERVE) throw new IOException("Destination storage is low. Original data remains selected.");
                progress.update(copied,total,stage);
            });
            marker(fresh,gen);
            JSONObject j=new JSONObject().put("volume",target.id).put("label",target.label).put("generation",gen)
                .put("oldVolume",volume(c,n)).put("oldGeneration",previous).put("oldLabel",label(c,n)).put("keepOld",bytes>0);
            // This durable intent is written only after every copied file passes its checksum.
            NodeConfig.writePrivate(journal(c,n).getBaseFile(),j.toString().getBytes(StandardCharsets.UTF_8)); committedJournal=true;
            finishSwitch(c,n,j);
        } finally {
            NativeFiles.unlock(lock);
            if(!committedJournal && fresh!=null) { try { StorageFiles.deleteTree(fresh); } catch(Exception ignored) { /* never delete the source or choose a partial copy */ } }
        }
    }
    private static void finishSwitch(Context c,String n,JSONObject j) throws Exception {
        String gen=token(j.getString("generation")); File directory=folder(c,n,j.getString("volume"),gen); checkMarker(directory,gen);
        File[] links=slots(c,n), targets=data(directory);
        if(j.has("snapshot")) {
            String id=SnapshotPlan.id(j.getString("snapshot"));
            NodeConfig.writePrivate(new File(NodeConfig.root(c,n),".backup-just-restored"),("backup_id="+id+"\nblock_producer_disabled_on_first_start=true\nstart_as_observer_first=true\n").getBytes(StandardCharsets.UTF_8));
            NodeConfig.writePrivate(new File(NodeConfig.root(c,n),".teleno-android-restore.json"),new JSONObject().put("backup_id",id).put("generation",gen).put("network",n).put("observer_first",true).toString().getBytes(StandardCharsets.UTF_8));
            syncPrivateDirectory(NodeConfig.root(c,n).getPath());
        }
        StorageLinks.publish(links,targets,gen,index->syncPrivateDirectory(links[index].getParent()));
        JSONObject retained=new JSONObject().put("volume",j.getString("oldVolume")).put("generation",j.getString("oldGeneration"))
            .put("label",j.getString("oldLabel")).put("backup",gen);
        SharedPreferences.Editor save=prefs(c).edit().putString(n+"-volume",j.getString("volume")).putString(n+"-label",j.getString("label"))
            .putString(n+"-generation",gen).remove(n+"-validated");
        if(j.optBoolean("keepOld",true))save.putString(n+"-retained",retained.toString());else save.remove(n+"-retained");
        if(!save.commit()) throw new IOException("Storage selection could not be saved. Keep the card inserted and retry.");
        journal(c,n).delete();
    }
    static File prepareSnapshot(Context c,String n,String selected,String gen)throws Exception {
        if(!selected.equals(volume(c,n)))throw new IOException("Storage selection changed. Keep the original card inserted to resume.");
        File fresh=folder(c,n,selected,gen);SnapshotDownload.plain(fresh,true);probe(fresh);return fresh;
    }
    // Caller holds the native ownership lock and has verified every staged file.
    static void activateSnapshot(Context c,String n,String selected,String gen,String snapshot)throws Exception {
        if(retained(c,n))throw new IOException("Validate and remove the retained previous copy before restoring another snapshot.");
        File fresh=folder(c,n,selected,gen);File legacy=new File(fresh,"blockchain");if(!legacy.isDirectory()&&!legacy.mkdir())throw new IOException("Could not prepare restored chain storage.");
        marker(fresh,gen);
        String previous=generation(c,n);File[] old=previous.isEmpty()?slots(c,n):data(folder(c,n,volume(c,n),previous));
        boolean keep=false;for(File f:old){String[] names=f.list();if(names!=null&&names.length>0)keep=true;}
        Choice choice=resolve(c,selected);
        JSONObject j=new JSONObject().put("volume",selected).put("label",choice.label).put("generation",gen).put("snapshot",SnapshotPlan.id(snapshot))
            .put("oldVolume",volume(c,n)).put("oldGeneration",previous).put("oldLabel",label(c,n)).put("keepOld",keep);
        NodeConfig.writePrivate(journal(c,n).getBaseFile(),j.toString().getBytes(StandardCharsets.UTF_8));
        finishSwitch(c,n,j);
    }
    private static void syncPrivateDirectory(String path) throws Exception {
        java.io.FileDescriptor fd=android.system.Os.open(path,android.system.OsConstants.O_RDONLY,0);
        try{android.system.Os.fsync(fd);}finally{android.system.Os.close(fd);}
    }
    static void clean(Context c,String n) throws Exception {
        ensureReady(c,n); if(!canClean(c,n)) throw new IOException("First validate an advancing observer on the new storage, then stop it.");
        int lock=NativeFiles.lock(new File(NodeConfig.root(c,n),"node.owner.lock").getPath());
        try {
            if(!hasChain(c,n))throw new IOException("The active chain is not readable. Backup deletion is blocked; use Find existing chain data first.");
            File[] active=generation(c,n).isEmpty()?slots(c,n):data(folder(c,n,volume(c,n),generation(c,n)));
            JSONObject old=new JSONObject(prefs(c).getString(n+"-retained","{}")); String gen=old.getString("generation");
            if(gen.isEmpty()) {
                String tag=token(old.getString("backup"));
                for(File slot:slots(c,n)){File removal=new File(slot.getParentFile(),".previous-"+tag+"-"+slot.getName());for(File current:active)ChainData.protectActive(current,removal);}
                for(File slot:slots(c,n))StorageFiles.deleteTree(new File(slot.getParentFile(),".previous-"+tag+"-"+slot.getName()));
            } else {
                if(gen.equals(generation(c,n))) throw new IOException("Refusing to remove the active database.");
                File removal=folder(c,n,old.getString("volume"),gen);for(File current:active)ChainData.protectActive(current,removal);
                StorageFiles.deleteTree(removal);
            }
            if(!prefs(c).edit().remove(n+"-retained").commit()) throw new IOException("Cleanup finished; its saved status could not be updated.");
        } finally { NativeFiles.unlock(lock); }
    }
}
