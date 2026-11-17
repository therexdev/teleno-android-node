package com.koinos.teleno;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.system.Os;
import android.util.AtomicFile;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

/** Device-bound encrypted block-signing key, deliberately separate from a funds wallet. */
final class KeyVault {
    // Activities can be recreated while an earlier key task is still finishing.
    // Serialize across all vault instances so a second task cannot replace the
    // wrapping alias or race the first atomic key publication.
    private static final Object VAULT_LOCK = new Object();
    private final File file;
    private final String alias;
    KeyVault(Context c, String network) {
        NodeConfig.checkNetwork(network);
        file = new File(c.getNoBackupFilesDir(), "signer-" + network + ".gcm");
        alias = "teleno-block-signer-v1-" + network;
    }
    boolean exists() { return file.isFile() || new File(file + ".bak").isFile(); }
    private SecretKey wrappingKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(alias)) {
            if (!create || exists()) throw new IllegalStateException("The device encryption key is unavailable. Your encrypted signing key was preserved. Restore your offline key on a fresh installation.");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey) ks.getKey(alias, null);
    }
    byte[] create() throws Exception { synchronized (VAULT_LOCK) {
        if (exists()) throw new IllegalStateException("A signing key already exists. It will not be replaced.");
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        try { byte[] pub = NativeKeys.publicKey(secret); save(secret); return pub; }
        finally { Arrays.fill(secret, (byte) 0); }
    } }
    byte[] importWif(String wif) throws Exception { synchronized (VAULT_LOCK) {
        if (exists()) throw new IllegalStateException("A signing key already exists. It will not be replaced.");
        byte[] secret = Base58.privateKey(wif.trim());
        try { byte[] pub = NativeKeys.publicKey(secret); save(secret); return pub; }
        finally { Arrays.fill(secret, (byte) 0); }
    } }
    private void save(byte[] secret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(true));
        cipher.updateAAD(alias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(secret); byte[] nonce = cipher.getIV();
        if (nonce.length != 12 || encrypted.length != 48) throw new IllegalStateException("Unexpected encryption format.");
        byte[] envelope = new byte[61]; envelope[0] = 1;
        System.arraycopy(nonce, 0, envelope, 1, 12); System.arraycopy(encrypted, 0, envelope, 13, 48);
        AtomicFile target = new AtomicFile(file); FileOutputStream stream = null;
        try { stream = target.startWrite(); Os.fchmod(stream.getFD(), 0600); stream.write(envelope); target.finishWrite(stream); }
        catch (Exception e) { if (stream != null) target.failWrite(stream); throw e; }
    }
    byte[] read() throws Exception { synchronized (VAULT_LOCK) {
        byte[] envelope = new AtomicFile(file).readFully();
        if (envelope.length != 61 || envelope[0] != 1) throw new IllegalStateException("The encrypted signing key is unreadable. It was preserved; do not reset app data.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(false), new GCMParameterSpec(128, Arrays.copyOfRange(envelope, 1, 13)));
        cipher.updateAAD(alias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher.doFinal(envelope, 13, 48);
    } }
    byte[] publicKey() throws Exception { byte[] k = read(); try { return NativeKeys.publicKey(k); } finally { Arrays.fill(k, (byte) 0); } }
    String exportWif() throws Exception { byte[] k = read(); try { return Base58.wif(k); } finally { Arrays.fill(k, (byte) 0); } }
}
