package com.koinos.teleno;

import org.json.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

public final class DashboardTests {
    static int assertions;static String account,other,koin,vhp;
    static final String CHAIN="EiBZK_GGVP0H_fXVAM3j6EAuz3-B-l3ejxRSewi7qIBfSA==";
    interface Checked{void run()throws Exception;}
    static void check(boolean v,String s){assertions++;if(!v)throw new AssertionError(s);}
    static void rejects(Checked f,String s)throws Exception{boolean rejected=false;try{f.run();}catch(Exception e){rejected=true;}check(rejected,s);}
    static String address(int n)throws Exception{byte[] b=new byte[21];b[1]=(byte)n;return Base58.encodeChecked(b);}
    static String id(int n){return "0x1220"+String.format(Locale.ROOT,"%064x",n);}
    static byte[] event(String who,long amount)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(Proto.bytes(Base58.address(who)));out.write(16);do{out.write((int)(amount&127)|(amount>127?128:0));amount>>>=7;}while(amount!=0);return out.toByteArray();}
    static JSONObject block(int seq)throws Exception{
        JSONArray events=new JSONArray().put(new JSONObject().put("source",koin).put("name","token.mint_event").put("data",WalletReader.b64(event(account,425000000))))
            .put(new JSONObject().put("sequence",1).put("source",vhp).put("name","token.burn_event").put("data",WalletReader.b64(event(account,409000000))));
        return new JSONObject().put("seq_num",""+seq).put("block",new JSONObject().put("id",id(seq+1)).put("header",new JSONObject().put("height","100").put("timestamp",""+(System.currentTimeMillis()-10000)).put("signer",account)).put("receipt",new JSONObject().put("id",id(seq+1)).put("events",events)));
    }
    static final class Fixture implements WalletReader.Rpc {
        JSONArray history=new JSONArray();String reported=CHAIN;boolean failMana,failHistory;
        public JSONObject call(String method,JSONObject p)throws Exception{
            if(method.equals("chain.get_chain_id"))return new JSONObject().put("chain_id",reported);
            if(method.equals("chain.invoke_system_call")){String name=new String(Proto.valueBytes(WalletReader.unb64(p.getString("args"))),java.nio.charset.StandardCharsets.UTF_8);return new JSONObject().put("value",WalletReader.b64(Proto.bytes(Proto.bytes(Base58.address(name.equals("koin")?koin:vhp)))));}
            if(method.equals("chain.read_contract"))return new JSONObject().put("result",WalletReader.b64(new byte[]{8,100}));
            if(method.equals("chain.get_account_rc")){if(failMana)throw new IOException("Read unavailable");return new JSONObject().put("rc","50");}
            if(method.equals("account_history.get_account_history")){check(p.getBoolean("irreversible"),"Only irreversible history requested");if(failHistory)throw new IOException("History unavailable");return new JSONObject().put("values",history);}
            throw new AssertionError("Unexpected method "+method);
        }
    }
    public static void main(String[] args)throws Exception{
        account=address(1);other=address(2);koin=address(3);vhp=address(4);
        check(WalletReader.amount("1").equals("0.00000001"),"Smallest token unit is exact");
        check(WalletReader.amount("18446744073709551615").equals("184467440737.09551615"),"uint64 is never converted to floating point");
        check(WalletReader.amount("-425000000").equals("-4.25"),"Signed changes formatted exactly");
        check(WalletReader.percent("50","100")==50,"Mana capacity percentage");
        check(WalletReader.percent("110","100")==100,"Sample changes cannot overflow visual meter");
        check(WalletReader.percent("0","0")==0,"Empty wallet capacity handled");
        rejects(()->WalletReader.uint("-1"),"Negative balance rejected");rejects(()->WalletReader.uint("18446744073709551616"),"Overflow rejected");rejects(()->WalletReader.uint("1e8"),"Exponential amounts rejected");
        WalletReader.Token token=WalletReader.token(event(account,123),false);check(token.from.equals(account)&&token.value.equals(BigInteger.valueOf(123)),"Token receipt decoded");
        byte[] duplicate=Arrays.copyOf(event(account,123),event(account,123).length+2);duplicate[duplicate.length-2]=16;duplicate[duplicate.length-1]=1;
        rejects(()->WalletReader.token(duplicate,false),"Duplicate amount rejected");
        rejects(()->WalletReader.token(new byte[]{16,1},false),"Missing account rejected");
        byte[] corrupt=event(account,123);corrupt[5]^=1;rejects(()->WalletReader.token(corrupt,false),"Event address checksum checked");
        rejects(()->WalletReader.token(new byte[]{10,25,1},false),"Truncated event rejected");
        WalletReader.Entry reward=WalletReader.normalize(block(20),account,koin,vhp,CHAIN);
        check(reward.reward&&reward.koin.equals("425000000")&&reward.vhp.equals("-409000000"),"Own block reward and consumed VHP separated");
        check(reward.height==100&&reward.time>0,"Block metadata preserved");
        JSONObject unrelated=block(21);unrelated.getJSONObject("block").getJSONObject("header").put("signer",other);
        check(!WalletReader.normalize(unrelated,account,koin,vhp,CHAIN).reward,"Receipt mint alone is not this account's production reward");
        JSONObject foreign=block(22);foreign.getJSONObject("block").getJSONObject("receipt").getJSONArray("events").getJSONObject(0).put("source",other);
        check(!WalletReader.normalize(foreign,account,koin,vhp,CHAIN).reward,"Unknown token cannot create a KOIN reward");
        JSONObject reverted=block(23);reverted.getJSONObject("block").getJSONObject("receipt").put("reverted",true);
        WalletReader.Entry failed=WalletReader.normalize(reverted,account,koin,vhp,CHAIN);check(!failed.reward&&failed.koin.equals("0")&&failed.vhp.equals("0"),"Reverted changes excluded");
        JSONObject mismatch=block(24);mismatch.getJSONObject("block").getJSONObject("receipt").put("id",id(80));rejects(()->WalletReader.normalize(mismatch,account,koin,vhp,CHAIN),"Mismatched receipt rejected");
        JSONObject dupEvent=block(25);JSONArray list=dupEvent.getJSONObject("block").getJSONObject("receipt").getJSONArray("events");list.put(list.getJSONObject(1));rejects(()->WalletReader.normalize(dupEvent,account,koin,vhp,CHAIN),"Duplicate event sequence rejected");
        Fixture f=new Fixture();WalletReader reader=new WalletReader(f,CHAIN);WalletReader.Balance b=reader.balances(account,"fixture");
        check(b.koin.equals("100")&&b.vhp.equals("100")&&b.mana.equals("50")&&b.at>0,"All three balances read and dated");
        f.failMana=true;rejects(()->reader.balances(account,"fixture"),"Mana failure never becomes zero");f.failMana=false;
        f.reported="EiBncD4pKRIQWco_WRqo5Q-xnXR7JuO3PtZv983mKdKHSQ==";rejects(()->reader.balances(account,"fixture"),"Wrong-chain balances rejected");rejects(()->reader.history(account,null),"Wrong-chain history rejected");f.reported=CHAIN;
        for(int i=40;i>20;i--)f.history.put(block(i));WalletReader.Page p=reader.history(account,null);
        check(p.next.equals("20")&&p.count==20&&p.entries.size()==20,"Inclusive history cursor decremented without gaps");
        check(p.newest.equals("40")&&p.oldest.equals("21"),"Coverage range retained");
        rejects(()->reader.history(account,"30"),"Out-of-range page rejected");
        f.history=new JSONArray().put(block(10)).put(block(10));rejects(()->reader.history(account,null),"Repeated history sequence rejected");
        f.history=new JSONArray().put(block(0));check(reader.history(account,null).next==null,"Genesis sequence terminates paging");
        f.history=new JSONArray();check(reader.history(account,null).entries.isEmpty(),"Successful empty history supported");
        JSONObject first=block(0);first.remove("seq_num");f.history=new JSONArray().put(first);
        check(reader.history(account,null).entries.get(0).seq.equals("0"),"Omitted protobuf zero sequence does not hide a new account's history");
        first.put("seq_num",JSONObject.NULL);rejects(()->reader.history(account,null),"Explicit null sequence is invalid, not protobuf's omitted zero");
        JSONObject sponsored=new JSONObject().put("seq_num","1").put("trx",new JSONObject().put("transaction",new JSONObject().put("id",id(50)).put("header",new JSONObject().put("chain_id",CHAIN).put("payer",other).put("payee",account))).put("receipt",new JSONObject().put("id",id(50))));
        check(WalletReader.normalize(sponsored,account,koin,vhp,CHAIN)!=null,"Sponsored account actions are recognized by their payee");
        f.failHistory=true;rejects(()->reader.history(account,null),"Unavailable history is not an empty success");
        check(WalletReader.Entry.from(reward.json()).koin.equals(reward.koin),"History cache preserves exact amount");
        check(WalletReader.Balance.from(b.json()).mana.equals(b.mana),"Balance cache preserves mana");
        System.out.println("PASS: "+assertions+" dashboard balances, receipt validation and history pagination assertions.");
    }
}
