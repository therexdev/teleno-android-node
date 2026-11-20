package com.koinos.teleno;

import java.io.*;
import java.nio.file.*;

public final class DataStorageTests {
    static int count;
    interface Run{void run()throws Exception;}
    static void check(boolean ok,String reason){count++;if(!ok)throw new AssertionError(reason);}
    static void rejects(Run run,String reason)throws Exception {boolean rejected=false;try{run.run();}catch(Exception e){rejected=true;}check(rejected,reason);}
    static SnapshotSetup.State state(){SnapshotSetup.State s=new SnapshotSetup.State();s.engine=s.arm64=true;s.free=50L<<30;return s;}
    public static void main(String[] args)throws Exception {
        long total=16L<<30;String warning=DataUse.warning(total,4L<<30);
        check(warning.contains("17.18 GB")&&warning.contains("16.00 GiB")&&warning.contains("17,179,869,184 bytes"),"mobile review shows full exact download size");
        check(warning.contains("4.29 GB")&&warning.contains("data allowance")&&warning.contains("carrier/roaming charges"),"resume size and charges explained");
        check(warning.contains("does not enable ongoing")&&warning.contains("Retries"),"download approval is scoped and retry usage disclosed");
        rejects(()->DataUse.warning(total,total+1),"invalid remaining size blocked");rejects(()->DataUse.warning(-1,0),"negative total blocked");
        SnapshotSetup.State state=state();state.existing=true;state.retained=state.validated=true;
        check(SnapshotSetup.from(state).action==SnapshotSetup.Action.USE_EXISTING,"an updated app uses the saved chain instead of demanding cleanup or download");
        check(SnapshotSetup.from(state).note.contains("no snapshot download or backup deletion"),"existing-chain next action explains no cleanup requirement");
        state.active=true;check(SnapshotSetup.from(state).action==SnapshotSetup.Action.USE_EXISTING,"running saved chain never redirects to snapshot setup");
        state=state();state.recoverable=true;check(SnapshotSetup.from(state).action==SnapshotSetup.Action.FIND_EXISTING,"lost selection offers read-only discovery before a new download");
        state.existing=true;state.saved=true;check(SnapshotSetup.from(state).label.contains("Resume"),"unfinished transfer remains resumable");
        state.saved=false;state.replace=true;check(SnapshotSetup.from(state).action==SnapshotSetup.Action.REVIEW,"explicit replacement still offers snapshot review");
        File root=Files.createTempDirectory("saved-chain-").toFile();
        try {
            File db=new File(root,"db");db.mkdir();check(!ChainData.usable(db),"empty folder is not a reusable database");
            Files.writeString(new File(db,"CURRENT").toPath(),"MANIFEST-000001\n");check(!ChainData.usable(db),"CURRENT without matching manifest is incomplete");
            Files.writeString(new File(db,"MANIFEST-000001").toPath(),"fixture manifest");check(ChainData.usable(db),"saved RocksDB with manifest is discoverable without changing it");
            File alias=new File(root,"database-link");Files.createSymbolicLink(alias.toPath(),db.toPath());
            check(!ChainData.usable(alias),"discovery does not treat an arbitrary symlink as a new copy");
            rejects(()->ChainData.protectActive(alias,db),"cleanup cannot remove the active database through an alias");
            rejects(()->ChainData.protectActive(db,root),"cleanup cannot remove an ancestor of the active database");
            File backup=new File(root,"backup");backup.mkdir();ChainData.protectActive(db,backup);check(true,"separate backup passes overlap protection");
            Files.delete(alias.toPath());
            SnapshotTests.Fixture fixture=new SnapshotTests.Fixture();SnapshotPlan plan=fixture.plan();File stage=new File(root,"stage");new File(stage,"db").mkdirs();
            check(SnapshotDownload.estimatedRemaining(plan,stage)==plan.bytes,"new transfer estimates the full snapshot");
            SnapshotPlan.Entry entry=plan.database.get(2);File part=new File(stage,entry.path+".download-part");
            try(RandomAccessFile f=new RandomAccessFile(part,"rw")){f.setLength(1024);}
            check(SnapshotDownload.estimatedRemaining(plan,stage)==plan.bytes-1024,"saved partial bytes reduce the estimated mobile transfer");
            SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,SnapshotTests.progress);
            check(SnapshotDownload.estimatedRemaining(plan,stage)==0,"completed download needs no further bulk transfer");
            int calls=fixture.calls;SnapshotDownload.run(plan,stage,fixture,()->Long.MAX_VALUE,SnapshotTests.progress);
            check(fixture.calls==calls,"fully saved chain verifies/resumes without redownloading its files");
            check(ChainData.usable(new File(stage,"db")),"completed transfer is a recognizable RocksDB chain");
        }finally{StorageFiles.deleteTree(root);}
        System.out.println("PASS: "+count+" mobile-data consent, saved-chain flow, resume and active-data protection assertions.");
    }
}
