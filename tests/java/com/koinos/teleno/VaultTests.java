package com.koinos.teleno;
import org.json.*;
import java.io.IOException;
import java.util.*;

public final class VaultTests {
    static int count;static final String ACCOUNT="1111111111111111111114oLvT2";
    static void check(boolean b,String s){count++;if(!b)throw new AssertionError(s);}
    interface Run{void run()throws Exception;}
    static void rejects(Run r,String s)throws Exception{boolean no=false;try{r.run();}catch(Exception e){no=true;}check(no,s);}
    static class Disk implements VaultClient.Store{JSONObject data=new JSONObject();boolean fail;
        public JSONObject load(){return new JSONObject(data.toString());}public void save(JSONObject v)throws Exception{if(fail)throw new IOException("disk full");data=new JSONObject(v.toString());}}
    static class Fake implements VaultClient.Transport,VaultClient.Clock{
        long now=1000000;int requests,creates;boolean timeout,connected=true,legacy;String result="pending",address=ACCOUNT;JSONObject sent;Disk disk;
        public long now(){return now;}
        public JSONObject call(String route,JSONObject body)throws Exception{
            JSONObject r=new JSONObject().put("ok",true);
            switch(route){
                case "config":return r.put("demo",false).put("network","mainnet").put("features",new JSONObject().put("kaiProducer",true));
                case "dapp/create":creates++;check(body.getInt("protocolVersion")==2,"client requests protocol 2");return r.put("sessionId","A".repeat(24)).put("secret","B".repeat(43)).put("expiresAt",now+1800000).put("protocolVersion",legacy?1:2).put("uri","https://untrusted.invalid/");
                case "dapp/status":check(body.has("secret")&&body.has("sessionId"),"poll credentials in POST body");return r.put("connected",connected).put("address",address);
                case "dapp/request":requests++;check(disk.data.getJSONObject("pending").getString("status").equals("sending"),"persist before registration POST");sent=new JSONObject(body.toString());if(timeout)throw new IOException("timeout");return r.put("requestId","C".repeat(24)).put("expiresAt",now+600000);
                case "dapp/request-status":return r.put("status",result).put("txid",result.equals("approved")?"0x1220"+"d".repeat(64):JSONObject.NULL);
                case "dapp/disconnect":return r;
            }throw new AssertionError(route);
        }
    }
    static VaultClient client(Fake f,Disk d)throws Exception{f.disk=d;return new VaultClient(f,d,f);}
    public static void main(String[] ignored)throws Exception{
        byte[] pub=CoreTests.unhex("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
        Disk d=new Disk();Fake f=new Fake();VaultClient c=client(f,d);c.connect();
        check(c.openUrl().equals("https://koinvault.app/#connect="+"A".repeat(24)+"&secret="+"B".repeat(43)),"fixed origin and fragment; server URL ignored");
        c.refresh();check(c.view().connected&&c.view().address.equals(ACCOUNT),"approved account selected");
        check(c.openUrl().equals(VaultClient.HOME),"connected wallet uses plain reopen URL");
        c.register(ACCOUNT,pub);check(c.view().pending&&f.requests==1,"one registration request");
        JSONObject op=f.sent.getJSONArray("operations").getJSONObject(0).getJSONObject("call_contract");
        check(op.getLong("entry_point")==1394158561L&&op.getString("contract_id").equals("159myq5YUhhoVWu3wsHKHiJYKPKGUrGiyv"),"only official Mainnet public-key registration");
        byte[] expected=CoreTests.unhex("0a19"+"00".repeat(21)+"94a00911"+"1221"+CoreTests.hex(pub));
        check(Arrays.equals(Base64.getUrlDecoder().decode(op.getString("args")),expected),"registration protobuf vector");
        rejects(()->c.register(ACCOUNT,pub),"double submission blocked");
        f.result="approved";c.refresh();check(c.view().status.equals("approved")&&c.view().pending&&c.view().note.contains("not final"),"wallet submission never reported as chain finality");
        String tx=c.view().txid;VaultClient restored=client(f,d);check(restored.view().pending&&restored.view().txid.equals(tx),"pending transaction survives process restart");
        f.now+=1900000;restored.refresh();check(restored.view().pending&&!restored.view().connected&&restored.view().txid.equals(tx),"expiry preserves transaction");
        rejects(restored::connect,"unresolved request blocks reconnect and implicit resend");restored.observed(ACCOUNT,pub);check(!restored.view().pending,"matching local registration resolves saved request");
        Disk ambiguous=new Disk();Fake timeout=new Fake();VaultClient a=client(timeout,ambiguous);a.connect();a.refresh();timeout.timeout=true;
        rejects(()->a.register(ACCOUNT,pub),"uncertain delivery returned");check(a.view().status.equals("unknown"),"unknown delivery preserved");
        VaultClient again=client(timeout,ambiguous);rejects(()->again.register(ACCOUNT,pub),"restart does not resend uncertain request");check(timeout.requests==1,"exactly one POST despite retry");
        rejects(again::dismissExpiredRegistration,"cannot dismiss a live approval");timeout.now+=1900000;again.dismissExpiredRegistration();check(!again.view().pending,"explicit expired registration resolution");
        Disk reject=new Disk();Fake denial=new Fake();VaultClient denied=client(denial,reject);denied.connect();denied.refresh();denied.register(ACCOUNT,pub);denial.result="rejected";denied.refresh();check(!denied.view().pending&&denial.requests==1,"denial never resubmits");
        Fake old=new Fake();old.legacy=true;VaultClient legacy=client(old,new Disk());rejects(legacy::connect,"no legacy credential fallback");
        Disk full=new Disk();Fake disk=new Fake();VaultClient before=client(disk,full);before.connect();before.refresh();full.fail=true;rejects(()->before.register(ACCOUNT,pub),"storage error prevents request");check(disk.requests==0,"no POST without durable record");
        Fake account=new Fake();VaultClient changed=client(account,new Disk());changed.connect();changed.refresh();account.address="19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK";rejects(changed::refresh,"wallet account cannot silently change");
        // Production allowance requests use the same durable protocol but never resolve as key registration.
        Disk grants=new Disk();Fake approvals=new Fake();VaultClient grant=client(approvals,grants);grant.connect();grant.refresh();
        java.math.BigInteger budget=new java.math.BigInteger("100000000000");int[] guarded={0};
        grant.approveProductionAllowance(ACCOUNT,budget,()->guarded[0]++);
        check(guarded[0]==1&&approvals.requests==1&&grant.view().kind.equals(VaultClient.ALLOWANCE),"fresh guard then one allowance request");
        check(grant.view().amount.equals(budget.toString()),"reviewed amount retained");
        check(approvals.sent.getJSONArray("operations").length()==1,"one operation only");
        JSONObject approval=approvals.sent.getJSONArray("operations").getJSONObject(0).getJSONObject("call_contract");
        check(approval.getString("contract_id").equals(ProductionPermission.VHP)&&approval.getLong("entry_point")==ProductionPermission.APPROVE,"only fixed official VHP approve");
        check(Arrays.equals(Base64.getUrlDecoder().decode(approval.getString("args")),ProductionPermission.approveArgs(ACCOUNT,budget)),"exact reviewed owner, PoB and amount sent");
        rejects(()->grant.register(ACCOUNT,pub),"allowance blocks unrelated registration");
        rejects(()->grant.approveProductionAllowance(ACCOUNT,budget,()->{}),"no second allowance while pending");
        rejects(()->grant.observed(ACCOUNT,pub),"registration cannot resolve allowance");
        rejects(grant::dismissExpiredRegistration,"registration dismissal cannot close allowance");
        rejects(grant::dismissExpiredAllowance,"cannot close a live allowance request");
        rejects(()->grant.observedAllowance(ACCOUNT,java.math.BigInteger.ONE),"different local amount cannot confirm request");
        rejects(()->grant.observedAllowance(ProductionPermission.POB,budget),"different owner cannot confirm request");
        approvals.result="approved";grant.refresh();check(grant.view().pending&&!grant.view().txid.isEmpty(),"allowance submission stays unconfirmed");
        VaultClient restarted=client(approvals,grants);check(restarted.view().amount.equals(budget.toString())&&restarted.view().pending,"allowance and ID survive restart");
        approvals.now+=1900000;restarted.refresh();check(!restarted.view().connected&&restarted.view().knownAddress.equals(ACCOUNT),"known Vault account retained after expiry");
        rejects(restarted::dismissExpiredRegistration,"expired allowance still cannot use registration dismissal");
        restarted.observedAllowance(ACCOUNT,budget);check(!restarted.view().pending&&restarted.view().note.contains("finality was not checked"),"offline local allowance resolves without claiming finality");
        restarted.connect();restarted.refresh();restarted.approveProductionAllowance(ACCOUNT,java.math.BigInteger.ZERO,()->{});
        check(restarted.view().amount.equals("0")&&approvals.requests==2,"revoke sends explicit zero only after review");
        restarted.observedAllowance(ACCOUNT,java.math.BigInteger.ZERO);restarted.observed(ACCOUNT,pub);check(restarted.view().kind.equals(VaultClient.REGISTRATION)&&!restarted.view().pending,"key can be checked after permission resolution");restarted.disconnect();check(restarted.view().knownAddress.equals(ACCOUNT),"disconnect retains public Vault identity for preflight");
        Disk uncertain=new Disk();Fake lost=new Fake();VaultClient u=client(lost,uncertain);u.connect();u.refresh();lost.timeout=true;
        rejects(()->u.approveProductionAllowance(ACCOUNT,budget,()->{}),"allowance transport timeout retained");
        VaultClient retry=client(lost,uncertain);rejects(()->retry.approveProductionAllowance(ACCOUNT,budget,()->{}),"no allowance resend after restart");check(lost.requests==1,"one allowance POST despite ambiguous delivery");
        lost.now+=1900000;retry.dismissExpiredAllowance();check(!retry.view().pending&&retry.view().note.contains("does not cancel or revoke"),"closing expired request is distinct from revocation");
        Disk blockedDisk=new Disk();Fake blocked=new Fake();VaultClient block=client(blocked,blockedDisk);block.connect();block.refresh();
        rejects(()->block.approveProductionAllowance(ACCOUNT,budget,()->{throw new IOException("review changed");}),"failed current review prevents request");
        rejects(()->block.approveProductionAllowance(ACCOUNT,WalletReader.UINT_MAX,()->{}),"unlimited cannot reach transport");
        rejects(()->block.approveProductionAllowance(ACCOUNT,budget,null),"missing current review rejected");check(blocked.requests==0,"no requests for rejected reviews");
        blockedDisk.fail=true;rejects(()->block.approveProductionAllowance(ACCOUNT,budget,()->{}),"no allowance without durable state");check(blocked.requests==0,"disk failure prevents allowance POST");
        Disk oldState=new Disk();oldState.data.put("pending",new JSONObject().put("account",ACCOUNT).put("publicKey",Base64.getUrlEncoder().withoutPadding().encodeToString(pub)).put("status","pending"));
        VaultClient migrated=client(new Fake(),oldState);check(migrated.view().kind.equals(VaultClient.REGISTRATION),"legacy pending records remain registrations");migrated.observed(ACCOUNT,pub);
        System.out.println("PASS: "+count+" wallet protocol, wire-format and recovery assertions (fixture transport; no live signing).");
    }
}
