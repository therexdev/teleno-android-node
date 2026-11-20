package com.koinos.teleno;

/** Explains the next safe setup action without starting maintenance itself. */
final class SnapshotSetup {
    enum Action { REVIEW, STOP_NODE, START_OBSERVER, CLEAN_PREVIOUS, USE_EXISTING, FIND_EXISTING, EXPLAIN, WAIT }
    static final class State {
        boolean engine, arm64, active, busy, checking, keyBusy, saved, retained, validated, downloading, existing, recoverable, replace;
        long free;
        String storageProblem = "", phase = "";
    }
    final Action action;
    final String label, note;
    private SnapshotSetup(Action action, String label, String note) {
        this.action = action; this.label = label; this.note = note;
    }
    boolean enabled() { return action != Action.WAIT; }
    static SnapshotSetup from(State s) {
        if (s.checking) return new SnapshotSetup(Action.WAIT, "Checking snapshot…",
            "Checking Foundation snapshot metadata and network identity. Please wait.");
        if (s.busy) return new SnapshotSetup(Action.WAIT, s.downloading ? "Chain download in progress…" : "Wait for the current operation…",
            s.downloading ? "Download, verification and restore progress appear below. Use Cancel operation above to pause."
                : "The node is busy: " + s.phase + ". Wait for it to finish before downloading a snapshot.");
        if (s.keyBusy) return new SnapshotSetup(Action.WAIT, "Finishing signing-key setup…",
            "Wait for signing-key setup to finish, then download the chain snapshot.");
        if (!s.engine) return new SnapshotSetup(Action.EXPLAIN, "Node engine unavailable…",
            "This installation does not contain a usable node engine. Install the complete observer APK to download and run the chain.");
        if (!s.arm64) return new SnapshotSetup(Action.EXPLAIN, "Check device compatibility…",
            "This build requires a 64-bit ARM Android installation.");
        if(!s.saved&&!s.replace&&s.existing)return new SnapshotSetup(Action.USE_EXISTING,
            s.active?"Existing chain is in use":"Use existing chain · start observer",
            "Your chain data is already downloaded. Start the observer to catch up; no snapshot download or backup deletion is needed. Backup cleanup in step 1 is optional.");
        if(!s.saved&&!s.replace&&s.recoverable)return new SnapshotSetup(Action.FIND_EXISTING,"Find existing chain data…",
            "Chain files may still be on this device. Reconnect the existing database before considering another large download. No files will be deleted by the search.");
        if (s.free < 0) return new SnapshotSetup(Action.EXPLAIN, "Check chain storage…",
            "The selected chain storage is unavailable. " + (s.storageProblem.isEmpty()
                ? "Check the storage selection in step 1 and keep the selected SD card inserted." : s.storageProblem));
        if (s.retained && !s.saved && !s.validated) return new SnapshotSetup(s.active ? Action.EXPLAIN : Action.START_OBSERVER,
            "Validate moved database…", "A previous database copy is still protected. "
                + (s.active ? "Let the current observer advance, then stop it and remove the previous copy in step 1."
                    : "Start the observer on the selected storage and let it advance. Then stop it and remove the previous copy in step 1 before downloading a snapshot."));
        if (s.active) return new SnapshotSetup(Action.STOP_NODE, "Stop node to download snapshot…",
            "The node is running. Stop it before downloading and restoring a snapshot. After the status is Stopped, return here to continue.");
        if (s.retained && !s.saved) return new SnapshotSetup(Action.CLEAN_PREVIOUS, "Remove previous copy to continue…",
            "The moved database has been validated. Remove its retained previous copy before downloading another snapshot. The current chain and signing keys are kept.");
        return new SnapshotSetup(Action.REVIEW, s.saved ? "Resume chain download…" : "Download chain snapshot…",
            s.saved ? "Saved download available. Resume to reuse verified files."
                : "Ready to review the latest snapshot. Download size and required free space are shown before you start.");
    }
}
