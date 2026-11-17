package com.koinos.teleno;

import android.content.Context;
import android.security.keystore.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Pairing credentials remain encrypted in no-backup phone storage, including on SD setups. */
final class VaultStore implements VaultClient.Store {
    private static final String ALIAS="teleno-koin-vault-session-v1";
    private final File file;
    VaultStore(Context c){file=new File(c.getNoBackupFilesDir(),"koin-vault-session.gcm");}
    private SecretKey key(boolean create) throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            if(!create||file.exists()||new File(file+".bak").exists())throw new IOException("Wallet connection storage is locked. Existing requests were preserved.");
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();
        }return(SecretKey)ks.getKey(ALIAS,null);
    }
    public JSONObject load() throws Exception {
        if(!file.exists()&&!new File(file+".bak").exists())return new JSONObject();
        byte[] bytes;try(InputStream in=new AtomicFile(file).openRead()){bytes=NodeConfig.readBounded(in,65536);}
        if(bytes.length<29||bytes[0]!=1)throw new IOException("Wallet connection data is unreadable. It was preserved.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Arrays.copyOfRange(bytes,1,13)));cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] plain=cipher.doFinal(bytes,13,bytes.length-13);try{return new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{Arrays.fill(plain,(byte)0);}
    }
    public void save(JSONObject value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] plain=value.toString().getBytes(StandardCharsets.UTF_8);try{byte[] encrypted=cipher.doFinal(plain),nonce=cipher.getIV();if(nonce.length!=12)throw new IOException("Invalid session encryption.");
            byte[] out=new byte[13+encrypted.length];out[0]=1;System.arraycopy(nonce,0,out,1,12);System.arraycopy(encrypted,0,out,13,encrypted.length);NodeConfig.writePrivate(file,out);
        }finally{Arrays.fill(plain,(byte)0);}
    }
}
