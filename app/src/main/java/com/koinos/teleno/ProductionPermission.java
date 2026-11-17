package com.koinos.teleno;

import java.io.ByteArrayOutputStream;
import java.math.*;
import org.json.*;

/** A bounded VHP allowance to the official Mainnet PoB contract, never a token burn. */
final class ProductionPermission {
    static final String CHAIN="EiBZK_GGVP0H_fXVAM3j6EAuz3-B-l3ejxRSewi7qIBfSA==";
    static final String VHP="12Y5vW6gk8GceH53YfRkRre2Rrcsgw7Naq", POB="159myq5YUhhoVWu3wsHKHiJYKPKGUrGiyv";
    static final long APPROVE=1960973952L, ALLOWANCE=854630305L;
    static final class Snapshot {
        final String account; final BigInteger balance,allowance; final long at;
        Snapshot(String account,BigInteger balance,BigInteger allowance,long at){this.account=account;this.balance=balance;this.allowance=allowance;this.at=at;}
        String status(){return "Mainnet VHP production permission\nRemaining PoB allowance: "+WalletReader.amount(allowance.toString())+" VHP\nWallet VHP: "+WalletReader.amount(balance.toString())+"\n"
            +(allowance.signum()==0?"No allowance is set. KOIN Vault smart accounts need this permission to produce.":"An allowance is present. Each successful block consumes part of it; renew before it runs out.")
            +"\nLocal check: "+java.text.DateFormat.getTimeInstance().format(new java.util.Date(at))+". This is a local state check, not transaction finality.";}
    }
    static BigInteger amount(String text){
        if(text==null||!text.matches("(0|[1-9][0-9]{0,11})(\\.[0-9]{1,8})?"))throw new IllegalArgumentException("Enter a VHP amount with up to 8 decimal places, without commas or an exponent.");
        BigInteger n=new BigDecimal(text).movePointRight(8).toBigIntegerExact();validate(n);return n;
    }
    static void validate(BigInteger n){if(n==null||n.signum()<0||n.compareTo(WalletReader.UINT_MAX)>=0)throw new IllegalArgumentException("Choose a limited VHP allowance. Unlimited approval is not supported.");}
    static byte[] allowanceArgs(String account)throws Exception{
        byte[] a=Proto.bytes(Base58.address(account)),b=Proto.bytes(Base58.address(POB));b[0]=18;
        byte[] out=java.util.Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;
    }
    static byte[] approveArgs(String account,BigInteger value)throws Exception{
        validate(value);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] accounts=allowanceArgs(account);out.write(accounts,0,accounts.length);out.write(24);
        // Koilib's canonical token ABI encoding includes an explicitly supplied zero.
        BigInteger n=value;do{int bits=n.and(BigInteger.valueOf(127)).intValue();n=n.shiftRight(7);out.write(bits|(n.signum()>0?128:0));}while(n.signum()>0);return out.toByteArray();
    }
    static JSONObject operation(String account,BigInteger value)throws Exception{
        return new JSONObject().put("call_contract",new JSONObject().put("contract_id",VHP).put("entry_point",APPROVE).put("args",WalletReader.b64(approveArgs(account,value))));
    }
    // Protobuf JSON omits an empty bytes result when uint64 is the default zero.
    static byte[] result(JSONObject response)throws Exception{
        if(!response.has("result"))return new byte[0];Object value=response.get("result");
        if(!(value instanceof String))throw new IllegalArgumentException("Invalid token response.");return WalletReader.unb64((String)value);
    }
    static Snapshot read(WalletReader.Rpc rpc,String account,long at)throws Exception{
        Base58.address(account);WalletReader reader=new WalletReader(rpc,CHAIN);reader.verify();
        if(!VHP.equals(reader.contract("vhp"))||!POB.equals(reader.contract("pob")))throw new IllegalStateException("The local node did not report the official Mainnet VHP and PoB contracts.");
        BigInteger balance=readAmount(rpc,1550980247L,Proto.bytes(Base58.address(account)));
        BigInteger allowance=readAmount(rpc,ALLOWANCE,allowanceArgs(account));reader.verify();return new Snapshot(account,balance,allowance,at);
    }
    private static BigInteger readAmount(WalletReader.Rpc rpc,long entry,byte[] args)throws Exception{
        return Proto.valueUint64(result(rpc.call("chain.read_contract",new JSONObject().put("contract_id",VHP).put("entry_point",entry).put("args",WalletReader.b64(args)))));
    }
    static void requireCurrent(Snapshot reviewed,Snapshot current,BigInteger value,long now){
        validate(value);
        if(!reviewed.account.equals(current.account)||now<reviewed.at||now-reviewed.at>120000)throw new IllegalStateException("The permission review expired or the account changed. Check permission and review again.");
        if(!reviewed.allowance.equals(current.allowance))throw new IllegalStateException("The remaining allowance changed. Check permission and review the new value before replacing it.");
        if(value.compareTo(current.balance)>0)throw new IllegalStateException("The allowance exceeds the current VHP balance. Choose a smaller amount.");
    }
    static String review(Snapshot s,BigInteger value){
        validate(value);return "Network: Mainnet\nOwner: "+s.account+"\nToken: VHP\nVHP contract: "+VHP+"\nSpender: official PoB\n"+POB
            +"\n\nRemaining allowance: "+WalletReader.amount(s.allowance.toString())+" VHP\nNew allowance: "+WalletReader.amount(value.toString())+" VHP"
            +"\n\nThis REPLACES the remaining allowance; it does not add to it. "
            +(value.signum()==0?"Once applied, PoB has no remaining VHP allowance for this account. Stop production before revoking.":"PoB can consume up to this amount of VHP during block production for KOIN rewards without asking for another passkey approval. It remains available until consumed or revoked; future deposits are not automatically added.")
            +"\n\nNo tokens move in this approval transaction. The signing key gains no transfer authority. KOIN Vault will decode this single VHP approval and ask for your passkey. Return here and check permission before starting production.";
    }
}
