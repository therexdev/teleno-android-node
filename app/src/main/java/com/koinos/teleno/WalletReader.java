package com.koinos.teleno;

import org.json.*;
import java.math.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Read-only, chain-scoped balances and irreversible account history. No signing APIs. */
final class WalletReader {
    interface Rpc { JSONObject call(String method, JSONObject params) throws Exception; }
    static final BigInteger UINT_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    static final int PAGE = 20;
    final Rpc rpc; final String expected;
    WalletReader(Rpc rpc, String expected) { this.rpc=rpc;this.expected=expected; }
    static String b64(byte[] b) { return Base64.getUrlEncoder().encodeToString(b); }
    static byte[] unb64(String s) { return Base64.getUrlDecoder().decode(s.replace('+','-').replace('/','_')); }
    static BigInteger uint(String s) {
        if(s==null||!s.matches("0|[1-9][0-9]{0,19}"))throw new IllegalArgumentException("Invalid unsigned amount.");
        BigInteger n=new BigInteger(s);if(n.compareTo(UINT_MAX)>0)throw new IllegalArgumentException("Amount overflow.");return n;
    }
    static boolean id(String s) { return s!=null&&s.matches("0x1220[0-9a-f]{64}"); }
    // Protobuf JSON omits the default zero sequence on an account's first record.
    static BigInteger sequence(JSONObject row) throws JSONException {return uint(row.has("seq_num")?row.getString("seq_num"):"0");}
    static String amount(String s) { return new BigDecimal(new BigInteger(s),8).stripTrailingZeros().toPlainString(); }
    static String compact(String s) {
        java.text.DecimalFormat f=new java.text.DecimalFormat("#,##0.####",java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()));
        f.setRoundingMode(RoundingMode.DOWN);return f.format(new BigDecimal(new BigInteger(s),8));
    }
    static int percent(String available,String total) {
        BigInteger cap=uint(total);return cap.signum()==0?0:uint(available).multiply(BigInteger.valueOf(100)).divide(cap).min(BigInteger.valueOf(100)).intValue();
    }
    void verify() throws Exception {
        if(!MessageDigest.isEqual(unb64(expected),unb64(rpc.call("chain.get_chain_id",new JSONObject()).getString("chain_id"))))throw new IllegalArgumentException("The data source reported a different network.");
    }
    String contract(String name) throws Exception {
        JSONObject result=rpc.call("chain.invoke_system_call",new JSONObject().put("name","get_contract_address").put("args",b64(Proto.bytes(name.getBytes(StandardCharsets.UTF_8)))));
        byte[] raw=Proto.valueBytes(Proto.valueBytes(unb64(result.getString("value"))));
        if(raw.length!=25)throw new IllegalArgumentException("Invalid token contract.");
        String address=Base58.encodeChecked(Arrays.copyOf(raw,21));
        if(!MessageDigest.isEqual(raw,Base58.address(address)))throw new IllegalArgumentException("Invalid contract checksum.");return address;
    }
    static final class Balance {
        String koin,vhp,mana,source;long at;
        JSONObject json() throws JSONException {return new JSONObject().put("koin",koin).put("vhp",vhp).put("mana",mana).put("source",source).put("at",at);}
        static Balance from(JSONObject j) throws Exception {Balance b=new Balance();b.koin=uint(j.getString("koin")).toString();b.vhp=uint(j.getString("vhp")).toString();b.mana=uint(j.getString("mana")).toString();b.source=j.getString("source");b.at=j.getLong("at");return b;}
    }
    Balance balances(String account,String source) throws Exception {
        byte[] args=Proto.bytes(Base58.address(account));verify();String k=contract("koin"),v=contract("vhp");
        if(k.equals(v))throw new IllegalArgumentException("Token contracts overlap.");
        Balance b=new Balance();b.koin=balance(k,args);b.vhp=balance(v,args);
        b.mana=uint(rpc.call("chain.get_account_rc",new JSONObject().put("account",account)).getString("rc")).toString();
        verify();b.source=source;b.at=System.currentTimeMillis();return b;
    }
    private String balance(String contract,byte[] args) throws Exception {
        JSONObject r=rpc.call("chain.read_contract",new JSONObject().put("contract_id",contract).put("entry_point",1550980247L).put("args",b64(args)));
        return Proto.valueUint64(ProductionPermission.result(r)).toString();
    }
    static final class Entry {
        String id,seq="0",kind="action",title,detail="",koin="0",vhp="0";long time,height;
        boolean reward;
        JSONObject json() throws JSONException {return new JSONObject().put("id",id).put("seq",seq).put("kind",kind).put("title",title).put("detail",detail).put("koin",koin).put("vhp",vhp).put("time",time).put("height",height).put("reward",reward);}
        static Entry from(JSONObject j) throws JSONException {Entry e=new Entry();e.id=j.getString("id");e.seq=j.optString("seq","0");e.kind=j.getString("kind");e.title=j.getString("title");e.detail=j.optString("detail");e.koin=j.optString("koin","0");e.vhp=j.optString("vhp","0");e.time=j.optLong("time");e.height=j.optLong("height");e.reward=j.optBoolean("reward");return e;}
    }
    static final class Page {final List<Entry> entries=new ArrayList<>();String next=null,newest="0",oldest="0";int count;}
    Page history(String account,String cursor) throws Exception {
        Base58.address(account);verify();String k=contract("koin"),v=contract("vhp");if(k.equals(v))throw new IllegalArgumentException("Token contracts overlap.");
        JSONObject params=new JSONObject().put("address",account).put("limit",PAGE).put("ascending",false).put("irreversible",true);
        if(cursor!=null)params.put("seq_num",uint(cursor).toString());
        JSONObject result=rpc.call("account_history.get_account_history",params);
        JSONArray rows=result.has("values")?result.getJSONArray("values"):new JSONArray();
        if(rows.length()>PAGE)throw new IllegalArgumentException("Oversized history page.");
        Page page=new Page();BigInteger min=UINT_MAX,max=BigInteger.ZERO,previous=null;
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);BigInteger seq=sequence(row);
            if((cursor!=null&&seq.compareTo(uint(cursor))>0)||(previous!=null&&seq.compareTo(previous)>=0))throw new IllegalArgumentException("Invalid history sequence.");
            previous=seq;min=min.min(seq);max=max.max(seq);
            Entry entry=normalize(row,account,k,v,expected);if(entry!=null)page.entries.add(entry);
        }
        verify();page.count=rows.length();page.newest=max.toString();page.oldest=rows.length()==0?"0":min.toString();
        if(rows.length()==PAGE&&min.signum()>0)page.next=min.subtract(BigInteger.ONE).toString();return page;
    }
    static Entry normalize(JSONObject row,String account,String koin,String vhp,String chain) throws Exception {
        JSONObject wrapper=row.optJSONObject("trx"),block=row.optJSONObject("block");
        JSONObject object=wrapper==null?block:wrapper.getJSONObject("transaction");if(object==null)throw new IllegalArgumentException("Missing history object.");
        JSONObject receipt=wrapper==null?object.getJSONObject("receipt"):wrapper.getJSONObject("receipt");
        String identifier=object.optString("id",receipt.optString("id"));
        if(!id(identifier)||!identifier.equals(receipt.optString("id",identifier)))throw new IllegalArgumentException("Invalid history receipt.");
        JSONObject header=object.getJSONObject("header");
        if(wrapper!=null&&!MessageDigest.isEqual(unb64(chain),unb64(header.getString("chain_id"))))throw new IllegalArgumentException("Transaction network mismatch.");
        if(receipt.has("reverted")&&!(receipt.get("reverted") instanceof Boolean))throw new IllegalArgumentException("Invalid transaction outcome.");
        boolean failed=receipt.optBoolean("reverted"),own=account.equals(header.optString(wrapper==null?"signer":"payer"))||(wrapper!=null&&account.equals(header.optString("payee")));
        BigInteger kd=BigInteger.ZERO,vd=BigInteger.ZERO;boolean movement=false,mintedKoin=false;
        JSONArray events=receipt.optJSONArray("events");if(events==null)events=new JSONArray();if(events.length()>1024)throw new IllegalArgumentException("Too many token events.");
        Set<String> seen=new HashSet<>();
        for(int i=0;i<events.length();i++) {
            JSONObject event=events.getJSONObject(i);String source=event.optString("source"),name=event.optString("name");
            if(!source.equals(koin)&&!source.equals(vhp))continue;
            if(!name.matches("(?:token|koinos\\.standards\\.kcs4)\\.(?:transfer|mint|burn)_event"))continue;
            String unique=event.has("sequence")?uint(event.get("sequence").toString()).toString():source+":"+name+":"+event.getString("data");
            if(!seen.add(unique))throw new IllegalArgumentException("Duplicate token event.");
            boolean transfer=name.endsWith("transfer_event"),mint=name.endsWith("mint_event");
            Token token=token(unb64(event.getString("data")),transfer);BigInteger delta=BigInteger.ZERO;
            if(transfer) {if(account.equals(token.from))delta=delta.subtract(token.value);if(account.equals(token.to))delta=delta.add(token.value);}
            else if(account.equals(token.from)) {delta=mint?token.value:token.value.negate();if(mint&&source.equals(koin)&&delta.signum()>0)mintedKoin=true;}
            if(delta.signum()!=0)movement=true;
            if(source.equals(koin))kd=kd.add(delta);else vd=vd.add(delta);
        }
        if(!own&&!movement)return null;
        Entry e=new Entry();e.id=identifier;e.seq=sequence(row).toString();e.kind=wrapper==null?"block":"transaction";
        e.reward=wrapper==null&&own&&mintedKoin&&!failed;
        e.koin=failed?"0":kd.toString();e.vhp=failed?"0":vd.toString();
        e.title=e.reward?"Block reward":failed?"Reverted transaction":wrapper==null?"Chain activity":movement?"Wallet activity":"Transaction confirmed";
        if(wrapper==null) {e.height=uint(header.getString("height")).longValueExact();e.time=uint(header.getString("timestamp")).longValueExact();if(e.height<=0||e.time<=0||e.time>System.currentTimeMillis()+60000)throw new IllegalArgumentException("Invalid block metadata.");}
        e.detail=failed?"Irreversible · transaction reverted":"Irreversible on chain";return e;
    }
    static final class Token {String from,to;BigInteger value=BigInteger.ZERO;}
    static Token token(byte[] encoded,boolean transfer) throws Exception {
        if(encoded.length>1024)throw new IllegalArgumentException("Oversized token event.");
        Token t=new Token();Proto.Reader r=new Proto.Reader(encoded);Set<Integer> seen=new HashSet<>();int amountField=transfer?3:2;
        while(r.at<encoded.length) {
            long tag=r.varint();int field=(int)(tag>>>3),wire=(int)(tag&7);
            if(tag<0||tag>26||field<1||field>amountField||!seen.add(field))throw new IllegalArgumentException("Invalid token field.");
            if(field==amountField) {if(wire!=0)throw new IllegalArgumentException("Invalid token amount.");t.value=new BigInteger(Long.toUnsignedString(r.varint()));}
            else {if(wire!=2)throw new IllegalArgumentException("Invalid token address.");byte[] raw=r.data();if(raw.length!=25)throw new IllegalArgumentException("Invalid token address size.");String a=Base58.encodeChecked(Arrays.copyOf(raw,21));if(!MessageDigest.isEqual(raw,Base58.address(a)))throw new IllegalArgumentException("Invalid token checksum.");if(field==1)t.from=a;else t.to=a;}
        }
        if(t.from==null||(transfer&&t.to==null))throw new IllegalArgumentException("Missing token address.");return t;
    }
}
