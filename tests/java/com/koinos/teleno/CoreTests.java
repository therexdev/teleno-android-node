package com.koinos.teleno;
import java.util.Arrays;
import java.math.BigInteger;

public final class CoreTests {
    static int assertions;
    interface Throwing { void run() throws Exception; }
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void rejects(Throwing action, String message) throws Exception { boolean rejected = false; try { action.run(); } catch (IllegalArgumentException e) { rejected = true; } check(rejected, message); }
    static String hex(byte[] v) { StringBuilder s = new StringBuilder(); for (byte b : v) s.append(String.format("%02x", b & 255)); return s.toString(); }
    static byte[] unhex(String s) { byte[] v = new byte[s.length()/2]; for(int i=0;i<v.length;i++) v[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return v; }
    public static void main(String[] args) throws Exception {
        // Public Bitcoin/Koinos test scalar = 1, never a funded user key.
        byte[] one = new byte[32]; one[31] = 1;
        check(Base58.wif(one).equals("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73sVHnoWn"), "Compressed WIF vector");
        check(Arrays.equals(Base58.privateKey(Base58.wif(one)), one), "WIF decode round trip");
        check(Base58.encodeChecked(new byte[21]).equals("1111111111111111111114oLvT2"), "Leading-zero address vector");
        check(Base58.address("1111111111111111111114oLvT2").length == 25, "Koinos protobuf address includes checksum");
        rejects(() -> Base58.address("1111111111111111111114oLvT3"), "Bad address checksum");
        rejects(() -> Base58.privateKey("KwDiBf89QgGbjEhKnhXJuH7LrciVrZi3qYjgd9M7rFU73sVHnoWo"), "Bad WIF checksum");
        rejects(() -> Base58.address("0OIl"), "Ambiguous non-Base58 characters");
        check(hex(NativeKeys.publicKey(one)).equals("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"), "JNI secp256k1 generator vector");
        one[31] = 2;
        check(hex(NativeKeys.publicKey(one)).equals("02c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"), "JNI secp256k1 scalar-2 vector");
        rejects(() -> NativeKeys.publicKey(new byte[32]), "Zero private key");
        rejects(() -> NativeKeys.publicKey(new byte[31]), "Wrong key length");
        rejects(() -> NativeKeys.publicKey(unhex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141")), "Key equal to group order");
        for (int n : new int[]{0, 1, 25, 33, 127, 128, 4096}) { byte[] b = new byte[n]; Arrays.fill(b, (byte) 42); check(Arrays.equals(Proto.valueBytes(Proto.bytes(b)), b), "Protobuf bytes length " + n); }
        check(Proto.valueUint64(new byte[0]).signum() == 0, "Default protobuf uint64 zero");
        check(Proto.valueUint64(unhex("08ffffffffffffffffff01")).equals(new BigInteger("18446744073709551615")), "Unsigned uint64 maximum");
        rejects(() -> Proto.valueUint64(unhex("08ffffffffffffffffff02")), "uint64 overflow");
        rejects(() -> Proto.valueBytes(unhex("0a0420")), "Truncated length-delimited field");
        rejects(() -> Proto.valueBytes(unhex("0a000a00")), "Duplicate public-key response");
        rejects(() -> Proto.valueBytes(unhex("00")), "Zero protobuf field tag");
        long t = 1000000;
        Readiness r = new Readiness(); check(r.blocker(t) != null, "No observation");
        r.sample(100, 95, t, t, true); r.peers = 2; r.peersAt = t;
        check(r.blocker(t) != null, "A single fresh head is not advancing");
        r.sample(100, 95, t, t + 1000, true); check(r.blocker(t + 1000) != null, "Repeated head is not advancing");
        r.sample(101, 96, t + 1000, t + 2000, true); check(r.blocker(t + 2000) == null, "Advancing healthy observer");
        check(r.blocker(t + 25000) != null, "Stale RPC sample");
        r.peersAt = t - 100000; check(r.blocker(t + 2000) != null, "Stale peer report");
        r.peersAt = t; r.peers = 0; check(r.blocker(t + 2000) != null, "No peers");
        r.peers = 2; r.correctChain = false; check(r.blocker(t + 2000) != null, "Wrong chain");
        r.correctChain = true; r.headTime = t - 120000; check(r.blocker(t + 2000) != null, "Stale block time");
        r.headTime = t + 120000; check(r.blocker(t + 2000) != null, "Future head time");
        r.headTime = t; r.irreversible = 200; check(r.blocker(t + 2000) != null, "Invalid irreversible height");
        r.irreversible = 95; check(r.blocker(t - 1000) != null, "Clock moved backwards");
        r.sample(99, 94, t + 3000, t + 3000, true); check(r.blocker(t + 3000) != null, "Head regression resets progress");
        r.reset(); check(r.blocker(t + 3000) != null && r.peers == -1, "Restart clears production readiness");
        System.out.println("PASS: " + assertions + " core and host-JNI assertions. Android runtime testing is separate.");
    }
}
