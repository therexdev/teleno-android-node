package com.koinos.teleno;
import java.nio.file.*;
import org.json.JSONObject;
/** Optional live metadata/genesis check only. Never downloads a chain database. */
public final class SnapshotSourceCheck {
    public static void main(String[] args)throws Exception {
        SnapshotPlan plan=new SnapshotSource("mainnet").plan(null,Files.readAllBytes(Paths.get(args[0],"mainnet-genesis.json")));
        System.out.println(new JSONObject().put("network","mainnet").put("id",plan.id).put("databaseBytes",plan.bytes).put("databaseFiles",plan.database.size()).put("manifestSha256",plan.manifestHash).put("inventorySha256",plan.filesHash).put("httpsMetadataAndGenesisVerified",true).put("databaseDownloaded",false).put("androidExecution",false).toString(2));
    }
}
