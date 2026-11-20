package com.koinos.teleno;

import org.json.*;
import java.util.*;

/** Protocol-2 pairing, key registration and explicitly reviewed, bounded PoB allowances. */
final class VaultClient {
    static final String REGISTRATION="registration", ALLOWANCE="production_allowance", BURN="burn_koin";
    static final String HOME="https://koinvault.app/", ORIGIN="https://koinosai.com";
    interface Transport { JSONObject call(String route,JSONObject body) throws Exception; }
    interface Store { JSONObject load() throws Exception; void save(JSONObject value) throws Exception; }
    interface Clock { long now(); }
    interface PermissionCheck { void verify() throws Exception; }
    static final class ApiException extends java.io.IOException {
        final int status; ApiException(int status,String message){super(message);this.status=status;}
    }
    static final class View {
        final boolean pairing, connected, pending; final String address, knownAddress, note, txid, status, kind, amount;
        View(boolean pairing,boolean connected,boolean pending,String address,String note,String txid,String status,String kind,String amount,String knownAddress) {
            this.pairing=pairing;this.connected=connected;this.pending=pending;this.address=address;this.note=note;this.txid=txid;this.status=status;this.kind=kind;this.amount=amount;this.knownAddress=knownAddress;
        }
    }
    private final Transport transport; private final Store store; private final Clock clock;
    private JSONObject state; private volatile View view;
    VaultClient(Transport transport,Store store,Clock clock) throws Exception { this.transport=transport;this.store=store;this.clock=clock;state=store.load();publish(); }
    View view() { return view; }
    private JSONObject session() { return state.optJSONObject("session"); }
    private JSONObject pending() { return state.optJSONObject("pending"); }
    private String kind() { return pending()==null?"":pending().optString("kind",REGISTRATION); }
    private boolean live() { return session()!=null && session().optLong("expiresAt")>clock.now(); }
    private boolean unresolved() { return pending()!=null && !Arrays.asList("rejected","observed","dismissed").contains(pending().optString("status")); }
    private void publish() {
        JSONObject s=session(),p=pending(); boolean live=live(); String address=live?s.optString("address",""):"";
        String note=p!=null?p.optString("note",""):!live?"Connect KOIN Vault to choose your producer account.":address.isEmpty()?"Approve the connection in KOIN Vault, then return here.":"KOIN Vault connected. Your wallet keys remain in Vault.";
        view=new View(live&&address.isEmpty(),live&&!address.isEmpty(),unresolved(),address,note,p==null?"":p.optString("txid",""),p==null?"":p.optString("status",""),kind(),p==null?"":p.optString("amount",""),state.optString("knownAddress",s!=null?s.optString("address",p==null?"":p.optString("account","")):p==null?"":p.optString("account","")));
    }
    private void save() throws Exception { store.save(state); publish(); }
    private void archivePending() throws Exception {
        if(pending()==null)return;
        JSONArray history=state.optJSONArray("history");if(history==null)history=new JSONArray();history.put(new JSONObject(pending().toString()));
        state.put("history",history);state.remove("pending");
    }
    private JSONObject pair() throws Exception {
        if(!live()) throw new IllegalStateException("The wallet connection expired. Any saved wallet request is preserved.");
        return new JSONObject().put("sessionId",session().getString("sessionId")).put("secret",session().getString("secret"));
    }
    static void token(String value,int size) { if(value==null || !value.matches("[A-Za-z0-9_-]{"+size+"}")) throw new IllegalArgumentException("Invalid wallet connection response."); }
    synchronized void connect() throws Exception {
        if(live()) { publish();return; }
        if(unresolved()) throw new IllegalStateException("Resolve the saved wallet request before reconnecting. It will not be sent again automatically.");
        JSONObject cfg=transport.call("config",null); JSONObject features=cfg.optJSONObject("features");
        if(cfg.optBoolean("demo",true)||!"mainnet".equals(cfg.optString("network"))||features==null||!features.optBoolean("kaiProducer")) throw new IllegalStateException("KOIN Vault producer connections are currently available on live Mainnet only.");
        JSONObject made=transport.call("dapp/create",new JSONObject().put("name","KAI Teleno · Android node").put("icon",ORIGIN+"/favicon.ico").put("protocolVersion",2));
        token(made.optString("sessionId"),24);token(made.optString("secret"),43);
        long expiry=made.optLong("expiresAt");
        if(made.optInt("protocolVersion")!=2 || expiry<=clock.now() || expiry>clock.now()+31*60000L) throw new IllegalStateException("KOIN Vault needs its secure connection update. No legacy URL credentials will be used.");
        state.put("session",new JSONObject().put("sessionId",made.getString("sessionId")).put("secret",made.getString("secret")).put("expiresAt",expiry));
        archivePending();save();
    }
    synchronized String openUrl() throws Exception {
        if(!live() || !session().optString("address","").isEmpty()) return HOME;
        // Construct from validated tokens, never follow a server-supplied URL.
        String id=session().getString("sessionId"),secret=session().getString("secret");token(id,24);token(secret,43);
        return HOME+"#connect="+id+"&secret="+secret;
    }
    synchronized void refresh() throws Exception {
        if(!live()) { publish();return; }
        JSONObject info;
        try{info=transport.call("dapp/status",pair());}
        catch(ApiException e){if(e.status==404){state.remove("session");if(unresolved())pending().put("note","Wallet connection ended. The request and any transaction ID remain saved; check wallet history before retrying.");save();}throw e;}
        if(info.optBoolean("connected")) {
            String account=info.getString("address"); Base58.address(account);
            String prior=session().optString("address","");
            if(!prior.isEmpty()&&!prior.equals(account)) throw new IllegalStateException("KOIN Vault returned a different account. Disconnect and choose the wallet again.");
            session().put("address",account);state.put("knownAddress",account);
        } else if(!session().optString("address","").isEmpty()) {session().remove("address");save();throw new IllegalStateException("The wallet no longer reports this connection as approved.");}
        JSONObject p=pending();
        if(p!=null && unresolved() && p.has("requestId") && p.optLong("expiresAt")>clock.now()) {
            JSONObject result=transport.call("dapp/request-status",pair().put("requestId",p.getString("requestId")));
            String status=result.optString("status");
            if(!Arrays.asList("pending","submitting","approved","rejected","failed").contains(status)) throw new IllegalStateException("Unexpected wallet approval status. The saved request was preserved.");
            String txid=result.optString("txid","");if(!txid.isEmpty() && !txid.matches("0x1220[0-9a-fA-F]{64}")) throw new IllegalStateException("Invalid wallet transaction ID.");
            if("approved".equals(status)&&txid.isEmpty())throw new IllegalStateException("KOIN Vault did not return a transaction ID. The request was preserved for checking.");
            if(!txid.isEmpty()) p.put("txid",txid);
            p.put("status",status);
            p.put("note","approved".equals(status)?"Vault reports the transaction was submitted. Check the requested state on the local node; submission is not final confirmation.":"rejected".equals(status)?"You rejected the request in KOIN Vault. No retry was sent.":"failed".equals(status)?"Wallet request failed. Check wallet history and the local key or permission before retrying.":"submitting".equals(status)?"KOIN Vault is submitting. Keep checking this request; do not send it again.":"Waiting for your passkey approval in KOIN Vault.");
        }
        if(p!=null && unresolved() && p.optLong("expiresAt")<=clock.now()) p.put("note","Approval window ended. The saved transaction is preserved. Check wallet history and the local key or permission; nothing is resubmitted.");
        save();
    }
    static byte[] registrationArgs(String account,byte[] publicKey) throws Exception {
        if(publicKey.length!=33 || (publicKey[0]!=2&&publicKey[0]!=3)) throw new IllegalArgumentException("Invalid block-signing public key.");
        byte[] a=Proto.bytes(Base58.address(account)), b=Proto.bytes(publicKey);b[0]=18;
        byte[] out=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;
    }
    private void requireWallet(String account) throws Exception {
        Base58.address(account);
        if(unresolved()) throw new IllegalStateException("A wallet request already exists. Check it instead of submitting again.");
        refresh(); if(!live()||!account.equals(session().optString("address"))) throw new IllegalStateException("The connected wallet changed. Review the request again.");
    }
    synchronized void register(String expectedAccount,byte[] publicKey) throws Exception {
        requireWallet(expectedAccount);
        String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
        JSONObject op=new JSONObject().put("call_contract",new JSONObject().put("contract_id",ProductionPermission.POB).put("entry_point",1394158561L)
            .put("args",WalletReader.b64(registrationArgs(expectedAccount,publicKey))));
        sendRequest(new JSONObject().put("kind",REGISTRATION).put("account",expectedAccount).put("publicKey",key),op,"public key");
    }
    synchronized void approveProductionAllowance(String account,java.math.BigInteger amount,PermissionCheck check) throws Exception {
        ProductionPermission.validate(amount);requireWallet(account);
        // The UI rechecks the local observer, reviewed allowance, account and balance
        // after refreshing the wallet connection and immediately before durable submission.
        if(check==null)throw new IllegalStateException("A fresh permission review is required.");check.verify();
        sendRequest(new JSONObject().put("kind",ALLOWANCE).put("account",account).put("amount",amount.toString()),ProductionPermission.operation(account,amount),"VHP allowance and official PoB spender");
    }
    synchronized void burnKoin(BurnKoin.Snapshot reviewed,java.math.BigInteger amount,boolean approve,PermissionCheck check)throws Exception {
        requireWallet(reviewed.permission.account);
        if(check==null)throw new IllegalStateException("A fresh burn review is required.");check.verify();
        JSONObject record=new JSONObject().put("kind",BURN).put("account",reviewed.permission.account).put("amount",amount.toString()).put("approveVhp",approve);
        if(approve)record.put("productionAllowance",BurnKoin.allowance(reviewed,amount).toString());
        sendRequest(record,BurnKoin.operations(reviewed,amount,approve),"irreversible KOIN burn, same-wallet VHP receipt"+(approve?" and exact VHP production allowance":""));
    }
    synchronized void checkBurn(WalletReader.Rpc rpc)throws Exception {
        refresh();JSONObject p=pending();
        if(p==null||!BURN.equals(kind()))throw new IllegalStateException("No saved KOIN burn request is available.");
        String id=p.optString("txid","");
        if(!BurnKoin.finalized(rpc,p.getString("account"),id,new java.math.BigInteger(p.getString("amount"))))
            throw new IllegalStateException("The saved burn is not in irreversible history yet. Wait, then check again. Nothing was resent.");
        p.put("status","observed").put("note","The exact burn transaction is irreversible: KOIN was burned and the same amount of VHP reached this wallet. Check the current VHP permission in step 5 before production.");save();
    }
    private void sendRequest(JSONObject p,JSONObject operation,String review)throws Exception {sendRequest(p,new JSONArray().put(operation),review);}
    private void sendRequest(JSONObject p,JSONArray operations,String review) throws Exception {
        p.put("status","sending").put("expiresAt",Math.min(session().getLong("expiresAt"),clock.now()+10*60000L))
            .put("note","Sending one wallet request. Approval is required in KOIN Vault.");
        archivePending();state.put("pending",p);save(); // Durable before network IO; uncertain delivery never resends.
        try {
            JSONObject sent=transport.call("dapp/request",pair().put("operations",operations).put("summary",new JSONObject().put("network","mainnet")));
            token(sent.optString("requestId"),24);long expiry=sent.optLong("expiresAt");
            if(expiry<=clock.now()||expiry>Math.min(session().getLong("expiresAt"),clock.now()+10*60000L)+1000L)throw new IllegalStateException("Unexpected wallet request expiry.");
            p.put("requestId",sent.getString("requestId")).put("expiresAt",Math.min(expiry,p.getLong("expiresAt"))).put("status","pending")
                .put("note","Open KOIN Vault, review the "+review+" and approve with your passkey. Return here to check progress.");save();
        } catch(Exception e) {
            p.put("status","unknown").put("note","Request delivery is uncertain. Open KOIN Vault and check its pending approvals. The request is saved and will not be sent again automatically.");save();throw new IllegalStateException(p.getString("note"));
        }
    }
    synchronized void observed(String account,byte[] publicKey) throws Exception {
        if(pending()!=null&&!REGISTRATION.equals(kind())){
            if(unresolved())throw new IllegalStateException("Resolve the saved wallet request using Check burn or Check VHP permission first.");
            archivePending();
        }
        JSONObject p=pending();String key=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
        if(p!=null && (!account.equals(p.optString("account"))||!key.equals(p.optString("publicKey")))) throw new IllegalStateException("The local registration check does not match the saved request.");
        if(p==null){p=new JSONObject().put("account",account).put("publicKey",key);state.put("pending",p);}
        p.put("status","observed").put("note","The advancing local node reports this public key registered to your account. Transaction finality was not checked.");save();
    }
    synchronized void disconnect() throws Exception {
        if(live()) try{transport.call("dapp/disconnect",pair());}catch(ApiException e){if(e.status!=404)throw e;}
        state.remove("session");if(pending()!=null&&unresolved()) pending().put("note","Disconnected. A submitted transaction cannot be cancelled. Check wallet history and the local node; the request remains saved.");save();
    }
    synchronized void observedAllowance(String account,java.math.BigInteger allowance) throws Exception {
        JSONObject p=pending();
        if(p==null||!ALLOWANCE.equals(kind())||!account.equals(p.optString("account"))||!allowance.toString().equals(p.optString("amount")))
            throw new IllegalStateException("The local allowance does not match the saved request yet. Check KOIN Vault and wait for the observer to catch up.");
        p.put("status","observed").put("note","The advancing local observer reports the requested VHP allowance. Transaction finality was not checked. Production does not start automatically.");save();
    }
    synchronized void dismissExpiredRegistration() throws Exception { dismissExpired(REGISTRATION); }
    synchronized void dismissExpiredAllowance() throws Exception { dismissExpired(ALLOWANCE); }
    synchronized void dismissExpiredBurn() throws Exception { dismissExpired(BURN); }
    private void dismissExpired(String expected) throws Exception {
        if(pending()==null||!expected.equals(kind())||pending().optLong("expiresAt")>clock.now())throw new IllegalStateException("Wait until this request's approval window ends, or reject it in KOIN Vault.");
        pending().put("status","dismissed").put("note","Expired request record closed after wallet review. This does not cancel or revoke an on-chain permission. Check the local state before another review. Nothing was sent.");save();
    }
}
