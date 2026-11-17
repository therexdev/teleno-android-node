package com.koinos.teleno;

/** Regression scenarios: a tappable recovery action must never become an unsafe restore. */
public final class SnapshotSetupTests {
    private static int count;
    private static void check(boolean condition, String message) {
        count++; if (!condition) throw new AssertionError(message);
    }
    private static SnapshotSetup.State ready() {
        SnapshotSetup.State s=new SnapshotSetup.State();
        s.engine=true;s.arm64=true;s.free=100L*1024*1024*1024;return s;
    }
    public static void main(String[] args) {
        SnapshotSetup.State s=ready();
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.REVIEW,"fresh installation offers snapshot review before starting a node");
        s.active=true;
        check(SnapshotSetup.from(s).enabled()&&SnapshotSetup.from(s).action==SnapshotSetup.Action.STOP_NODE,"running observer offers Stop, never restore");
        s.busy=true;s.phase="Stopping node";
        check(!SnapshotSetup.from(s).enabled()&&SnapshotSetup.from(s).note.contains("Stopping node"),"shutdown cannot race snapshot setup and explains the wait");
        s.active=false;s.busy=false;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.REVIEW,"clean shutdown unlocks review");
        s.retained=true;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.START_OBSERVER,"unvalidated moved data requires observer validation");
        s.active=true;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.EXPLAIN,"running unvalidated observer is allowed to advance instead of offering cleanup");
        s.validated=true;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.STOP_NODE,"validated running data must still stop before cleanup");
        s.active=false;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.CLEAN_PREVIOUS,"validated stopped data offers explicit old-copy cleanup, not restore");
        s.saved=true;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.REVIEW&&SnapshotSetup.from(s).label.contains("Resume"),"saved attempt remains resumable after a completed storage switch");
        s.free=-1;s.storageProblem="Selected SD card is missing";
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.EXPLAIN&&SnapshotSetup.from(s).note.contains(s.storageProblem),"resume cannot bypass a missing card and explains the specific problem");
        s=ready();s.engine=false;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.EXPLAIN,"diagnostic APK cannot restore a chain");
        s=ready();s.arm64=false;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.EXPLAIN,"unsupported ABI cannot restore a chain");
        s=ready();s.checking=true;
        check(!SnapshotSetup.from(s).enabled(),"repeated taps cannot start parallel reviews");
        s=ready();s.keyBusy=true;
        check(!SnapshotSetup.from(s).enabled(),"key work remains exclusive");
        s=ready();s.saved=true;s.busy=true;s.downloading=true;
        check(!SnapshotSetup.from(s).enabled()&&SnapshotSetup.from(s).note.contains("Cancel operation"),"active download explains progress and pause instead of offering a second attempt");
        s.busy=false;s.downloading=false;
        check(SnapshotSetup.from(s).action==SnapshotSetup.Action.REVIEW,"paused download returns to resume review");
        System.out.println("PASS: "+count+" snapshot setup action and maintenance-exclusion assertions.");
    }
}
