package com.koinos.teleno;

/** Replays peer/chain timing from the Lite report without a wallet or network. */
public final class SyncStatusTests {
    static int assertions;
    static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    static String metrics(long h,long checks,int peers){return "INFO [metrics] head_height="+h+" producer_pob_evaluations="+checks+" peer_count="+peers;}
    static Readiness fresh(long t){Readiness r=new Readiness();r.sample(41293924,41293864,t-5000,t-2000,true);r.sample(41293925,41293865,t-4000,t-1000,true);return r;}
    public static void main(String[] args){
        long t=1000000;Readiness r=fresh(t);
        r.acceptNative(metrics(41293920,0,0),t-33000,1000);
        check(r.chainState(t)==Readiness.State.READY,"Reported four-second block is current independently of peers");
        check(r.state(t)==Readiness.State.NO_PEERS,"Zero-peer report is a peer interruption");
        check(r.state(t).title.equals("Reconnecting peers")&&!r.state(t).badge.equals("SYNCING"),"Fresh chain is never labelled as catching up because of peers");
        r.acceptNative("INFO [block_producer] Gossip production gate opened",t-10000,24000);
        check(r.peerCountPending(t)&&r.peers==0,"Newer live connection evidence does not invent an exact count");
        check(r.blocker(t)==null,"Startup zero cannot override a newer native connection event");
        r.acceptNative(metrics(41293930,50,3),t,34000);
        check(!r.peerCountPending(t)&&r.peers==3,"A new exact peer count replaces pending evidence");
        check(r.nativeProgressAt==34000,"Increasing native height or checks records monotonic progress");
        r.acceptNative(metrics(41293930,50,0),t+1,34001);
        check(r.state(t+1)==Readiness.State.NO_PEERS,"A newer zero is never hidden by an old open gate");
        r.acceptNative("INFO [block_producer] Gossip production gate opened",t+2,34002);
        r.acceptNative("INFO [block_producer] Gossip production gate closed",t+3,34003);
        check(!r.peerCountPending(t+3)&&r.state(t+3)==Readiness.State.NO_PEERS,"A closed gate immediately invalidates the hint");
        r.acceptNative("INFO [block_producer] Gossip production gate: disabled",t+4,34004);
        check(!r.peerCountPending(t+4),"Disabling the native gate does not prove any connection");
        r.acceptNative("INFO [block_producer] Gossip production gate opened",t+5,34005);
        r.sample(41293940,41293880,t+100000,t+100001,true);
        check(!r.peerCountPending(t+100001)&&r.state(t+100001)==Readiness.State.PEER_REPORT,"Connection events expire; missing metrics do not grant indefinite readiness");
        r=fresh(t);r.acceptNative(metrics(10,20,2),t,1000);
        Readiness formatted=fresh(t);
        formatted.acceptNative("\u001b[32m2026-11-22 12:42:00.123456 (main) [main.cpp:2401] <info>: [metrics] head_height=41293925 producer_pob_evaluations=0 peer_count=0\u001b[0m",t-1000,1000);
        check(formatted.peers==0&&formatted.peersAt==t-1000,"The actual native timestamp/header and ANSI colors are accepted");
        formatted.acceptNative("2026-11-22 12:42:01.123456 (main) [main.cpp:2032] <info>: [block_producer] Gossip production gate opened",t,2000);
        check(formatted.peerCountPending(t)&&formatted.blocker(t)==null,"A real native gate event supersedes an earlier numeric startup report");
        long before=r.peersAt;
        for(String bad:new String[]{"WARNING [metrics] head_height=99 producer_pob_evaluations=99 peer_count=8", "INFO remote says [metrics] head_height=99 producer_pob_evaluations=99 peer_count=8",metrics(11,21,3)+" peer_count=4",metrics(11,21,3).replace("peer_count=3","peer_count=99999999999999999999"),"INFO [metrics] peer_count=8"})r.acceptNative(bad,t+5000,6000);
        check(r.peers==2&&r.peersAt==before&&r.nativeProgressAt==0,"Malformed, duplicate and embedded counts do not partially update state");
        r.acceptNative(metrics(10,20,2),t+1000,2000);check(r.nativeProgressAt==0,"Repeating a heartbeat without progress cannot preserve a stuck process");
        r.acceptNative(metrics(11,21,2),t+2000,3000);check(r.nativeProgressAt==3000,"New measured work is liveness evidence");
        r.acceptNative(metrics(1,1,2),t+3000,4000);check(r.nativeProgressAt==0,"Counter reset invalidates previous progress evidence");
        r=fresh(t);r.peers=2;r.peersAt=t;
        r.correctChain=false;check(r.blocker(t)!=null,"Peer evidence never bypasses chain identity");r.correctChain=true;
        r.headTime=t+31000;check(r.state(t)==Readiness.State.CLOCK,"Future timestamps remain blocked");
        r.headTime=t-90000;check(r.state(t)==Readiness.State.SYNCING,"Observed historical blocks can be labelled syncing");
        r.sampledAt=t+100000;r.headTime=t-90000;check(r.state(t+100000)==Readiness.State.WAITING_BLOCK,"A stale stationary head is waiting, not proof of active catch-up");
        r=fresh(t);r.peers=2;r.peersAt=t;r.irreversible=r.height+1;check(r.state(t)==Readiness.State.FINALITY,"Invalid finality remains blocked");
        r=fresh(t);r.peers=2;r.peersAt=t;check(r.state(t+21000)==Readiness.State.LOCAL_RESPONSE,"Stale local samples remain blocked");
        r.reset();check(r.nativeProgressAt==0&&r.gate==-1&&!r.peerCountPending(t),"Restart clears connection and progress evidence");
        RecoveryPolicy policy=new RecoveryPolicy();check(!policy.rpcExpired(1000,0),"RPC watchdog starts normally");
        check(!policy.rpcExpired(180000,179000),"Fresh native progress prevents an unnecessary RPC restart");
        check(!policy.rpcExpired(359999,179000)&&policy.rpcExpired(360000,179000),"An unchanged old counter cannot postpone recovery forever");
        policy.processStarted();check(!policy.rpcExpired(1000,2000)&&policy.rpcExpired(181000,2000),"Future or wrong-clock evidence is rejected");
        policy.processStarted();check(!policy.rpcExpired(1000,0)&&policy.rpcExpired(181000,0),"No telemetry still uses the original timeout");
        System.out.println("PASS: "+assertions+" sync-state, native peer evidence, safety guard and recovery assertions.");
    }
}
