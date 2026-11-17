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
        if (!s.active && s.savedDownload) return explain("Resume or discard the unfinished chain download in step 2, then start the observer.");
        if (!s.active) return new ProductionSetup(Action.START_OBSERVER, "Start an observer on " + s.networkLabel + " first. It will use your saved chain data and key. Wait for it to catch up before starting production.");
        if (!s.observer || !s.sameNetwork) return explain("Run an observer on " + s.networkLabel + ". Stop the current node before changing networks.");
        if (!s.ready) return explain("Production cannot start yet: " + (s.readiness.isEmpty() ? "Waiting for this observer's current chain status." : s.readiness)
            + "\nKeep the observer running. This check updates automatically; you can tap Review again at any time.");
        if (!s.hasAccount) return explain("Choose your producer account in step 3. A saved account address is enough; a live KOIN Vault connection is not required to start production.");
        if (!s.hasKey) return explain("Create or import the block-signing key in step 4. Register its public key for this producer account before starting.");
        return new ProductionSetup(Action.REVIEW, "Ready to review. Connect power, then tap below. VHP and the registered key will be checked on your synced node before starting. KOIN Vault background checks do not block this review.");
    }
    private static ProductionSetup explain(String note) { return new ProductionSetup(Action.EXPLAIN, note); }
}
