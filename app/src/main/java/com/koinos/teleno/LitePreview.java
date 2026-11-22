package com.koinos.teleno;

/** The separate app foundation must never fall back to a full-chain download. */
final class LitePreview {
    static final String PACKAGE="com.koinos.teleno.lite";
    static final String STATUS=LiteRuntime.ENABLED?"Experimental Lite · compact local miner":"Lite preview · compact mining is not available yet";
    static final String EXPLANATION=LiteRuntime.ENABLED?"Connect KOIN Vault, review VHP and signing permission, restore the bundled compact checkpoint, then Start node. This experimental build has passed isolated native tests; physical Android and Mainnet PoB tests remain outstanding.":"Teleno Node Lite is a separate app for the compact-storage miner. You can connect KOIN Vault and view your wallet. Mining and chain downloads will become available after the compact engine and snapshot are validated. This preview does not download the standard node's large database. Your standard Teleno Node app remains separate.";
    static boolean ready(){return LiteRuntime.ENABLED;}
    static boolean blocks(String action) {
        return action==null||NodeService.RECOVER.equals(action)||NodeService.OBSERVER.equals(action)
            ||NodeService.PRODUCER.equals(action)||NodeService.DOWNLOAD_CHAIN.equals(action)
            ||NodeService.MOVE_STORAGE.equals(action)||NodeService.CLEAN_STORAGE.equals(action)
            ||NodeService.DISCARD_DOWNLOAD.equals(action)||NodeService.RECONNECT_STORAGE.equals(action);
    }
    static void requireCompactRuntime() {
        throw new IllegalStateException(EXPLANATION);
    }
}
