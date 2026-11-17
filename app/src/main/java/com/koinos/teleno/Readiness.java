package com.koinos.teleno;

/** Pure policy shared by status reporting and the producer-start path. */
final class Readiness {
    long firstHeight = -1, height, irreversible, headTime, sampledAt, advancedAt;
    long peersAt; int peers = -1;
    boolean correctChain;
    void sample(long nextHeight, long lib, long blockTime, long now, boolean chain) {
        if (firstHeight < 0) firstHeight = nextHeight;
        if (sampledAt != 0 && nextHeight > height) advancedAt = now;
        if (sampledAt != 0 && nextHeight < height) advancedAt = 0;
        height = nextHeight; irreversible = lib; headTime = blockTime; sampledAt = now; correctChain = chain;
    }
    String blocker(long now) {
        if (!correctChain) return "Waiting for the selected chain to be verified.";
        if (sampledAt == 0 || now < sampledAt || now - sampledAt > 20000) return "Waiting for a fresh response from this node.";
        if (headTime <= 0) return "Waiting for the observer's latest block timestamp.";
        if (headTime - now > 30000) return "The latest block is ahead of the phone's clock. Check Android's automatic date and time setting.";
        if (now - headTime > 60000) return "The latest local block is " + age(now - headTime) + " old. Production needs a block no more than 60 seconds old. Keep the observer syncing.";
        if (advancedAt == 0 || now < advancedAt || now - advancedAt > 60000) return "Waiting to see this observer validate new blocks.";
        if (peers < 0) return "Waiting for the native engine's first peer report (normally within 60 seconds).";
        if (now < peersAt || now - peersAt > 90000) return "The connected-peer report is stale. Keep the observer running for a new report.";
        if (peers == 0) return "No peers are connected. Check Wi-Fi and keep the observer running while it reconnects.";
        if (irreversible <= 0 || irreversible > height) return "Waiting for a valid irreversible block.";
        return null;
    }
    private static String age(long millis) {
        long seconds = millis / 1000;
        if (seconds < 120) return seconds + " seconds";
        if (seconds < 7200) return (seconds / 60) + " minutes";
        return (seconds / 3600) + " hours";
    }
    void reset() { firstHeight = -1; height = irreversible = headTime = sampledAt = advancedAt = peersAt = 0; peers = -1; correctChain = false; }
}
