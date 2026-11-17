package com.koinos.teleno;

import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class VaultTransport implements VaultClient.Transport {
    public JSONObject call(String route,JSONObject body) throws Exception {
        if(!Arrays.asList("config","dapp/create","dapp/status","dapp/request","dapp/request-status","dapp/disconnect").contains(route)) throw new IllegalArgumentException("Unsupported wallet operation.");
        HttpsURLConnection c=(HttpsURLConnection)new URL(VaultClient.HOME+"api/"+route).openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("Origin",VaultClient.ORIGIN);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");
        try {
            if(body!=null) {c.setRequestMethod("POST");c.setDoOutput(true);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);} }
            int status=c.getResponseCode();
            if(status!=200) throw new VaultClient.ApiException(status,status==404?"KOIN Vault connection or approval expired. Saved requests are preserved.":status==429?"KOIN Vault is busy. Wait before checking again.":"KOIN Vault could not complete this request (HTTP "+status+"). Check the wallet before retrying.");
            JSONObject result;try(InputStream in=c.getInputStream()){result=new JSONObject(new String(NodeConfig.readBounded(in,100000),StandardCharsets.UTF_8));}
            if(!result.optBoolean("ok")) throw new IOException("KOIN Vault did not accept this request.");return result;
        } finally {c.disconnect();}
    }
}
