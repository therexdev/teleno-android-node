package com.koinos.teleno;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** One private, inactive generation; completed files are rehashed and reused on resume. */
final class SnapshotDownload {
    interface Source {
        InputStream open(SnapshotPlan.Entry entry)throws Exception;
        default boolean resumable(){return false;}
        default InputStream open(SnapshotPlan.Entry entry,long offset)throws Exception{return open(entry);}
        default boolean retryable(IOException failure){return false;}
    }
    interface Pause {void waitMillis(long millis)throws Exception;}
    static final class RangeRestart extends IOException {RangeRestart(){super("The server requires this file to restart.");}}
    static final class NetworkFailure extends Exception {final IOException failure;NetworkFailure(IOException e){super(e);failure=e;}}
    interface Progress { void update(String phase,long done,long total,int files,int count)throws Exception; }
    interface Space { long free()throws Exception; }
    static byte[] read(InputStream in,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(n>limit-out.size())throw new IOException("Snapshot response exceeds its limit.");out.write(b,0,n);}return out.toByteArray();}
    static void plain(File file,boolean directory)throws IOException {
        Path p=file.toPath();if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)&&!(directory?Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS):Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)))throw new IOException("Unexpected link or special file in snapshot storage.");
    }
    static boolean verified(File file,SnapshotPlan.Entry e,Progress progress,long done,long total,int count,int all)throws Exception {
        plain(file,false);if(!file.exists()||file.length()!=e.size)return false;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[512*1024];int n;
        try(InputStream in=new FileInputStream(file)){while((n=in.read(b))!=-1){digest.update(b,0,n);progress.update("Verifying saved files",done,total,count,all);}}
        return e.hash.equals(SnapshotPlan.hex(digest.digest()));
    }
    static void run(SnapshotPlan plan,File generation,Source source,Space space,Progress progress)throws Exception {
        run(plan,generation,source,space,progress,Thread::sleep);
    }
    static void run(SnapshotPlan plan,File generation,Source source,Space space,Progress progress,Pause pause)throws Exception {
        plain(generation,true);if(!generation.isDirectory())throw new IOException("Snapshot storage is unavailable.");
        File db=new File(generation,"db");plain(db,true);if(!db.isDirectory()&&!db.mkdir())throw new IOException("Could not create the snapshot database directory.");
        Set<String> allowed=new HashSet<>();for(SnapshotPlan.Entry e:plan.database){allowed.add(e.path.substring(3));allowed.add(e.path.substring(3)+".download-part");}
        File[] existing=db.listFiles();if(existing==null)throw new IOException("Snapshot storage cannot be read.");
        for(File f:existing)if(!allowed.contains(f.getName()))throw new IOException("Snapshot storage contains an unexpected file. Data was preserved.");
        Set<String> complete=new HashSet<>();long done=0,partialBytes=0;int count=0;
        for(SnapshotPlan.Entry e:plan.database){
            File file=new File(generation,e.path),part=new File(file+".download-part");plain(part,false);
            if(part.exists()&&(!source.resumable()||part.length()>e.size)&&!part.delete())throw new IOException("Could not reset an incomplete download.");
            if(verified(file,e,progress,done,plan.bytes,count,plan.database.size())){complete.add(e.path);done+=e.size;count++;}
            else {if(file.exists()&&!file.delete())throw new IOException("Could not replace an unverified snapshot file.");if(part.exists())partialBytes+=part.length();}
        }
        if(space.free()<Math.addExact(plan.bytes-done-partialBytes,SnapshotPlan.RESERVE))throw new IOException("Not enough space on the selected storage. The remaining download needs "+gib(plan.bytes-done-partialBytes)+" GiB plus a 2 GiB reserve. Existing chain data is preserved.");
        byte[] buffer=new byte[512*1024];
        for(SnapshotPlan.Entry e:plan.database){
            if(complete.contains(e.path))continue;
            File target=new File(generation,e.path),part=new File(target+".download-part");
            progress.update("Downloading chain snapshot",done,plan.bytes,count,plan.database.size());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long received=0;int retries=0;
            if(source.resumable()&&part.exists())try(InputStream in=new FileInputStream(part)){int n;while((n=in.read(buffer))!=-1){digest.update(buffer,0,n);received+=n;progress.update("Checking saved download portion",done+received,plan.bytes,count,plan.database.size());}}
            while(received<e.size) {
                try {
                    InputStream opened;try{opened=source.open(e,received);}catch(IOException failure){throw new NetworkFailure(failure);}
                    try(InputStream in=opened;FileOutputStream out=new FileOutputStream(part,received>0)) {
                        int n;while(true){
                            try{n=in.read(buffer);}catch(IOException failure){throw new NetworkFailure(failure);}
                            if(n==-1)break;
                            if(n>e.size-received)throw new IOException("A snapshot file exceeds its published size.");
                            out.write(buffer,0,n);digest.update(buffer,0,n);received+=n;
                            progress.update("Downloading chain snapshot",done+received,plan.bytes,count,plan.database.size());
                        }
                        out.getFD().sync();
                    }
                    if(received<e.size)throw new NetworkFailure(new EOFException("The snapshot connection ended early."));
                } catch(NetworkFailure failure) {
                    IOException error=failure.failure;
                    if(!source.retryable(error))throw error;
                    if(retries++>=4)throw new IOException("Snapshot download paused after 4 automatic reconnects. Saved file portions and verified files are kept. Check your connection, then tap Resume chain download.",error);
                    if(!source.resumable()||error instanceof RangeRestart){if(part.exists()&&!part.delete())throw new IOException("Could not reset the current download file.");received=0;digest.reset();}
                    long wait=1000L*(1<<retries);
                    while(wait>0){progress.update("Reconnecting secure download · attempt "+retries+"/4 in "+((wait+999)/1000)+"s",done+received,plan.bytes,count,plan.database.size());long step=Math.min(wait,200);pause.waitMillis(step);wait-=step;}
                }
            }
            if(e.size==0&&!part.exists())try(FileOutputStream out=new FileOutputStream(part)){out.getFD().sync();}
            if(received!=e.size||!e.hash.equals(SnapshotPlan.hex(digest.digest()))){part.delete();throw new IOException("Snapshot checksum failed. Retry to download this file again; verified files are kept.");}
            progress.update("Verifying downloaded files",done+received,plan.bytes,count,plan.database.size());
            if(!verified(part,e,progress,done+received,plan.bytes,count,plan.database.size())){part.delete();throw new IOException("Snapshot storage verification failed. Check the SD card or phone storage, then retry.");}
            Files.move(part.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);done+=received;count++;
        }
        String current;
        try(InputStream in=new FileInputStream(new File(db,"CURRENT"))){current=new String(read(in,256),StandardCharsets.US_ASCII);}
        if(!current.matches("MANIFEST-[0-9]+\n")||!allowed.contains(current.trim())||!new File(db,current.trim()).isFile())throw new IOException("The restored RocksDB manifest pointer is invalid.");
        progress.update("Snapshot verified",done,plan.bytes,count,plan.database.size());
    }
    static long estimatedRemaining(SnapshotPlan plan,File generation)throws IOException {
        long remaining=plan.bytes;
        for(SnapshotPlan.Entry e:plan.database){
            File file=new File(generation,e.path),partial=new File(file+".download-part");
            if(Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)&&file.length()==e.size)remaining-=e.size;
            else if(Files.isRegularFile(partial.toPath(),LinkOption.NOFOLLOW_LINKS))remaining-=Math.min(e.size,partial.length());
        }
        return Math.max(0,remaining);
    }
    static String gib(long bytes){return String.format(Locale.ROOT,"%.2f",bytes/1073741824.0);}
}
