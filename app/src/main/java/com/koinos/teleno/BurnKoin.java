package com.koinos.teleno;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import org.json.*;

/** Exact, wallet-approved PoB burn. No funds key is held by this application. */
final class BurnKoin {
    static final long BURN=2241834181L;
    static final BigInteger MANA_RESERVE=new BigInteger("100000000");
    static final class Snapshot {
        final ProductionPermission.Snapshot permission;
        final BigInteger koin,mana;
        final String koinContract;
        Snapshot(ProductionPermission.Snapshot p,String koinContract,BigInteger koin,BigInteger mana){this.permission=p;this.koinContract=koinContract;this.koin=koin;this.mana=mana;}
        BigInteger maximum(){return koin.min(mana.subtract(MANA_RESERVE).max(BigInteger.ZERO));}
    }
    static Snapshot read(WalletReader.Rpc rpc,String account,long at)throws Exception {
        ProductionPermission.Snapshot permission=ProductionPermission.read(rpc,account,at);
        WalletReader reader=new WalletReader(rpc,ProductionPermission.CHAIN);
        String koin=reader.contract("koin");
        if(koin.equals(ProductionPermission.VHP)||koin.equals(ProductionPermission.POB))throw new IllegalStateException("The node returned an invalid KOIN contract.");
        // Verify current Mainnet KCS-4 allowance support before building a burn.
        Proto.valueUint64(ProductionPermission.result(rpc.call("chain.read_contract",new JSONObject().put("contract_id",koin).put("entry_point",ProductionPermission.ALLOWANCE).put("args",WalletReader.b64(ProductionPermission.allowanceArgs(account))))));
        WalletReader.Balance balances=reader.balances(account,"Verified Mainnet node");
        // Production can consume VHP between these reads. Use the latest balance
        // for the review; it is an estimate, never an instruction to spend more.
        permission=new ProductionPermission.Snapshot(account,new BigInteger(balances.vhp),permission.allowance,at);
        reader.verify();return new Snapshot(permission,koin,new BigInteger(balances.koin),new BigInteger(balances.mana));
    }
    static BigInteger amount(String text) {
        BigInteger n=ProductionPermission.amount(text);
        if(n.signum()==0)throw new IllegalArgumentException("Enter a positive KOIN amount.");return n;
    }
    static BigInteger allowance(Snapshot s,BigInteger amount) {
        BigInteger n=s.permission.balance.add(amount);ProductionPermission.validate(n);return n;
    }
    static void requireCurrent(Snapshot reviewed,Snapshot current,BigInteger amount,boolean approve,long now) {
        ProductionPermission.validate(amount);
        if(amount.signum()==0||!reviewed.permission.account.equals(current.permission.account)||now<reviewed.permission.at||now-reviewed.permission.at>120000
            ||!reviewed.koinContract.equals(current.koinContract))
            throw new IllegalStateException("The burn review expired or the wallet/network changed. Review the exact amount again.");
        if(amount.compareTo(reviewed.maximum())>0)throw new IllegalStateException("The amount exceeds the reviewed available KOIN or mana. Review again.");
        if(amount.compareTo(current.maximum())>0)throw new IllegalStateException("The amount exceeds available KOIN or mana. Maximum now: "+WalletReader.amount(current.maximum().toString())+" KOIN (1 KOIN of mana reserved).");
        // Keep the exact reviewed cap even if mining changes VHP or allowance.
        if(approve)allowance(reviewed,amount);
    }
    static byte[] burnArgs(String account,BigInteger amount)throws Exception {
        ProductionPermission.validate(amount);if(amount.signum()==0)throw new IllegalArgumentException("Burn amount must be positive.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(8);
        BigInteger n=amount;do{int bits=n.intValue()&127;n=n.shiftRight(7);out.write(bits|(n.signum()>0?128:0));}while(n.signum()>0);
        byte[] a=Proto.bytes(Base58.address(account));a[0]=18;out.write(a);a=a.clone();a[0]=26;out.write(a);return out.toByteArray();
    }
    static JSONArray operations(Snapshot s,BigInteger amount,boolean approve)throws Exception {
        requireCurrent(s,s,amount,approve,s.permission.at);String account=s.permission.account;
        JSONObject koinApprove=new JSONObject().put("call_contract",new JSONObject().put("contract_id",s.koinContract).put("entry_point",ProductionPermission.APPROVE)
            .put("args",WalletReader.b64(ProductionPermission.approveArgs(account,amount))));
        JSONObject burn=new JSONObject().put("call_contract",new JSONObject().put("contract_id",ProductionPermission.POB).put("entry_point",BURN).put("args",WalletReader.b64(burnArgs(account,amount))));
        JSONArray ops=new JSONArray().put(koinApprove).put(burn);
        if(approve)ops.put(ProductionPermission.operation(account,allowance(s,amount)));return ops;
    }
    static String review(Snapshot s,BigInteger amount,boolean approve) {
        return "Network: Mainnet\nWallet / VHP recipient: "+s.permission.account+"\nKOIN contract: "+s.koinContract+"\nOfficial PoB: "+ProductionPermission.POB
            +"\n\nBurn: "+WalletReader.amount(amount.toString())+" KOIN\nReceive: "+WalletReader.amount(amount.toString())+" VHP at the same address"
            +"\nEstimated VHP balance after burn: "+WalletReader.amount(allowance(s,amount).toString())+" VHP"
            +(approve?"\n\nReplace PoB VHP production allowance: "+WalletReader.amount(s.permission.allowance.toString())+" → "+WalletReader.amount(allowance(s,amount).toString())+" VHP\nThis permits PoB to consume that exact VHP amount for block production until used or revoked.":"\n\nThe existing VHP production allowance will stay unchanged. Approve the new amount separately in step 5 when needed.")
            +"\n\nYour node keeps running. Mining can change the balance and remaining allowance while you approve; the burn and new allowance above stay fixed at the exact reviewed amounts."
            +"\n\nBurning KOIN is irreversible. One atomic wallet transaction approves exactly the KOIN burn amount, burns it through PoB"
            +(approve?", then sets the reviewed VHP allowance.":".")+" No wallet private key is imported. Review every operation and approve in KOIN Vault. Requests are saved and never resent automatically.";
    }
    static boolean finalized(WalletReader.Rpc rpc,String account,String txid,BigInteger amount)throws Exception {
        if(!WalletReader.id(txid))throw new IllegalStateException("Wait for KOIN Vault to return the burn transaction ID.");
        WalletReader reader=new WalletReader(rpc,ProductionPermission.CHAIN);String cursor=null;
        for(int i=0;i<5;i++){
            WalletReader.Page page=reader.history(account,cursor);
            for(WalletReader.Entry e:page.entries)if(txid.equals(e.id)) {
                if(!"transaction".equals(e.kind)||!e.koin.equals(amount.negate().toString())||!e.vhp.equals(amount.toString()))
                    throw new IllegalStateException("The irreversible transaction did not report the reviewed KOIN burn and VHP receipt. Check the wallet history before another request.");
                return true;
            }
            if(page.next==null)return false;cursor=page.next;
        }
        return false;
    }
}
