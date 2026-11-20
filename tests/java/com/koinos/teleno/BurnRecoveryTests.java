package com.koinos.teleno;

import java.math.BigInteger;
import org.json.*;
import java.util.Arrays;

/** Deterministic failure, exact-amount and durable wallet-request tests. No signing. */
public final class BurnRecoveryTests {
    static int count;
    static final String ACCOUNT=PermissionTests.ACCOUNT;
    static final BigInteger AMOUNT=new BigInteger("123456789");
    interface Run{void run()throws Exception;}
    static void check(boolean ok,String note){count++;if(!ok)throw new AssertionError(note);}
    static void rejects(Run run,String note)throws Exception{boolean fail=false;try{run.run();}catch(Exception e){fail=true;}check(fail,note);}
    static BurnKoin.Snapshot snapshot(long at) {
        return new BurnKoin.Snapshot(new ProductionPermission.Snapshot(ACCOUNT,new BigInteger("300000000"),new BigInteger("200000000"),at),"19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK",new BigInteger("1000000000"),new BigInteger("500000000"));
    }
    static final class Rpc implements WalletReader.Rpc {
        JSONArray history=new JSONArray();boolean wrong,zero;int irreversibleReads;
        public JSONObject call(String method,JSONObject params)throws Exception {
            if(method.equals("chain.get_chain_id"))return new JSONObject().put("chain_id",wrong?"AQ==":ProductionPermission.CHAIN);
            if(method.equals("chain.invoke_system_call")) {
                String name=new String(Proto.valueBytes(WalletReader.unb64(params.getString("args"))),java.nio.charset.StandardCharsets.UTF_8);
                String address=name.equals("koin")?snapshot(0).koinContract:name.equals("vhp")?ProductionPermission.VHP:ProductionPermission.POB;
                return new JSONObject().put("value",WalletReader.b64(Proto.bytes(Proto.bytes(Base58.address(address)))));
            }
            if(method.equals("chain.get_account_rc"))return new JSONObject().put("rc",zero?"0":"500000000");
            if(method.equals("chain.read_contract")) {
                boolean koin=params.getString("contract_id").equals(snapshot(0).koinContract),allowance=params.getLong("entry_point")==ProductionPermission.ALLOWANCE;
                if(zero||(koin&&allowance))return new JSONObject();
                long value=koin?1000000000L:allowance?200000000L:300000000L;
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(8);
                do{out.write((int)(value&127)|(value>127?128:0));value>>>=7;}while(value!=0);
                return new JSONObject().put("result",WalletReader.b64(out.toByteArray()));
            }
            if(method.equals("account_history.get_account_history")){check(params.getBoolean("irreversible"),"burn confirmation requests irreversible history");irreversibleReads++;return new JSONObject().put("values",history);}
            throw new AssertionError(method);
        }
        void receipt(String id,boolean reverted)throws Exception {
            JSONArray events=new JSONArray().put(new JSONObject().put("source",snapshot(0).koinContract).put("name","token.burn_event").put("data",WalletReader.b64(DashboardTests.event(ACCOUNT,123456789))))
                .put(new JSONObject().put("source",ProductionPermission.VHP).put("name","token.mint_event").put("data",WalletReader.b64(DashboardTests.event(ACCOUNT,123456789))));
            history=new JSONArray().put(new JSONObject().put("seq_num","1").put("trx",new JSONObject()
                .put("transaction",new JSONObject().put("id",id).put("header",new JSONObject().put("chain_id",ProductionPermission.CHAIN).put("payer",ACCOUNT)))
                .put("receipt",new JSONObject().put("id",id).put("reverted",reverted).put("events",events))));
        }
    }
    public static void main(String[] args)throws Exception {
        RecoveryPolicy policy=new RecoveryPolicy();
        check(!policy.rpcExpired(0)&&!policy.rpcExpired(15000)&&!policy.rpcExpired(179999),"brief RPC errors never terminate a run");
        check(policy.rpcExpired(180000),"sustained RPC failure requests recovery");policy.rpcHealthy();
        check(!policy.rpcExpired(200000),"a good response resets the RPC failure window");
        check(!policy.stalled(0,false)&&!policy.stalled(299999,false)&&policy.stalled(300000,false),"production gets five minutes to recover chain checks");
        check(!policy.stalled(400000,true)&&!policy.stalled(400001,false),"fresh readiness clears the stale-chain window");
        for(long expected:new long[]{5000,10000,20000,40000,60000,60000})check(policy.retryDelay()==expected,"bounded retry backoff");
        policy.healthy();check(policy.retryDelay()==5000,"stable running resets backoff");
        policy.processStarted();check(!policy.producerExpired(0,-1)&&!policy.producerExpired(299999,-1)&&policy.producerExpired(300000,-1),"missing native eligibility checks trigger recovery");
        policy.processStarted();for(long t=0;t<3600000;t+=60000)check(!policy.producerExpired(t,t/60000),"advancing eligibility checks stay running even without a reward");
        policy.processStarted();check(!policy.observerExpired(0,100)&&!policy.observerExpired(599999,100)&&policy.observerExpired(600000,100),"stuck observer is reconnected");
        check(!policy.observerExpired(600001,101),"observer block progress resets watchdog");
        BurnKoin.Snapshot s=snapshot(1000000);
        check(BurnKoin.amount("1.23456789").equals(AMOUNT),"exact eight-decimal burn amount");
        for(String text:new String[]{"0","-1","1e3","1,000","0.000000001","184467440737.09551615"})rejects(()->BurnKoin.amount(text),"reject ambiguous/zero/unlimited amount");
        check(s.maximum().equals(new BigInteger("400000000")),"mana caps maximum below balance, with 1 KOIN reserve");
        BurnKoin.requireCurrent(s,s,AMOUNT,true,1000001);count++;
        rejects(()->BurnKoin.requireCurrent(s,s,AMOUNT,true,1120001),"expired burn review");
        rejects(()->BurnKoin.requireCurrent(s,s,new BigInteger("400000001"),true,1000001),"insufficient mana");
        BurnKoin.Snapshot changed=new BurnKoin.Snapshot(s.permission,s.koinContract,BigInteger.ONE,s.mana);
        rejects(()->BurnKoin.requireCurrent(s,changed,AMOUNT,true,1000001),"wallet balance changed after review");
        JSONArray ops=BurnKoin.operations(s,AMOUNT,true);
        check(ops.length()==3,"one atomic request includes KOIN approve, PoB burn, VHP approve");
        check(ops.getJSONObject(0).getJSONObject("call_contract").getString("contract_id").equals(s.koinContract),"first approval targets only KOIN");
        check(ops.getJSONObject(1).getJSONObject("call_contract").getString("contract_id").equals(ProductionPermission.POB),"burn only through official PoB");
        check(ops.getJSONObject(1).getJSONObject("call_contract").getLong("entry_point")==BurnKoin.BURN,"PoB burn entry point");
        check(ops.getJSONObject(2).getJSONObject("call_contract").getString("args").equals(WalletReader.b64(ProductionPermission.approveArgs(ACCOUNT,new BigInteger("423456789")))),"new production allowance is existing VHP plus exact burn");
        check(BurnKoin.operations(s,AMOUNT,false).length()==2,"VHP approval can be declined");
        byte[] wire=BurnKoin.burnArgs(ACCOUNT,AMOUNT);Proto.Reader reader=new Proto.Reader(wire);
        check(reader.varint()==8&&reader.varint()==123456789L,"burn protobuf exact amount");
        check(reader.varint()==18&&Arrays.equals(reader.data(),Base58.address(ACCOUNT)),"KOIN source is selected wallet");
        check(reader.varint()==26&&Arrays.equals(reader.data(),Base58.address(ACCOUNT))&&reader.at==wire.length,"VHP recipient is same wallet, no extra fields");
        String review=BurnKoin.review(s,AMOUNT,true);
        check(review.contains("1.23456789 KOIN")&&review.contains("4.23456789 VHP")&&review.contains("irreversible")&&review.contains(ACCOUNT),"review includes exact burn, resulting allowance and owner");
        Rpc rpc=new Rpc();BurnKoin.Snapshot loaded=BurnKoin.read(rpc,ACCOUNT,1000000);
        check(loaded.koin.equals(s.koin)&&loaded.permission.balance.equals(s.permission.balance)&&loaded.maximum().equals(s.maximum()),"burn review loads verified balances, mana and current allowances");
        rpc.zero=true;check(BurnKoin.read(rpc,ACCOUNT,1000000).maximum().signum()==0,"omitted protobuf zero balances remain zero");rpc.zero=false;
        rpc.wrong=true;rejects(()->BurnKoin.read(rpc,ACCOUNT,1000000),"burn cannot use another chain");rpc.wrong=false;
        String txid="0x1220"+"d".repeat(64);
        check(!BurnKoin.finalized(rpc,ACCOUNT,txid,AMOUNT),"unconfirmed burn is never treated as finalized");
        rpc.receipt(txid,false);check(BurnKoin.finalized(rpc,ACCOUNT,txid,AMOUNT),"exact irreversible burn receipt confirms");
        check(!BurnKoin.finalized(rpc,ACCOUNT,"0x1220"+"a".repeat(64),AMOUNT),"another transaction cannot confirm the burn");
        rpc.receipt(txid,true);rejects(()->BurnKoin.finalized(rpc,ACCOUNT,txid,AMOUNT),"reverted burn cannot confirm");rpc.receipt(txid,false);
        VaultTests.Disk disk=new VaultTests.Disk();VaultTests.Fake transport=new VaultTests.Fake();transport.disk=disk;
        VaultClient vault=new VaultClient(transport,disk,transport);vault.connect();vault.refresh();int[] guard={0};
        vault.burnKoin(s,AMOUNT,true,()->guard[0]++);
        check(guard[0]==1&&transport.requests==1&&transport.sent.getJSONArray("operations").length()==3,"fresh guard then one combined request");
        check(disk.data.getJSONObject("pending").getString("productionAllowance").equals("423456789"),"exact allowance persisted before wallet IO");
        rejects(()->vault.burnKoin(s,AMOUNT,true,()->{}),"no duplicate burn while pending");
        rejects(()->vault.observedAllowance(ACCOUNT,new BigInteger("423456789")),"matching allowance alone cannot resolve a burn");
        VaultClient restored=new VaultClient(transport,disk,transport);
        rejects(()->restored.burnKoin(s,AMOUNT,true,()->{}),"process restart cannot resend burn");
        check(transport.requests==1&&restored.view().kind.equals(VaultClient.BURN),"one durable burn request");
        rejects(restored::dismissExpiredBurn,"live request cannot be dismissed");
        transport.result="approved";restored.checkBurn(rpc);check(!restored.view().pending&&restored.view().note.contains("irreversible"),"only exact irreversible receipt resolves saved burn");
        restored.burnKoin(s,AMOUNT,false,()->{});check(transport.requests==2,"a separate burn needs a new explicit review after confirmation");
        transport.now+=1900000;restored.dismissExpiredBurn();check(!restored.view().pending,"expired request requires explicit wallet review dismissal");
        VaultTests.Disk uncertain=new VaultTests.Disk();VaultTests.Fake lost=new VaultTests.Fake();lost.disk=uncertain;
        VaultClient failed=new VaultClient(lost,uncertain,lost);failed.connect();failed.refresh();lost.timeout=true;
        rejects(()->failed.burnKoin(s,AMOUNT,true,()->{}),"timeout preserves unknown burn delivery");
        VaultClient retry=new VaultClient(lost,uncertain,lost);rejects(()->retry.burnKoin(s,AMOUNT,true,()->{}),"unknown burn is never resent");
        check(lost.requests==1&&retry.view().status.equals("unknown"),"timeout saved, exactly one POST");
        VaultTests.Disk noDisk=new VaultTests.Disk();VaultTests.Fake noPost=new VaultTests.Fake();noPost.disk=noDisk;
        VaultClient blocked=new VaultClient(noPost,noDisk,noPost);blocked.connect();blocked.refresh();noDisk.fail=true;
        rejects(()->blocked.burnKoin(s,AMOUNT,true,()->{}),"persistence failure blocks burn");check(noPost.requests==0,"no wallet POST without saved intent");
        if(args.length>0)java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),ops.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("PASS: "+count+" burn and recovery assertions; no live wallet signing.");
    }
}
