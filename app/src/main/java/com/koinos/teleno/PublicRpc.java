package com.koinos.teleno;

import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Fixed HTTPS endpoints and a read-only method allowlist. Redirects are refused. */
final class PublicRpc implements WalletReader.Rpc {
    final String endpoint;
    PublicRpc(String endpoint) {
        if(!Arrays.asList("https://api.koinos.io","https://api.koinosai.com","https://harbinger-api.koinos.io").contains(endpoint))throw new IllegalArgumentException("Unknown data source.");this.endpoint=endpoint;
    }
    static String[] endpoints(String network) {
        NodeConfig.checkNetwork(network);return "mainnet".equals(network)?new String[]{"https://api.koinos.io","https://api.koinosai.com"}:new String[]{"https://harbinger-api.koinos.io"};
    }
    public JSONObject call(String method,JSONObject params) throws Exception {
        if(!Arrays.asList("chain.get_chain_id","chain.invoke_system_call","chain.read_contract","chain.get_account_rc","account_history.get_account_history").contains(method))throw new IllegalArgumentException("Read-only method required.");
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Read cancelled.");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("User-Agent","Teleno-Android/0.3.0");
        byte[] body=new JSONObject().put("jsonrpc","2.0").put("id",1).put("method",method).put("params",params).toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
        try {
            try(OutputStream out=c.getOutputStream()){out.write(body);}
            int code=c.getResponseCode();if(code!=200)throw new IOException("Public data source returned HTTP "+code+".");
            JSONObject response;try(InputStream in=c.getInputStream()){response=new JSONObject(new String(NodeConfig.readBounded(in,4*1024*1024),StandardCharsets.UTF_8));}
            if(response.has("error"))throw new IOException("Public data source could not complete this read.");return response.getJSONObject("result");
        } finally {c.disconnect();}
    }
}
