package com.koinos.teleno;

/** Opening a review always works; only the separate start action has prerequisites. */
final class ProductionSetup {
    enum Action { REVIEW, START_OBSERVER, EXPLAIN }
    static final class State {
        boolean engine, productionBuild, active, busy, observer, producer, sameNetwork;
        boolean ready, hasAccount, hasKey, keyBusy, reviewLoading, savedDownload;
        String networkLabel = "the selected network", phase = "", readiness = "";
    }
    final Action action;
    final String note;
    private ProductionSetup(Action action, String note) { this.action = action; this.note = note; }
    static ProductionSetup from(State s) {
        if (s.reviewLoading) return explain("Opening the production review. Reading your public signing key locally; no wallet approval is needed.");
        if (!s.engine) return explain("The node engine is missing. Install the full Teleno Node app as an update.");
        if (!s.productionBuild) return explain("This installed APK is observer-only. Install the full Teleno Node app, then check the version at the top of the page.");
        if (s.busy) return explain("The node is busy: " + s.phase + ". Wait for this operation to finish, then tap Review again.");
        if (s.producer) return explain("Block production is already active. See live status and the local block count at the top. Use Stop node to finish.");
        if (s.keyBusy) return explain("A signing-key operation is finishing. Tap Review again when it completes.");
        if (!s.active && s.savedDownload) return explain("Resume the unfinished chain download in step 3 before starting the node.");
        if (s.active && (!s.observer || !s.sameNetwork)) return explain("Run an observer on " + s.networkLabel + ". Stop the current node before changing networks.");
        if (!s.hasAccount) return explain("Connect your producer wallet in step 1. Your saved address is enough for later starts; a live Vault connection is not required to mine.");
        if (!s.hasKey) return explain("Tap Start node to prepare and register this device's signing key. Existing signing keys are reused.");
        return new ProductionSetup(Action.REVIEW, s.active&&!s.ready?"Start node will keep syncing and enable mining automatically when the local chain, key and VHP checks pass. "+s.readiness:"Start node reuses your saved chain and signing key, catches up, and enables mining automatically after fresh checks. Charging and a live Vault connection are optional.");
    }
    private static ProductionSetup explain(String note) { return new ProductionSetup(Action.EXPLAIN, note); }
}
