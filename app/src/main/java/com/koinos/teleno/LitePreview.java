package com.koinos.teleno;

/** The separate app foundation must never fall back to a full-chain download. */
final class LitePreview {
    static final String PACKAGE="com.koinos.teleno.lite";
    static final String STATUS="Lite preview · compact mining is not available yet";
    static final String EXPLANATION="Teleno Node Lite is a separate app for the compact-storage miner. You can connect KOIN Vault and view your wallet. Mining and chain downloads will become available after the compact engine and snapshot are validated. This preview does not download the standard node's large database. Your standard Teleno Node app remains separate.";
    static boolean ready(){return false;}
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
