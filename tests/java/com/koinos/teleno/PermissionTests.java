package com.koinos.teleno;
import java.math.*;
import java.util.*;
import java.io.*;
import org.json.*;

public final class PermissionTests {
    static int count;static final String ACCOUNT="1111111111111111111114oLvT2";
    interface Run{void run()throws Exception;}
    static void check(boolean b,String note){count++;if(!b)throw new AssertionError(note);}
    static void rejects(Run run,String note)throws Exception{boolean failed=false;try{run.run();}catch(Exception e){failed=true;}check(failed,note);}
    static class Rpc implements WalletReader.Rpc{
        int chains,reads;boolean wrongChain,changedChain,wrongContract,malformed;String allowance="25000000000",balance="12000000000000";
        public JSONObject call(String method,JSONObject params)throws Exception{
            if(method.equals("chain.get_chain_id")){chains++;return new JSONObject().put("chain_id",wrongChain||(changedChain&&chains>1)?"AQ==":ProductionPermission.CHAIN);}
            if(method.equals("chain.invoke_system_call")){
                String name=new String(Proto.valueBytes(WalletReader.unb64(params.getString("args"))),java.nio.charset.StandardCharsets.UTF_8);
                String address=wrongContract?ACCOUNT:name.equals("vhp")?ProductionPermission.VHP:ProductionPermission.POB;
                return new JSONObject().put("value",WalletReader.b64(Proto.bytes(Proto.bytes(Base58.address(address)))));
            }
            if(method.equals("chain.read_contract")){
                reads++;check(params.getString("contract_id").equals(ProductionPermission.VHP),"only official VHP is read");
                boolean approve=params.getLong("entry_point")==ProductionPermission.ALLOWANCE;
                byte[] expected=approve?ProductionPermission.allowanceArgs(ACCOUNT):Proto.bytes(Base58.address(ACCOUNT));
                check(Arrays.equals(expected,WalletReader.unb64(params.getString("args"))),"owner and PoB bound to reads");
                if(malformed)return new JSONObject().put("result",JSONObject.NULL);
                BigInteger n=new BigInteger(approve?allowance:balance);if(n.signum()==0)return new JSONObject();
                ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(8);do{int bits=n.intValue()&127;n=n.shiftRight(7);out.write(bits|(n.signum()>0?128:0));}while(n.signum()>0);
                return new JSONObject().put("result",WalletReader.b64(out.toByteArray()));
            }throw new AssertionError("Unexpected RPC "+method);
        }
    }
    public static void main(String[] args)throws Exception{
        check(ProductionPermission.amount("1000").equals(new BigInteger("100000000000")),"exact decimal scale");
        check(ProductionPermission.amount("0.00000001").equals(BigInteger.ONE),"one atomic unit");
        check(ProductionPermission.amount("184467440737.09551614").equals(WalletReader.UINT_MAX.subtract(BigInteger.ONE)),"largest bounded uint64");
        for(String value:new String[]{"-1","1e3","1,000",".1","1.000000001","01","184467440737.09551615","184467440738","NaN",""})rejects(()->ProductionPermission.amount(value),"invalid or unlimited amount: "+value);
        String vector="ChkAAAAAAAAAAAAAAAAAAAAAAAAAAACUoAkREhkALYlgyhGNCaqiodXSW2XSIEX3iFXkTdCxGIDQ28P0Ag==";
        check(WalletReader.b64(ProductionPermission.approveArgs(ACCOUNT,ProductionPermission.amount("1000"))).equals(vector),"Koilib canonical 1000 VHP vector");
        check(WalletReader.b64(ProductionPermission.approveArgs(ACCOUNT,BigInteger.ZERO)).equals("ChkAAAAAAAAAAAAAAAAAAAAAAAAAAACUoAkREhkALYlgyhGNCaqiodXSW2XSIEX3iFXkTdCxGAA="),"canonical revoke includes explicit zero");
        Rpc rpc=new Rpc();ProductionPermission.Snapshot s=ProductionPermission.read(rpc,ACCOUNT,1000);check(rpc.chains==2&&rpc.reads==2,"chain verified before and after reads");
        check(s.allowance.toString().equals(rpc.allowance)&&s.balance.toString().equals(rpc.balance),"correct allowance and balance");
        ProductionPermission.requireCurrent(s,s,ProductionPermission.amount("1000"),1100);count++;
        rejects(()->ProductionPermission.requireCurrent(s,s,s.balance.add(BigInteger.ONE),1100),"reject amount above balance");
        rejects(()->ProductionPermission.requireCurrent(s,s,BigInteger.ONE,121001),"expire review");
        rejects(()->ProductionPermission.requireCurrent(s,s,BigInteger.ONE,999),"reject clock reversal");
        ProductionPermission.requireCurrent(s,new ProductionPermission.Snapshot(ACCOUNT,s.balance,BigInteger.ONE,1001),BigInteger.ONE,1100);count++;
        rejects(()->ProductionPermission.requireCurrent(s,new ProductionPermission.Snapshot(ACCOUNT,s.balance,s.allowance.add(BigInteger.ONE),1001),BigInteger.ONE,1100),"external allowance increase requires review");
        rejects(()->ProductionPermission.requireCurrent(s,new ProductionPermission.Snapshot(ProductionPermission.POB,s.balance,s.allowance,1001),BigInteger.ONE,1100),"reject changed owner");
        Rpc zero=new Rpc();zero.allowance="0";check(ProductionPermission.read(zero,ACCOUNT,1000).allowance.signum()==0,"protobuf default omission is zero");
        Rpc wrong=new Rpc();wrong.wrongChain=true;rejects(()->ProductionPermission.read(wrong,ACCOUNT,1000),"reject wrong chain before read");check(wrong.reads==0,"no token calls on wrong chain");
        Rpc end=new Rpc();end.changedChain=true;rejects(()->ProductionPermission.read(end,ACCOUNT,1000),"reject chain change during read");
        Rpc target=new Rpc();target.wrongContract=true;rejects(()->ProductionPermission.read(target,ACCOUNT,1000),"reject replacement token or PoB contract");check(target.reads==0,"no calls to replacement contract");
        Rpc malformed=new Rpc();malformed.malformed=true;rejects(()->ProductionPermission.read(malformed,ACCOUNT,1000),"reject explicit null result");
        rejects(()->ProductionPermission.result(new JSONObject().put("result",123)),"reject non-string result");
        String review=ProductionPermission.review(s,ProductionPermission.amount("1000"));
        check(review.contains(ACCOUNT)&&review.contains(ProductionPermission.POB)&&review.contains(ProductionPermission.VHP)&&review.contains("New allowance: 1000 VHP")&&review.contains("REPLACES"),"exact review binds owner, token, spender and replacement amount");
        check(ProductionPermission.review(s,BigInteger.ZERO).contains("New allowance: 0 VHP"),"revoke shows zero");
        if(args.length>0){JSONArray fixtures=new JSONArray();for(String n:new String[]{"0","1","100000000000","18446744073709551614"})fixtures.put(new JSONObject().put("owner",ACCOUNT).put("amount",n).put("operation",ProductionPermission.operation(ACCOUNT,new BigInteger(n))));
            java.nio.file.Files.write(java.nio.file.Paths.get(args[0]),fixtures.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        System.out.println("PASS: "+count+" bounded VHP permission, network, wire-format and review assertions (read-only fixture RPC).");
    }
}
