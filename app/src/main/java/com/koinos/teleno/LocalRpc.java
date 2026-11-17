package com.koinos.teleno;

import android.util.Base64;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigInteger;

final class LocalRpc {
    static String b64(byte[] value) { return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    static byte[] unb64(String value) { return Base64.decode(value.replace('+', '-').replace('/', '_'), Base64.URL_SAFE | Base64.NO_WRAP); }
    JSONObject call(String method, JSONObject params) throws Exception {
        if (!java.util.Arrays.asList("chain.get_chain_id", "chain.get_head_info", "chain.read_contract", "chain.invoke_system_call", "node.get_status").contains(method)) throw new IllegalArgumentException("Unsupported local check.");
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + NodeConfig.RPC_PORT + "/").openConnection();
        connection.setConnectTimeout(2500); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
        byte[] body = new JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("The local node returned HTTP " + connection.getResponseCode() + ".");
            JSONObject result;
            try (java.io.InputStream in = connection.getInputStream()) { result = new JSONObject(new String(NodeConfig.readBounded(in, 1024 * 1024), StandardCharsets.UTF_8)); }
            if (result.has("error")) throw new java.io.IOException("This node could not complete " + method + ". It may still be starting or syncing.");
            return result.getJSONObject("result");
        } finally { connection.disconnect(); }
    }
    boolean chainMatches(String network) throws Exception { return MessageDigest.isEqual(unb64(call("chain.get_chain_id", new JSONObject()).getString("chain_id")), unb64(NodeConfig.chainId(network))); }
    String contract(String name) throws Exception {
        JSONObject r = call("chain.invoke_system_call", new JSONObject().put("name", "get_contract_address").put("args", b64(Proto.bytes(name.getBytes(StandardCharsets.UTF_8)))));
        byte[] address = Proto.valueBytes(Proto.valueBytes(unb64(r.getString("value"))));
        if (address.length != 25) throw new IllegalArgumentException("Unexpected contract address.");
        String encoded = Base58.encodeChecked(java.util.Arrays.copyOf(address, 21));
        if (!MessageDigest.isEqual(Base58.address(encoded), address)) throw new IllegalArgumentException("Invalid contract address checksum.");
        return encoded;
    }
    byte[] readContract(String contract, long entry, byte[] args) throws Exception {
        return ProductionPermission.result(call("chain.read_contract", new JSONObject().put("contract_id", contract).put("entry_point", entry).put("args", b64(args))));
    }
    void checkProducer(String account, byte[] publicKey) throws Exception {
        byte[] args = Proto.bytes(Base58.address(account));
        byte[] registered = Proto.valueBytes(readContract(contract("pob"), 2523090792L, args));
        if (!MessageDigest.isEqual(registered, publicKey)) throw new IllegalStateException("This signing key is not registered to the producer account on this chain. Complete registration in your wallet, then wait for it to confirm.");
        BigInteger vhp = Proto.valueUint64(readContract(contract("vhp"), 1550980247L, args));
        if (vhp.signum() <= 0) throw new IllegalStateException("The producer account has no VHP on this node. Complete the burn in your wallet and let the observer catch up.");
    }
}
