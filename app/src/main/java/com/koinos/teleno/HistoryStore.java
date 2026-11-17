package com.koinos.teleno;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigInteger;

/** Persistent public-chain records and fixed, secret-free app action summaries. */
final class HistoryStore extends SQLiteOpenHelper {
    private static HistoryStore instance;
    private static final ExecutorService journal=Executors.newSingleThreadExecutor();
    static volatile String storageError="";
    static synchronized HistoryStore get(Context c) {if(instance==null)instance=new HistoryStore(c.getApplicationContext());return instance;}
    private HistoryStore(Context c){super(c,"activity.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries(network TEXT NOT NULL,account TEXT NOT NULL,id TEXT NOT NULL,source TEXT NOT NULL,reward INTEGER NOT NULL,time INTEGER NOT NULL,seq TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(network,account,id))");
        db.execSQL("CREATE INDEX timeline ON entries(network,account,time DESC,seq DESC)");
        db.execSQL("CREATE TABLE cache(network TEXT NOT NULL,account TEXT NOT NULL,name TEXT NOT NULL,value TEXT NOT NULL,PRIMARY KEY(network,account,name))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("History migration required.");}
    static void record(Context context,String network,String title,String detail) {
        final Context c=context.getApplicationContext();final long when=System.currentTimeMillis();
        final String account=c.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("producer-"+network,"");
        journal.execute(()->{try {
            NodeConfig.checkNetwork(network);WalletReader.Entry e=new WalletReader.Entry();e.id="local-"+UUID.randomUUID();e.title=title;e.detail=detail;e.time=when;
            get(c).put(network,account,e,"local");storageError="";
        }catch(Exception error){storageError="App history could not be saved. Check available storage.";}});
    }
    private synchronized void put(String network,String account,WalletReader.Entry e,String source) throws Exception {
        ContentValues v=new ContentValues();v.put("network",network);v.put("account",account);v.put("id",e.id);v.put("source",source);v.put("reward",e.reward?1:0);v.put("time",e.time);
        v.put("seq",String.format(Locale.ROOT,"%020d",WalletReader.uint(e.seq)));v.put("value",e.json().toString());
        getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized String cache(String n,String a,String name) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM cache WHERE network=? AND account=? AND name=?",new String[]{n,a,name})){return c.moveToFirst()?c.getString(0):null;}
    }
    synchronized void cache(String n,String a,String name,String value) {
        ContentValues v=new ContentValues();v.put("network",n);v.put("account",a);v.put("name",name);v.put("value",value);getWritableDatabase().insertWithOnConflict("cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized void savePage(String n,String a,WalletReader.Page page,boolean recent) throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            String before=cache(n,a,"newest"),cursor=cache(n,a,"cursor");
            for(WalletReader.Entry e:page.entries)put(n,a,e,"chain");
            if(!recent||cursor==null||before==null||(page.count>0&&WalletReader.uint(page.oldest).compareTo(WalletReader.uint(before).add(BigInteger.ONE))>0))cache(n,a,"cursor",page.next==null?"-1":page.next);
            BigInteger newest=before==null?BigInteger.ZERO:WalletReader.uint(before);
            cache(n,a,"newest",newest.max(WalletReader.uint(page.newest)).toString());cache(n,a,"historyAt",Long.toString(System.currentTimeMillis()));
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private String where(String filter) {return "network=? AND (account=? OR (source='local' AND account=''))"+("Rewards".equals(filter)?" AND reward=1":"Actions".equals(filter)?" AND source='local'":"");}
    synchronized List<WalletReader.Entry> rows(String n,String a,String filter,int offset,int limit) throws Exception {
        List<WalletReader.Entry> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM entries WHERE "+where(filter)+" ORDER BY time DESC,seq DESC,id DESC LIMIT ? OFFSET ?",new String[]{n,a,Integer.toString(Math.min(50,Math.max(1,limit))),Integer.toString(Math.max(0,offset))})) {
            while(c.moveToNext())out.add(WalletReader.Entry.from(new JSONObject(c.getString(0))));
        }return out;
    }
    synchronized int count(String n,String a,String filter) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM entries WHERE "+where(filter),new String[]{n,a})){return c.moveToFirst()?c.getInt(0):0;}
    }
    static final class RewardTotal {
        BigInteger koin=BigInteger.ZERO;
        long count;
    }
    /** Sum every cached irreversible reward in this run, not just the three-row feed.
     * Token atoms stay integers; SQLite floating-point SUM would lose precision. */
    synchronized RewardTotal rewardsForRun(String n,String a,long start,long end) throws Exception {
        RewardTotal total=new RewardTotal();
        if(start<=0||a.isEmpty())return total;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT value FROM entries WHERE network=? AND account=? AND source='chain' AND reward=1 AND time>=? AND time<=?",
                new String[]{n,a,Long.toString(start),Long.toString(end>0?end:Long.MAX_VALUE)})) {
            while(c.moveToNext()) {
                WalletReader.Entry e=WalletReader.Entry.from(new JSONObject(c.getString(0)));
                total.koin=total.koin.add(new BigInteger(e.koin));total.count++;
            }
        }
        return total;
    }
}
