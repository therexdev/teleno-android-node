package com.koinos.teleno;

import java.security.MessageDigest;

/** A single production start is bound to the network, account and public key reviewed. */
final class ProductionReview {
    final String network, account;
    private final byte[] publicKey;

    static boolean buildAllowsProduction(boolean engine, boolean qualified, boolean experimental) {
        return engine && (qualified || experimental);
    }

    ProductionReview(boolean reviewed, String network, String account, byte[] publicKey) throws Exception {
        if (!reviewed) throw new IllegalArgumentException("Review block production in step 4 before starting.");
        if (!"mainnet".equals(network) && !"harbinger".equals(network)) throw new IllegalArgumentException("Choose a supported network.");
        if (account == null) throw new IllegalArgumentException("Choose a producer account in step 1.");
        Base58.address(account);
        if (publicKey == null || publicKey.length != 33 || (publicKey[0] != 2 && publicKey[0] != 3))
            throw new IllegalArgumentException("A compressed public signing key is required.");
        this.network = network; this.account = account; this.publicKey = publicKey.clone();
    }

    void requireCurrent(String network, String account, byte[] publicKey) {
        if (!this.network.equals(network) || !this.account.equals(account) || publicKey == null || !MessageDigest.isEqual(this.publicKey, publicKey))
            throw new IllegalStateException("The network, producer account or signing key changed. Review production again in step 4.");
    }
}
