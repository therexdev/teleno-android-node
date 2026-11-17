package com.koinos.teleno;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/** Bitcoin Base58Check as used by Koinos addresses and WIF. No elliptic-curve code. */
final class Base58 {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    static byte[] sha256(byte[] v) throws Exception { return MessageDigest.getInstance("SHA-256").digest(v); }
    static byte[] decode(String value) throws Exception {
        if (value == null || value.length() < 4 || value.length() > 100) throw new IllegalArgumentException("Invalid Base58 length.");
        BigInteger n = BigInteger.ZERO;
        for (char c : value.toCharArray()) { int i = ALPHABET.indexOf(c); if (i < 0) throw new IllegalArgumentException("Invalid Base58 character."); n = n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(i)); }
        byte[] full = n.toByteArray();
        int leading = 0; while (leading < value.length() && value.charAt(leading) == '1') leading++;
        int sign = full.length > 0 && full[0] == 0 ? 1 : 0;
        byte[] out = new byte[leading + full.length - sign];
        System.arraycopy(full, sign, out, leading, full.length - sign);
        return out;
    }
    static byte[] checked(String value) throws Exception {
        byte[] full = decode(value);
        if (full.length < 5) throw new IllegalArgumentException("Invalid checksum.");
        byte[] body = Arrays.copyOf(full, full.length - 4);
        byte[] sum = Arrays.copyOf(sha256(sha256(body)), 4);
        if (!MessageDigest.isEqual(sum, Arrays.copyOfRange(full, full.length - 4, full.length))) throw new IllegalArgumentException("The checksum is invalid. Check every character.");
        return body;
    }
    static String encodeChecked(byte[] body) throws Exception {
        byte[] full = Arrays.copyOf(body, body.length + 4);
        System.arraycopy(sha256(sha256(body)), 0, full, body.length, 4);
        BigInteger n = new BigInteger(1, full); StringBuilder s = new StringBuilder();
        while (n.signum() != 0) { BigInteger[] d = n.divideAndRemainder(BigInteger.valueOf(58)); s.append(ALPHABET.charAt(d[1].intValue())); n = d[0]; }
        for (byte b : full) { if (b != 0) break; s.append('1'); }
        return s.reverse().toString();
    }
    static byte[] address(String text) throws Exception {
        byte[] body = checked(text);
        if (body.length != 21 || body[0] != 0) throw new IllegalArgumentException("Enter a Koinos account address.");
        return decode(text); // Koinos address arguments include version and checksum.
    }
    static byte[] privateKey(String wif) throws Exception {
        byte[] body = checked(wif);
        try {
            if ((body.length != 33 && body.length != 34) || body[0] != (byte) 0x80 || (body.length == 34 && body[33] != 1)) throw new IllegalArgumentException("Enter a valid block-signing WIF key.");
            return Arrays.copyOfRange(body, 1, 33);
        } finally { Arrays.fill(body, (byte) 0); }
    }
    static String wif(byte[] key) throws Exception {
        if (key.length != 32) throw new IllegalArgumentException("A private key must contain 32 bytes.");
        byte[] body = new byte[34]; body[0] = (byte) 0x80; body[33] = 1;
        System.arraycopy(key, 0, body, 1, 32);
        try { return encodeChecked(body); } finally { Arrays.fill(body, (byte) 0); }
    }
}
