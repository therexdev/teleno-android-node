package com.koinos.teleno;

/** Monotonic watchdog deadlines. Lack of rewards is never a failure signal. */
final class RecoveryPolicy {
    private long rpcSince=-1, stalledSince=-1;
    private int attempts;
    private long producerSince=-1,lastEvaluations=-1,observerSince=-1,lastHeight=-1;
    synchronized boolean rpcExpired(long now) { if(rpcSince<0)rpcSince=now;return now-rpcSince>=180000; }
    synchronized void rpcHealthy() { rpcSince=-1; }
    synchronized boolean stalled(long now,boolean ready) {
        if(ready){stalledSince=-1;return false;}
        if(stalledSince<0)stalledSince=now;
        return now-stalledSince>=300000;
    }
    synchronized long retryDelay() { return Math.min(60000L,5000L << Math.min(attempts++,4)); }
    synchronized boolean producerExpired(long now,long evaluations) {
        if(producerSince<0||evaluations>lastEvaluations){producerSince=now;lastEvaluations=evaluations;}
        return now-producerSince>=300000;
    }
    synchronized boolean observerExpired(long now,long height) {
        if(observerSince<0||height!=lastHeight){observerSince=now;lastHeight=height;}
        return now-observerSince>=600000;
    }
    synchronized void processStarted(){rpcSince=stalledSince=producerSince=observerSince=-1;lastEvaluations=lastHeight=-1;}
    synchronized void healthy() { attempts=0; }
}
