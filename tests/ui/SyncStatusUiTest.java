package com.koinos.teleno;

import android.app.Activity;
import android.os.SystemClock;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowSystemClock;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SyncStatusUiTest {
    org.robolectric.android.controller.ActivityController<Activity> controller;
    Activity activity;DashboardView view;DashboardController data;NodeService.ViewState node;
    final String account="1111111111111111111114oLvT2";
    @Before public void open(){
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2));
        controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();
        view=new DashboardView(activity,()->{},()->{});activity.setContentView(view);data=new DashboardController(activity,()->{});
        node=new NodeService.ViewState();node.active=true;node.mode=NodeService.PRODUCER;node.network="mainnet";node.producerAccount=account;
        node.height=41293925;node.lib=41293865;node.headTime=System.currentTimeMillis()-4000;node.sampleAt=System.currentTimeMillis();node.hasSample=true;node.peers=0;node.readinessState=Readiness.State.NO_PEERS;
    }
    @After public void close(){view.foreground(false);controller.pause().stop().destroy();}
    void bind(){view.bind(node,data,true,true,"mainnet",account,8L<<30);}
    String metrics(long e){return "INFO [metrics] producer_running=true producer_pob_evaluations="+e+" producer_candidates_prepared=20 producer_errors=0 producer_head_retries=0 producer_mempool_retries=0 peer_count=2";}
    @Test public void currentChainWithZeroPeersShowsNetworkState(){
        bind();assertEquals("Reconnecting peers",view.phase.getText().toString());assertTrue(view.badge.getText().toString().contains("NETWORK CHECK"));
        assertEquals("0",view.peers.getText().toString());assertFalse(view.phase.getText().toString().toLowerCase().contains("catching up"));
    }
    @Test public void peerTransitionsDoNotPretendChainNeedsRestoring(){
        bind();node.ready=true;node.readinessState=Readiness.State.READY;node.peerCountPending=true;
        ProductionStatus production=new ProductionStatus();long now=SystemClock.elapsedRealtime();production.accept("INFO [block_producer] Production loop started",now-60001);production.accept(metrics(0),now);
        node.production=production.snapshot();bind();assertEquals("Live",view.peers.getText().toString());assertEquals("Measuring production activity",view.phase.getText().toString());assertFalse(view.badge.getText().toString().contains("HEALTHY"));
        production.reset();production.accept(metrics(1),now-60000);production.accept(metrics(501),now);node.production=production.snapshot();node.peers=2;node.peerCountPending=false;
        bind();assertEquals("Checking block opportunities",view.phase.getText().toString());assertTrue(view.badge.getText().toString().contains("HEALTHY"));
        node.ready=false;node.peers=0;node.readinessState=Readiness.State.NO_PEERS;bind();assertEquals("Reconnecting peers",view.phase.getText().toString());
    }
    @Test public void rpcDelayIsStatusCheckNotCatchup(){
        node.readinessState=Readiness.State.LOCAL_RESPONSE;bind();assertEquals("Waiting for local status",view.phase.getText().toString());assertFalse(view.badge.getText().toString().contains("SYNCING"));
    }
    @Test public void historicalBlocksAndMissingTimeHaveDifferentLabels(){
        node.headTime=System.currentTimeMillis()-600000;node.readinessState=Readiness.State.SYNCING;bind();assertEquals("Syncing recent blocks",view.phase.getText().toString());assertTrue(view.badge.getText().toString().contains("SYNCING"));
        node.headTime=0;node.readinessState=Readiness.State.TIMESTAMP;bind();assertEquals("Waiting for block time",view.phase.getText().toString());assertFalse(view.badge.getText().toString().contains("SYNCING"));
    }
}
