package com.koinos.teleno;

public final class ProductionSetupTests {
    static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static ProductionSetup.State ready() {
        ProductionSetup.State s = new ProductionSetup.State();
        s.engine = s.productionBuild = s.active = s.observer = s.sameNetwork = s.ready = s.hasAccount = s.hasKey = true;
        s.networkLabel = "Mainnet"; return s;
    }
    static void explains(ProductionSetup.State s, String phrase, String why) {
        ProductionSetup p = ProductionSetup.from(s);
        check(p.action == ProductionSetup.Action.EXPLAIN && p.note.contains(phrase), why);
    }
    public static void main(String[] args) {
        ProductionSetup.State s = ready();
        check(ProductionSetup.from(s).action == ProductionSetup.Action.REVIEW, "Synced node with saved account and key opens review without any wallet connection state");
        s.ready = false; s.readiness = "The latest local block is 5 minutes old.";
        check(ProductionSetup.from(s).action==ProductionSetup.Action.REVIEW&&ProductionSetup.from(s).note.contains("5 minutes old"),"syncing observer accepts reviewed startup but keeps freshness checks");
        s.readiness = ""; check(ProductionSetup.from(s).action==ProductionSetup.Action.REVIEW,"missing sample queues sync before production");
        s = ready(); s.active = s.observer = s.ready = false;
        check(ProductionSetup.from(s).action == ProductionSetup.Action.REVIEW, "Stopped configured node offers complete producer startup");
        s.savedDownload = true; explains(s, "step 3", "Pending download cannot start a conflicting observer");
        s = ready(); s.sameNetwork = false; explains(s, "Stop the current node", "Wrong active network must not start production");
        s = ready(); s.observer = false; explains(s, "Run an observer", "Non-observer state cannot bypass readiness");
        s = ready(); s.hasAccount = false; explains(s, "step 1", "Missing account points to setup");
        s = ready(); s.hasKey = false; explains(s, "prepare and register", "Missing key points to setup");
        s = ready(); s.busy = true; s.phase = "Switching to production";
        explains(s, "Switching to production", "Repeated taps during start explain current operation");
        s = ready(); s.producer = true; s.observer = false;
        explains(s, "already active", "Running producer cannot launch a second one");
        s = ready(); s.productionBuild = false; explains(s, "observer-only", "Old observer build is identifiable");
        s = ready(); s.engine = false; explains(s, "engine is missing", "Diagnostic build cannot offer producer start");
        s = ready(); s.keyBusy = true; explains(s, "signing-key operation", "Actual key operation still excludes concurrent review");
        s.reviewLoading = true; explains(s, "Reading your public signing key locally", "Duplicate review tap has immediate progress feedback");
        s = ready();
        ProductionSetup initial = ProductionSetup.from(s); s.busy = true;
        check(initial.action == ProductionSetup.Action.REVIEW && ProductionSetup.from(s).action == ProductionSetup.Action.EXPLAIN, "Conditions are re-evaluated when start is confirmed");

        long now = 10000000;
        Readiness r = new Readiness(); r.sample(100, 95, now - 1000, now - 1000, true); r.sample(101, 96, now, now, true); r.peers = 2; r.peersAt = now;
        check(r.blocker(now) == null, "Normal advancing observer is accepted");
        r.headTime = now - 60000; check(r.blocker(now) == null, "Existing freshness boundary unchanged");
        r.headTime = now - 61000; check(r.blocker(now).contains("61 seconds"), "Sync blocker reports measured local block age");
        r.headTime = now - 300000; check(r.blocker(now).contains("5 minutes"), "Longer sync age is readable");
        r.headTime = now - 7200000; check(r.blocker(now).contains("2 hours"), "Large sync age is readable");
        r.headTime = now + 31000; check(r.blocker(now).contains("automatic date and time"), "Clock mismatch has an actionable explanation");
        r.headTime = 0; check(r.blocker(now).contains("timestamp"), "Unknown timestamp is not called syncing progress");
        r.headTime = now; r.peers = -1; check(r.blocker(now).contains("first peer report"), "Missing metrics are distinct from disconnected peers");
        r.peers = 0; check(r.state(now)==Readiness.State.NO_PEERS&&r.blocker(now).contains("Reconnecting peers"), "Zero peers on a current chain show connection guidance");
        r.peers = 2; r.peersAt = now - 91000; check(r.blocker(now).contains("stale"), "Old peer data remains blocked");
        r.peersAt = now; r.correctChain = false; check(r.blocker(now) != null, "Wrong chain remains blocked");
        System.out.println("PASS: " + assertions + " production setup and actionable-readiness assertions. Android UI testing is separate.");
    }
}
