package com.koinos.teleno;

import android.content.Context;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Independent UI reads; never use the signing/review worker or touch private keys. */
final class DashboardController {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);private final Runnable changed;
    String network="",account="",balanceError="",historyError="",filter="All",cursor;
    WalletReader.Balance balance;
    List<WalletReader.Entry> rewards=new ArrayList<>(),rows=new ArrayList<>();
    long runStartedAt,runEndedAt;
    HistoryStore.RewardTotal runRewards=new HistoryStore.RewardTotal();
    boolean runRewardsReady;
    int offset,total;boolean balancesBusy,historyBusy,closed;private long generation,lastBalance,lastHistory,lastRows;private boolean rowsBusy;
    DashboardController(Context c,Runnable changed){context=c.getApplicationContext();this.changed=changed;}
    void select(String n,String a) {
        if(n.equals(network)&&a.equals(account))return;
        network=n;account=a;generation++;balance=null;balanceError=historyError="";cursor=null;offset=total=0;rewards=new ArrayList<>();rows=new ArrayList<>();
        balancesBusy=historyBusy=rowsBusy=false;lastBalance=lastHistory=lastRows=0;
        runStartedAt=runEndedAt=0;runRewardsReady=false;runRewards=new HistoryStore.RewardTotal();
        final long g=generation;
        io.execute(()->{WalletReader.Balance cached=null;try{String json=HistoryStore.get(context).cache(n,a,"balances");if(json!=null)cached=WalletReader.Balance.from(new JSONObject(json));}catch(Exception ignored){}final WalletReader.Balance found=cached;main.post(()->{if(current(g)){if(balance==null)balance=found;changed.run();}});});
        refreshRows();
    }
    private boolean current(long g){return !closed&&generation==g;}
    void run(NodeService.ViewState node) {
        long start=network.equals(node.runNetwork)&&account.equals(node.runAccount)?node.runStartedAt:0;
        long end=start>0?node.runEndedAt:0;
        if(start==runStartedAt&&end==runEndedAt)return;
        runStartedAt=start;runEndedAt=end;runRewardsReady=false;runRewards=new HistoryStore.RewardTotal();lastRows=0;
        refreshRows();
    }
    void tick(){long now=System.currentTimeMillis();if(now-lastRows>5000)refreshRows();if(account.isEmpty())return;if(now-lastBalance>30000)loadBalance();if(now-lastHistory>60000)loadHistory(false);}
    void refresh(){lastBalance=lastHistory=0;tick();}
    void page(int direction){offset=Math.max(0,offset+direction*25);refreshRows();}
    void filter(String value){filter=value;offset=0;refreshRows();}
    void refreshRows() {
        if(closed||rowsBusy)return;rowsBusy=true;lastRows=System.currentTimeMillis();
        final long g=generation,runStart=runStartedAt,runEnd=runEndedAt;final String n=network,a=account,f=filter;final int start=offset;
        io.execute(()->{List<WalletReader.Entry> items=new ArrayList<>(),feed=new ArrayList<>();String tail=null;int count=0;String problem="";
            HistoryStore.RewardTotal summary=new HistoryStore.RewardTotal();
            try{HistoryStore db=HistoryStore.get(context);items=db.rows(n,a,f,start,25);feed=db.rows(n,a,"Rewards",0,3);count=db.count(n,a,f);tail=db.cache(n,a,"cursor");summary=db.rewardsForRun(n,a,runStart,runEnd);}
            catch(Exception e){problem="Saved history is unavailable. Check free storage.";}
            final List<WalletReader.Entry> list=items,latest=feed;final String next=tail,error=problem;final int size=count;
            final HistoryStore.RewardTotal runTotal=summary;
            main.post(()->{if(!current(g))return;rowsBusy=false;if(!f.equals(filter)||start!=offset||runStart!=runStartedAt||runEnd!=runEndedAt){refreshRows();return;}rows=list;rewards=latest;total=size;cursor=next;runRewards=runTotal;runRewardsReady=error.isEmpty();if(!error.isEmpty())historyError=error;changed.run();});
        });
    }
    private void loadBalance() {
        if(closed||balancesBusy||account.isEmpty())return;balancesBusy=true;lastBalance=System.currentTimeMillis();
        final long g=generation;final String n=network,a=account;
        io.execute(()->{WalletReader.Balance loaded=null;
            for(String url:PublicRpc.endpoints(n))try{loaded=new WalletReader(new PublicRpc(url),NodeConfig.chainId(n)).balances(a,url);break;}catch(Exception e){if(Thread.currentThread().isInterrupted())break;}
            String saveError="";if(loaded!=null)try{HistoryStore.get(context).cache(n,a,"balances",loaded.json().toString());}catch(Exception e){saveError="Balance read succeeded; offline cache could not be saved.";}
            final WalletReader.Balance value=loaded;final String note=saveError;
            main.post(()->{if(!current(g))return;balancesBusy=false;if(value!=null)balance=value;balanceError=value==null?"Wallet data is temporarily unavailable. Saved amounts are shown with their last update time.":note;changed.run();});
        });
    }
    void loadHistory(boolean older) {
        if(closed||historyBusy||account.isEmpty()||(older&&"-1".equals(cursor)))return;
        historyBusy=true;lastHistory=System.currentTimeMillis();final long g=generation;final String n=network,a=account,tail=older?cursor:null;
        changed.run();
        io.execute(()->{WalletReader.Page page=null;boolean saved=false;
            for(String url:PublicRpc.endpoints(n))try{page=new WalletReader(new PublicRpc(url),NodeConfig.chainId(n)).history(a,tail);break;}catch(Exception e){if(Thread.currentThread().isInterrupted())break;}
            if(page!=null)try{HistoryStore.get(context).savePage(n,a,page,!older||tail==null);saved=true;}catch(Exception ignored){}
            final boolean ok=saved;main.post(()->{if(!current(g))return;historyBusy=false;historyError=ok?"":"Chain history is temporarily unavailable. Saved records are kept; tap Refresh to retry.";refreshRows();changed.run();});
        });
    }
    void close(){closed=true;generation++;io.shutdownNow();main.removeCallbacksAndMessages(null);}
}
