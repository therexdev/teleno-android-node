package com.koinos.teleno;

public final class ProductionTests {
    static int assertions;
    static String metrics(long evaluations,long errors){return "2026-11-15 22:00:00.000000 (main.Koinos) [main.cpp:2400] <info>: [metrics] producer_running=1 producer_pob_evaluations="+evaluations+" producer_candidates_prepared=20 producer_errors="+errors+" producer_head_retries=5 producer_mempool_retries=2 peer_count=4";}
    interface Action { void run() throws Exception; }
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void rejects(Action action, String message) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        check(!ProductionReview.buildAllowsProduction(false, true, true), "Missing engine cannot produce");
        check(!ProductionReview.buildAllowsProduction(true, false, false), "Ordinary observer build remains locked");
        check(ProductionReview.buildAllowsProduction(true, false, true), "Explicit experimental build works without claiming qualification");
        check(ProductionReview.buildAllowsProduction(true, true, false), "Qualified build can produce");
        String account = Base58.encodeChecked(new byte[21]);
        byte[] other = new byte[21]; other[1] = 1; String otherAccount = Base58.encodeChecked(other);
        byte[] key = CoreTests.unhex("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
        rejects(() -> new ProductionReview(false, "mainnet", account, key), "Service request without review rejected");
        rejects(() -> new ProductionReview(true, "invalid", account, key), "Unsupported network rejected");
        rejects(() -> new ProductionReview(true, "mainnet", null, key), "Missing account rejected");
        rejects(() -> new ProductionReview(true, "mainnet", "invalid", key), "Invalid address rejected");
        rejects(() -> new ProductionReview(true, "mainnet", account, null), "Missing key rejected");
        rejects(() -> new ProductionReview(true, "mainnet", account, new byte[65]), "Uncompressed key rejected");
        byte[] invalid = key.clone(); invalid[0] = 4;
        rejects(() -> new ProductionReview(true, "mainnet", account, invalid), "Invalid compressed key prefix rejected");
        ProductionReview review = new ProductionReview(true, "mainnet", account, key);
        review.requireCurrent("mainnet", account, key.clone()); check(true, "Unchanged reviewed context accepted");
        new ProductionReview(true, "harbinger", account, key).requireCurrent("harbinger", account, key); check(true, "Testnet review accepted");
        rejects(() -> review.requireCurrent("harbinger", account, key), "Network change after dialog rejected");
        rejects(() -> review.requireCurrent("mainnet", otherAccount, key), "Account change during preflight rejected");
        rejects(() -> review.requireCurrent("mainnet", account, null), "Key removed after dialog rejected");
        byte[] original = key.clone(); key[1] ^= 1;
        review.requireCurrent("mainnet", account, original); check(true, "Reviewed key is defensively copied");
        rejects(() -> review.requireCurrent("mainnet", account, key), "Key change after review rejected");

        ProductionStatus status = new ProductionStatus();
        check(!status.running() && status.blocks() == 0, "Launching process alone does not confirm production");
        status.accept("INFO [block_producer] Private key: /proc/self/fd/0");
        check(!status.running(), "Key loading alone does not confirm production");
        status.accept("INFO [block_producer] Production loop started");
        check(status.running(), "Native loop start confirms activity");
        check(status.accept("INFO [block_producer] Produced block #1 at height 101"), "Native produced event accepted");
        check(status.blocks() == 1 && status.lastHeight() == 101, "First local block counted");
        check(!status.accept("INFO [block_producer] Produced block - Height: 101, ID: abc (0 transactions)"), "Second native log for same block is not double counted");
        status.accept("INFO [block_producer] Stopped (produced 1 blocks)");
        check(!status.running() && status.blocks() == 1, "Gossip pause preserves block count");
        status.accept("INFO [metrics] head_height=101 producer_running=1 peer_count=3");
        check(status.running(), "Native numeric metric confirms running");
        status.accept("INFO [metrics] producer_running=false peer_count=3");
        check(!status.running(), "Native metric reports paused loop");
        status.accept("untrusted producer_running=true");
        check(!status.running(), "Unscoped producer string ignored");
        status.accept("INFO [metrics] producer_running=10 peer_count=3");
        check(!status.running(), "Malformed boolean ignored");
        status.accept("INFO [metrics] producer_running=true");
        check(status.running(), "Boolean native metric accepted");
        status.accept("INFO [block_producer] Produced block #1 at height 102");
        check(status.blocks() == 2 && status.lastHeight() == 102, "Loop restart counter does not lose prior blocks");
        check(!status.accept("INFO [block_producer] Produced block #3 at height 99999999999999999999999"), "Oversized height ignored");
        check(!status.accept("INFO [block_producer] Produced block #3 at height 0"), "Zero height ignored");
        check(!status.accept("INFO [block_producer] Produced block #3 at height 103 untrusted suffix"), "Unrecognized block line ignored");
        status.reset(); check(!status.running() && status.blocks() == 0 && status.lastHeight() == 0, "Stop or new run clears production telemetry");
        check(status.snapshot().health(1000)==ProductionStatus.Health.WAITING,"No telemetry is unknown, not healthy");
        status.accept("INFO [block_producer] Production loop started",500);
        check(!status.snapshot().advancing(1000),"An open loop alone cannot turn the health indicator green");
        status.accept(metrics(100,0),1000);
        check(status.snapshot().health(1000)==ProductionStatus.Health.BASELINE,"First counter sample cannot prove progress rate");
        status.accept(metrics(6000,0),61000);
        ProductionStatus.Snapshot healthy=status.snapshot();
        check(healthy.advancing(62000)&&healthy.delta==5900,"Completed block checks establish observed activity");
        check(healthy.report(62000).contains("98.3"),"Rate is based on the measured interval");
        check(healthy.errors==0&&healthy.headRetries==5,"Zero errors and normal head retries are distinct");
        status.accept("INFO [block_producer] Gossip production gate closed",62000);
        check(status.snapshot().health(62000)==ProductionStatus.Health.PAUSED,"A closed gossip gate overrides an advancing old sample");
        status.accept("INFO [block_producer] Gossip production gate opened",63000);
        check(status.snapshot().advancing(63000),"Opening the gate exposes still-fresh measured activity");
        status.accept(metrics(6000,0),121000);
        check(status.snapshot().health(121000)==ProductionStatus.Health.NO_PROGRESS,"A running loop with unchanged checks is not advertised as producing");
        check(healthy.delta==5900&&healthy.metricsAt==61000,"Published snapshots are immutable");
        status.accept(metrics(6100,2),181000);
        check(status.snapshot().health(181000)==ProductionStatus.Health.WARNING&&status.snapshot().errorDelta==2,"New native errors are visible even when the loop stays open");
        status.accept(metrics(12000,2),241000);
        check(status.snapshot().advancing(241000),"An unchanged historical error count does not create an ongoing failure");
        check(status.snapshot().health(332000)==ProductionStatus.Health.STALE,"Stale counters never confirm current production");
        status.accept(metrics(20000,2),400000);
        check(status.snapshot().health(400000)==ProductionStatus.Health.BASELINE,"A long telemetry gap requires a new measurement interval");
        status.accept(metrics(21000,2).replace("producer_errors=2","producer_errors=999999999999999999999"),460000);
        check(status.snapshot().metricsAt==400000,"Overflowing counters cannot refresh telemetry");
        status.accept(metrics(21000,2)+" producer_errors=2",460000);
        check(status.snapshot().metricsAt==400000,"Duplicate counters are rejected");
        status.accept(metrics(21000,2).replace("producer_running=1","producer_running=10"),460000);
        check(status.snapshot().metricsAt==400000,"Malformed running state cannot freshen counters");
        status.accept("INFO Peer sent "+metrics(21000,2),460000);
        check(status.snapshot().metricsAt==400000,"Embedded metric text is not a native metric header");
        status.accept("2026-11-15 22:00:00.000000 (main.Koinos) [main.cpp:1916] <warning>: [block_producer] invalid signature secret-fixture-do-not-show",461000);
        check(status.snapshot().health(461000)==ProductionStatus.Health.WARNING,"Producer warnings surface before the next minute's counters");
        check(status.snapshot().warning.contains("signing"),"Recognized signature failures get a fixed explanation");
        check(!status.snapshot().report(461000).contains("secret-fixture-do-not-show"),"Reports never echo raw warning bodies");
        status.reset();status.accept(metrics(20,0),1000);status.accept(metrics(30,0),61000);
        status.accept("INFO [block_producer] Gossip production gate: disabled",62000);
        check(status.snapshot().gate==1,"Disabled gossip gating is reported as open");
        status.accept(metrics(1,0),121000);
        check(status.snapshot().health(121000)==ProductionStatus.Health.BASELINE,"Counter reset establishes a new baseline instead of negative progress");
        status.reset();check(status.snapshot().metricsAt==0&&status.snapshot().warning.isEmpty(),"A new run clears old counters and warning metadata");
        String[][] reasons={
            {"could not burn vhp","VHP_BURN_REJECTED"},
            {"configured producer has no effective VHP balance","VHP_BALANCE_ZERO"},
            {"unable to parse VHP balance","VHP_BALANCE_DECODE"},
            {"unable to parse VHP decimals","VHP_DECIMALS_DECODE"},
            {"invalid VHP decimals from chain","VHP_DECIMALS_INVALID"},
            {"unable to parse VHP symbol","VHP_SYMBOL_DECODE"},
            {"unable to parse contract address for vhp","VHP_CONTRACT_LOOKUP"},
            {"failed to retrieve token balance","TOKEN_BALANCE_READ_FAILED"},
            {"signer address has no public key record","PRODUCER_KEY_MISSING"},
            {"public key not yet active","PRODUCER_KEY_NOT_ACTIVE"},
            {"proof failed vrf validation","PRODUCER_VRF_REJECTED"},
            {"provided hash is not sufficient","PRODUCER_PROOF_REJECTED"},
            {"unable to parse PoB consensus parameters","POB_PARAMETERS_DECODE"},
            {"unable to parse PoB metadata","POB_METADATA_DECODE"},
            {"invalid PoB consensus timing from chain","POB_TIMING_INVALID"},
            {"PoB difficulty is zero","POB_DIFFICULTY_ZERO"}
        };
        long now=1000;
        for(String[] reason:reasons) {
            status.accept("WARNING [block_producer] "+reason[0]+" secret-fixture-do-not-show",now++);
            check(status.snapshot().warningCode.equals(reason[1]),"Exact source error is distinguished: "+reason[1]);
        }
        String bounded=status.snapshot().report(now);
        check(bounded.contains("Recent warning groups (newest first; up to 8)"),"Report contains bounded history");
        check(!bounded.contains("VHP_BURN_REJECTED")&&bounded.contains("PRODUCER_KEY_MISSING"),"Old warning groups are evicted after eight distinct groups");
        check(!bounded.contains("secret-fixture-do-not-show"),"History never retains raw warning text");
        status.reset();status.accept("WARNING [block_producer] could not burn vhp",1000);
        ProductionStatus.Snapshot firstWarning=status.snapshot();
        status.accept("WARNING [block_producer] could not burn vhp",2000);
        status.accept("WARNING [block_producer] could not burn vhp",3000);
        check(status.snapshot().report(4000).contains("VHP_BURN_REJECTED · 3 occurrence(s) · last 1s ago"),"Consecutive identical errors preserve count and latest age");
        check(firstWarning.report(4000).contains("VHP_BURN_REJECTED · 1 occurrence(s) · last 3s ago"),"Published warning history is immutable");
        status.accept("WARNING [block_producer] unable to parse VHP balance",5000);
        check(status.snapshot().report(5000).contains("VHP_BURN_REJECTED · 3"),"A later read error does not hide prior burn rejections");
        check(status.snapshot().warning.contains("not proof of an empty balance"),"Decode failures never claim insufficient VHP");
        status.accept("WARNING [block_producer] unusual vhp problem secret-fixture-do-not-show",6000);
        check(status.snapshot().warningCode.equals("VHP_OTHER")&&!status.snapshot().warning.contains("eligibility problem"),"Unclassified VHP errors remain explicitly uncertain");
        status.accept("INFO [block_producer] could not burn vhp",7000);
        check(status.snapshot().warningCode.equals("VHP_OTHER")&&status.snapshot().warningAt==6000,"Info text cannot create a warning");
        status.reset();check(status.snapshot().warningCode.equals("NONE")&&!status.snapshot().report(8000).contains("Recent warning groups"),"New run clears reason codes and bounded history");
        System.out.println("PASS: " + assertions + " production review and native-status assertions. Actual Android production is not tested here.");
    }
}
