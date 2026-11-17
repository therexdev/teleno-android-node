package com.koinos.teleno;

import android.content.Context;
import android.system.Os;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class NodeConfig {
    static final int RPC_PORT = 18085;
    static final int P2P_PORT = 18888;
    static final String COMMIT = "2c11229267cff2f9ee39ed8e56ab70af391d85ce";
    static void checkNetwork(String n) { if (!"mainnet".equals(n) && !"harbinger".equals(n)) throw new IllegalArgumentException("Unknown network."); }
    static String chainId(String n) { checkNetwork(n); return n.equals("mainnet") ? "EiBZK_GGVP0H_fXVAM3j6EAuz3-B-l3ejxRSewi7qIBfSA==" : "EiBncD4pKRIQWco_WRqo5Q-xnXR7JuO3PtZv983mKdKHSQ=="; }
    static File root(Context c, String n) { checkNetwork(n); return new File(c.getNoBackupFilesDir(), "node-" + n); }
    static File secretFile(Context c, String n) { return new File(root(c, n), "block_producer/private.key"); }
    static void clearTransientKey(Context c, String n) throws IOException {
        File key = secretFile(c, n);
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File candidate = new File(key.getPath() + suffix);
            if (candidate.exists() && !candidate.delete()) throw new IOException("Could not remove the transient signing file. Keep app data private.");
        }
    }
    static byte[] asset(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) { if (out.size() + count > 8 * 1024 * 1024) throw new IOException("Bundled asset is too large."); out.write(buffer, 0, count); }
            return out.toByteArray();
        }
    }
    static void writePrivate(File f, byte[] bytes) throws Exception {
        File parent = f.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create private node storage.");
        Os.chmod(parent.getPath(), 0700);
        AtomicFile target = new AtomicFile(f); FileOutputStream stream = null;
        try { stream = target.startWrite(); Os.fchmod(stream.getFD(), 0600); stream.write(bytes); target.finishWrite(stream); }
        catch (Exception e) { if (stream != null) target.failWrite(stream); throw e; }
    }
    static File prepare(Context c, String network, String producer) throws Exception {
        checkNetwork(network);
        File base = root(c, network); if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Could not prepare node storage.");
        Os.chmod(base.getPath(), 0700);
        File genesis = new File(base, "chain/genesis_data.json");
        byte[] bundled = asset(c, network + "-genesis.json");
        if (genesis.exists() || new File(genesis + ".bak").exists()) {
            try (InputStream in = new AtomicFile(genesis).openRead()) {
                byte[] existing = readBounded(in, 8 * 1024 * 1024);
                if (!Arrays.equals(existing, bundled)) throw new IOException("Existing genesis differs from the selected network. Data was preserved.");
            }
        } else writePrivate(genesis, bundled);
        String text = new String(asset(c, network + "-observer.yml"), StandardCharsets.UTF_8)
            .replace("listen: 127.0.0.1:18122", "listen: 127.0.0.1:" + RPC_PORT)
            .replace("listen: /ip4/0.0.0.0/tcp/18888", "listen: /ip4/0.0.0.0/tcp/" + P2P_PORT)
            .replace("log-level: info", "log-level: info\n  log-color: false")
            .replace("target-peer-count: 20", "target-peer-count: 8");
        if (producer != null) {
            Base58.address(producer);
            File keyDirectory = secretFile(c, network).getParentFile();
            if (!keyDirectory.isDirectory() && !keyDirectory.mkdirs()) throw new IOException("Could not prepare producer storage.");
            Os.chmod(keyDirectory.getPath(), 0700);
            text = text.replace("block_producer: false", "block_producer: true");
            text += "\nblock_producer:\n  algorithm: pob\n  producer: " + producer + "\n  private-key-file: "
                + JSONObject.quote("/proc/self/fd/0")
                + "\n  resources-lower-bound: 75\n  resources-upper-bound: 90\n  max-inclusion-attempts: 2000\n  gossip-production: true\n";
        }
        File config = new File(base, "config.yml"); writePrivate(config, text.getBytes(StandardCharsets.UTF_8)); return config;
    }
    static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) { if (out.size() + n > max) throw new IOException("Response is too large."); out.write(b, 0, n); }
        return out.toByteArray();
    }
}
