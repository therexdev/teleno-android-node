package com.koinos.teleno;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local chain freshness and peer evidence are separate startup prerequisites. */
final class Readiness {
    enum State {
        VERIFYING("Checking chain identity","STATUS CHECK"),
        LOCAL_RESPONSE("Waiting for local status","STATUS CHECK"),
        TIMESTAMP("Waiting for block time","STATUS CHECK"),
        CLOCK("Check the phone clock","NEEDS ATTENTION"),
        SYNCING("Syncing recent blocks","SYNCING"),
        WAITING_BLOCK("Waiting for new blocks","STATUS CHECK"),
        PEER_REPORT("Checking peer connection","NETWORK CHECK"),
        NO_PEERS("Reconnecting peers","NETWORK CHECK"),
        FINALITY("Checking chain finality","STATUS CHECK"),
        NETWORK("Waiting for network","NETWORK CHECK"),
        READY("Validating the chain","HEALTHY");
        final String title,badge;
        State(String title,String badge){this.title=title;this.badge=badge;}
    }
    private static final Pattern HEAD=metric("head_height"),EVALUATIONS=metric("producer_pob_evaluations"),PEERS=metric("peer_count");
    long firstHeight=-1,height,irreversible,headTime,sampledAt,advancedAt;
    long peersAt,gateAt,nativeProgressAt;int peers=-1,gate=-1;
    private long nativeHeight=-1,nativeEvaluations=-1;
    boolean correctChain;
    private static Pattern metric(String key){return Pattern.compile("(?:^|\\s)"+key+"=([0-9]+)(?=\\s|$)");}
    private static long number(Pattern pattern,String body){
        Matcher m=pattern.matcher(body);if(!m.find())return -1;
        try{long value=Long.parseLong(m.group(1));return m.find()?-1:value;}catch(NumberFormatException e){return -1;}
    }
    void acceptNative(String line,long wallNow,long elapsedNow){
        String body=ProductionStatus.infoBody(line);if(body==null||wallNow<=0||elapsedNow<0)return;
        if(body.equals("[block_producer] Gossip production gate opened")){gate=1;gateAt=wallNow;return;}
        if(body.equals("[block_producer] Gossip production gate closed")){gate=0;gateAt=wallNow;return;}
        // A deliberately disabled production gate is not evidence of a peer.
        if(body.equals("[block_producer] Gossip production gate: disabled")){gate=-1;gateAt=0;return;}
        if(!body.startsWith("[metrics] "))return;
        long h=number(HEAD,body),e=number(EVALUATIONS,body),p=number(PEERS,body);
        if(h<0||e<0||p<0||p>Integer.MAX_VALUE)return;
        peers=(int)p;peersAt=wallNow;
        if(nativeHeight>=0&&nativeEvaluations>=0){
            if(h<nativeHeight||e<nativeEvaluations)nativeProgressAt=0;
            else if(h>nativeHeight||e>nativeEvaluations)nativeProgressAt=elapsedNow;
        }
        nativeHeight=h;nativeEvaluations=e;
    }
    void sample(long nextHeight,long lib,long blockTime,long now,boolean chain){
        if(firstHeight<0)firstHeight=nextHeight;
        if(sampledAt!=0&&nextHeight>height)advancedAt=now;
        if(sampledAt!=0&&nextHeight<height)advancedAt=0;
        height=nextHeight;irreversible=lib;headTime=blockTime;sampledAt=now;correctChain=chain;
    }
    private static boolean recent(long at,long now,long window){return at>0&&now>=at&&now-at<=window;}
    // Prefer a newer native connection event to an older startup zero. Never
    // invent an exact count, accept a disabled gate, or ignore a newer zero.
    boolean peerCountPending(long now){return gate==1&&gateAt>peersAt&&recent(gateAt,now,90000);}
    State chainState(long now){
        if(!correctChain)return State.VERIFYING;
        if(!recent(sampledAt,now,20000))return State.LOCAL_RESPONSE;
        if(headTime<=0)return State.TIMESTAMP;
        if(headTime-now>30000)return State.CLOCK;
        if(now-headTime>60000)return recent(advancedAt,now,60000)?State.SYNCING:State.WAITING_BLOCK;
        if(!recent(advancedAt,now,60000))return State.WAITING_BLOCK;
        if(irreversible<=0||irreversible>height)return State.FINALITY;
        return State.READY;
    }
    State state(long now){
        State chain=chainState(now);if(chain!=State.READY)return chain;
        if(peerCountPending(now))return State.READY;
        if(peers<0||!recent(peersAt,now,90000))return State.PEER_REPORT;
        return peers==0?State.NO_PEERS:State.READY;
    }
    String blocker(long now){
        switch(state(now)){
            case VERIFYING:return "Waiting for the selected chain to be verified.";
            case LOCAL_RESPONSE:return "Waiting for a fresh response from this node.";
            case TIMESTAMP:return "Waiting for the observer's latest block timestamp.";
            case CLOCK:return "The latest block is ahead of the phone's clock. Check Android's automatic date and time setting.";
            case SYNCING:case WAITING_BLOCK:
                if(headTime>0&&now-headTime>60000)return "The latest local block is "+age(now-headTime)+" old. Production needs a block no more than 60 seconds old. Waiting for current blocks.";
                return "Waiting to see this observer validate new blocks.";
            case PEER_REPORT:return peers<0?"Waiting for the native engine's first peer report (normally within 60 seconds).":"The connected-peer report is stale. Waiting for an updated connection report.";
            case NO_PEERS:return "The local chain is current, but the latest peer report has no connections. Reconnecting peers; this does not mean the checkpoint needs restoring.";
            case FINALITY:return "Waiting for a valid irreversible block.";
            default:return null;
        }
    }
    private static String age(long millis){long seconds=millis/1000;return seconds<120?seconds+" seconds":seconds<7200?(seconds/60)+" minutes":(seconds/3600)+" hours";}
    void reset(){firstHeight=-1;height=irreversible=headTime=sampledAt=advancedAt=peersAt=gateAt=nativeProgressAt=0;nativeHeight=nativeEvaluations=-1;peers=gate=-1;correctChain=false;}
}
