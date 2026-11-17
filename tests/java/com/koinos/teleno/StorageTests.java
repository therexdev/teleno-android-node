package com.koinos.teleno;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class StorageTests {
    static int count;
    interface Run{void run()throws Exception;}
    static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
    static void rejects(Run r,String label)throws Exception{boolean failed=false;try{r.run();}catch(IOException e){failed=true;}check(failed,label);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("teleno-storage-test-");
        try{
            Path source=Files.createDirectories(root.resolve("source/db"));Files.createDirectories(source.resolve("nested"));
            byte[] bytes=new byte[1500000];new Random(8).nextBytes(bytes);Files.write(source.resolve("0001.sst"),bytes);Files.write(source.resolve("nested/CURRENT"),"MANIFEST-1".getBytes());
            File[] sources={source.toFile(),root.resolve("absent-legacy").toFile()};File[] copies={root.resolve("card/db").toFile(),root.resolve("card/blockchain").toFile()};
            long[] latest={0};StorageFiles.copy(sources,copies,(n,total,stage)->latest[0]=n);
            check(Arrays.equals(Files.readAllBytes(copies[0].toPath().resolve("0001.sst")),bytes),"large file checksum copy");
            check(latest[0]==StorageFiles.size(sources),"copy progress covers all bytes");
            check(copies[1].isDirectory(),"empty legacy directory supported");
            rejects(()->StorageFiles.copy(sources,copies,(n,t,s)->{}),"existing destination not overwritten");
            rejects(()->StorageFiles.copy(new File[]{source.toFile()},new File[]{root.resolve("cancelled").toFile()},(n,t,s)->{if(n>0)throw new IOException("cancel");}),"cancel stops transfer");
            check(Arrays.equals(Files.readAllBytes(source.resolve("0001.sst")),bytes),"source preserved after cancellation");
            Path linked=Files.createDirectories(root.resolve("linked"));Files.createSymbolicLink(linked.resolve("escape"),source);
            rejects(()->StorageFiles.size(new File[]{linked.toFile()}),"links inside database rejected");
            Path corruptSource=Files.createDirectories(root.resolve("one"));Files.write(corruptSource.resolve("item"),new byte[]{1,2,3});File corrupted=root.resolve("corrupted").toFile();boolean[] did={false};
            rejects(()->StorageFiles.copy(new File[]{corruptSource.toFile()},new File[]{corrupted},(n,t,s)->{if(n==3&&!did[0]){did[0]=true;Files.write(corrupted.toPath().resolve("item"),new byte[]{3,2,1});}}),"checksum detects bad destination before switch");
            Path privateRoot=Files.createDirectories(root.resolve("private"));File[] links={privateRoot.resolve("db").toFile(),privateRoot.resolve("chain/blockchain").toFile()};
            Files.createDirectories(links[0].toPath());Files.write(links[0].toPath().resolve("old"),new byte[]{7});
            String gen="0123456789abcdef0123456789abcdef";
            rejects(()->StorageLinks.publish(links,copies,gen,i->{if(i==0)throw new IOException("simulated process loss");}),"interruption between publications");
            check(Files.exists(privateRoot.resolve(".previous-"+gen+"-db/old")),"old physical database retained");
            StorageLinks.publish(links,copies,gen,i->{});
            check(Files.readSymbolicLink(links[0].toPath()).equals(copies[0].toPath())&&Files.readSymbolicLink(links[1].toPath()).equals(copies[1].toPath()),"interrupted publication safely resumes");
            StorageLinks.publish(links,copies,gen,i->{throw new AssertionError("already published");});check(true,"publication is idempotent");
            Files.delete(links[1].toPath());Files.createSymbolicLink(links[1].toPath(),root.resolve("gone-card"));
            check(!links[1].exists()&&Files.isSymbolicLink(links[1].toPath()),"missing card stays a dangling link, no phone fallback");
            File lock=privateRoot.resolve("owner.lock").toFile();int fd=NativeFiles.lock(lock.getPath());
            try{rejects(()->NativeFiles.lock(lock.getPath()),"JNI blocks second storage owner");
                Process child=new ProcessBuilder(args[0],lock.getPath(),privateRoot.resolve("never-key").toString(),"/bin/true").start();check(child.waitFor()==73,"JNI lease excludes native supervisor");
            }finally{NativeFiles.unlock(fd);}
            fd=NativeFiles.lock(lock.getPath());NativeFiles.unlock(fd);check(true,"lease released after operation");
            Path bad=privateRoot.resolve("link.lock");Files.createSymbolicLink(bad,lock.toPath());rejects(()->NativeFiles.lock(bad.toString()),"JNI rejects symlink lock");
            StorageFiles.deleteTree(corrupted);check(!corrupted.exists()&&Files.exists(corruptSource.resolve("item")),"cleanup restricted to retained copy");
            System.out.println("PASS: "+count+" storage copy, recovery and native ownership checks (host Linux).");
        }finally{try(java.util.stream.Stream<Path> walk=Files.walk(root)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    }
}
