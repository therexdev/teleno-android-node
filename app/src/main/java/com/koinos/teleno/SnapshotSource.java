package com.koinos.teleno;

import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Platform TLS and hostname checks; fixed Foundation origins, no redirects or arbitrary URLs. */
final class SnapshotSource implements SnapshotDownload.Source {
    final String network,base;
    SnapshotSource(String n)throws Exception{network=n;base=SnapshotPlan.base(n);}
    InputStream relative(String path)throws Exception {
        return relative(path,0,-1);
    }
    private InputStream relative(String path,long offset,long size)throws Exception {
        if(!path.matches("[A-Za-z0-9_./-]+")||path.contains("..")||path.startsWith("/"))throw new IOException("Invalid snapshot source path.");
        HttpsURLConnection c=(HttpsURLConnection)new URL(base+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);
        c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent","KAI-Teleno-Android/0.3");
        c.setRequestProperty("Connection","close");if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
        try{
            int status=c.getResponseCode();
            if(offset>0&&status==200)throw new SnapshotDownload.RangeRestart();
            if(status!=(offset>0?206:200))throw new HttpFailure(status);
            if(offset>0&&!validRange(c.getHeaderField("Content-Range"),offset,size))throw new IOException("The snapshot server returned a different file range. Saved data was not activated.");
            if(!"identity".equals(c.getHeaderField("Content-Encoding"))&&c.getHeaderField("Content-Encoding")!=null)throw new IOException("Unexpected snapshot encoding.");
            return new FilterInputStream(c.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{c.disconnect();}}};
        }catch(Exception e){c.disconnect();throw e;}
    }
    String text(String path,int limit)throws Exception {try(InputStream in=relative(path)){return new String(SnapshotDownload.read(in,limit),StandardCharsets.UTF_8);}}
    public InputStream open(SnapshotPlan.Entry entry)throws Exception{return relative(entry.objectPath());}
    @Override public InputStream open(SnapshotPlan.Entry entry,long offset)throws Exception{return relative(entry.objectPath(),offset,entry.size);}
    @Override public boolean resumable(){return true;}
    static boolean validRange(String header,long offset,long size){return header!=null&&offset>0&&offset<size&&header.equals("bytes "+offset+"-"+(size-1)+"/"+size);}
    static final class HttpFailure extends IOException {
        final int code;
        HttpFailure(int code){super(code==404?"The saved snapshot is no longer available from the Foundation. Discard the unfinished download and check for a newer snapshot.":"The Foundation snapshot server is unavailable (HTTP "+code+"). Retry later; verified files are kept.");this.code=code;}
    }
    @Override public boolean retryable(IOException e){
        if(Thread.currentThread().isInterrupted()||e instanceof javax.net.ssl.SSLHandshakeException||e instanceof javax.net.ssl.SSLPeerUnverifiedException)return false;
        if(e instanceof HttpFailure){int c=((HttpFailure)e).code;return c==408||c==429||c>=500&&c<=599;}
        return e instanceof SnapshotDownload.RangeRestart||e instanceof javax.net.ssl.SSLException||e instanceof java.net.SocketException||e instanceof java.net.SocketTimeoutException||e instanceof EOFException;
    }
    SnapshotPlan plan(String exact,byte[] bundledGenesis)throws Exception {
        String id=exact;
        if(id==null){JSONObject latest=new JSONObject(text("latest.json",64*1024));SnapshotPlan.format(latest,"teleno-native-latest-snapshot");id=SnapshotPlan.id(latest.getString("backup_id"));}
        id=SnapshotPlan.id(id);String dir="snapshots/"+id+"/";
        SnapshotPlan p=new SnapshotPlan(network,id,text(dir+"manifest.json",SnapshotPlan.METADATA_LIMIT),text(dir+"files.json",SnapshotPlan.METADATA_LIMIT));
        if(!"complete\n".equals(text(dir+"COMPLETE",64)))throw new IOException("The public snapshot is not complete yet. Retry later.");
        try(InputStream in=open(p.genesis)){p.verifyGenesis(SnapshotDownload.read(in,SnapshotPlan.METADATA_LIMIT),bundledGenesis);}
        return p;
    }
}
