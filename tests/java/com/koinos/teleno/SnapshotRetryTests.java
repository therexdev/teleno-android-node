package com.koinos.teleno;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.*;

public final class SnapshotRetryTests {
    static int assertions;
    static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void rejects(Action a,String message)throws Exception{boolean rejected=false;try{a.run();}catch(Exception e){rejected=true;}check(rejected,message);}
    static class RangeSource extends SnapshotTests.Fixture {
        final List<Long> offsets=new ArrayList<>();int breaks=1;boolean ignoredRange;
        RangeSource()throws Exception{super();}
        public boolean resumable(){return true;}
        public boolean retryable(IOException e){return e instanceof SSLException||e instanceof SnapshotDownload.RangeRestart;}
        public InputStream open(SnapshotPlan.Entry e,long offset)throws Exception {
            byte[] b=content.get(e.hash);if(!e.path.endsWith(".sst"))return new ByteArrayInputStream(b,(int)offset,b.length-(int)offset);
            offsets.add(offset);if(ignoredRange&&offset>0){ignoredRange=false;throw new SnapshotDownload.RangeRestart();}
            return new InputStream(){int at=(int)offset;boolean fail=breaks>0;int allowance=300000;
                public int read()throws IOException{byte[] v=new byte[1];return read(v)==-1?-1:v[0]&255;}
                public int read(byte[] out,int start,int length)throws IOException{if(fail&&allowance==0){breaks--;throw new SSLException("DECRYPTION_FAILED_OR_BAD_RECORD_MAC");}if(at==b.length)return -1;int n=Math.min(length,b.length-at);if(fail)n=Math.min(n,allowance);System.arraycopy(b,at,out,start,n);at+=n;allowance-=n;return n;}
            };
        }
    }
    public static void main(String[] args)throws Exception{
        SnapshotSource policy=new SnapshotSource("mainnet");
        check(policy.retryable(new SSLException("BAD_RECORD_MAC")),"TLS record failure is reconnectable");
        check(policy.retryable(new java.net.SocketTimeoutException()),"Read timeout is reconnectable");
        check(policy.retryable(new EOFException()),"Early EOF is reconnectable");
        check(!policy.retryable(new SSLHandshakeException("certificate")),"Certificate/handshake failures are never bypassed");
        check(!policy.retryable(new SSLPeerUnverifiedException("hostname")),"Hostname verification retained");
        check(policy.retryable(new SnapshotSource.HttpFailure(503)),"Temporary server error retries");
        check(!policy.retryable(new SnapshotSource.HttpFailure(404)),"Removed snapshot does not loop");
        check(SnapshotSource.validRange("bytes 30-99/100",30,100),"Exact pinned byte range accepted");
        check(!SnapshotSource.validRange("bytes 0-99/100",30,100),"Wrong start rejected");
        check(!SnapshotSource.validRange("bytes 30-109/110",30,100),"Wrong total rejected");
        check(!SnapshotSource.validRange(null,30,100),"Missing Content-Range rejected");
        File stage=Files.createTempDirectory("snapshot-retry-").toFile();RangeSource source=new RangeSource();SnapshotPlan plan=source.plan();List<String> phases=new ArrayList<>();
        try{
            SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,(p,d,t,f,a)->{SnapshotTests.progress.update(p,d,t,f,a);if(p.startsWith("Reconnecting"))phases.add(p);},millis->{});
            check(source.offsets.equals(Arrays.asList(0L,300000L)),"TLS failure resumes at saved byte without redownloading prefix");
            check(!phases.isEmpty(),"Reconnect status visible");
            File sst=new File(stage,"db/000002.sst");byte[] expected=source.content.get(plan.database.stream().filter(e->e.path.endsWith(".sst")).findFirst().get().hash);
            check(Arrays.equals(Files.readAllBytes(sst.toPath()),expected),"Resumed file matches exact original bytes");
            Files.delete(sst.toPath());source.offsets.clear();source.breaks=20;
            rejects(()->SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,SnapshotTests.progress,millis->{}),"Persistent TLS failure stops after bounded retries");
            check(source.offsets.size()==5,"One attempt plus four reconnects only");
            File part=new File(sst+".download-part");check(part.length()>0&&!sst.exists(),"Partial remains inactive for later resume");
            long saved=part.length();source.breaks=0;source.offsets.clear();SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,SnapshotTests.progress,millis->{});
            check(source.offsets.get(0)==saved,"New run resumes persisted partial file");
            Files.delete(sst.toPath());source.breaks=1;source.offsets.clear();
            rejects(()->SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,(p,d,t,f,a)->{if(p.startsWith("Reconnecting"))throw new IOException("Cancelled by user");},millis->{}),"Cancel during reconnect stops immediately");
            check(source.offsets.size()==1,"Cancellation does not open another connection");
            source.breaks=0;source.ignoredRange=true;source.offsets.clear();SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,SnapshotTests.progress,millis->{});
            check(source.offsets.size()==2&&source.offsets.get(0)>0&&source.offsets.get(1)==0,"Server ignoring Range restarts only current file safely");
            Files.delete(sst.toPath());byte[] bad=Arrays.copyOf(expected,300000);bad[0]^=1;Files.write(part.toPath(),bad);source.offsets.clear();
            rejects(()->SnapshotDownload.run(plan,stage,source,()->Long.MAX_VALUE,SnapshotTests.progress,millis->{}),"Corrupt persisted prefix fails final checksum");
            check(!sst.exists()&&!part.exists(),"Corrupt reconstructed file never becomes active");
        }finally{StorageFiles.deleteTree(stage);}
        System.out.println("PASS: "+assertions+" TLS retry, partial resume, cancellation and integrity assertions.");
    }
}
