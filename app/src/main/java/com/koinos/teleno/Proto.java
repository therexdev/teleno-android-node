package com.koinos.teleno;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

/** Bounded protobuf wire fields for read-only PoB and token checks. */
final class Proto {
    static byte[] bytes(byte[] value) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); b.write(10);
        int length = value.length; do { b.write((length & 127) | (length > 127 ? 128 : 0)); length >>>= 7; } while (length != 0);
        b.write(value, 0, value.length); return b.toByteArray();
    }
    static final class Reader {
        byte[] b; int at;
        Reader(byte[] value) { if (value.length > 65536) throw new IllegalArgumentException("Oversized protobuf."); b = value; }
        long varint() {
            long out = 0;
            for (int i = 0; i < 10; i++) {
                if (at >= b.length) throw new IllegalArgumentException("Truncated protobuf.");
                int next = b[at++] & 255;
                if (i == 9 && next > 1) throw new IllegalArgumentException("Overflowing protobuf.");
                out |= (long) (next & 127) << (i * 7); if ((next & 128) == 0) return out;
            }
            throw new IllegalArgumentException("Invalid protobuf.");
        }
        byte[] data() { long size = varint(); if (size < 0 || size > b.length - at) throw new IllegalArgumentException("Truncated protobuf."); byte[] v = Arrays.copyOfRange(b, at, at + (int) size); at += (int) size; return v; }
        void skip(int type) { if (type == 0) { varint(); return; } if (type == 2) { data(); return; } int size = type == 1 ? 8 : type == 5 ? 4 : -1; if (size < 0 || b.length - at < size) throw new IllegalArgumentException("Unsupported protobuf field."); at += size; }
    }
    static byte[] valueBytes(byte[] encoded) {
        Reader r = new Reader(encoded); byte[] found = null;
        while (r.at < encoded.length) { long tag = r.varint(); if (tag == 0) throw new IllegalArgumentException("Invalid protobuf tag."); if (tag == 10) { if (found != null) throw new IllegalArgumentException("Duplicate value."); found = r.data(); } else r.skip((int)(tag & 7)); }
        if (found == null) throw new IllegalArgumentException("No value in response."); return found;
    }
    static BigInteger valueUint64(byte[] encoded) {
        Reader r = new Reader(encoded); BigInteger found = null;
        while (r.at < encoded.length) { long tag = r.varint(); if (tag == 0) throw new IllegalArgumentException("Invalid protobuf tag."); if (tag == 8) { if (found != null) throw new IllegalArgumentException("Duplicate value."); found = new BigInteger(Long.toUnsignedString(r.varint())); } else r.skip((int)(tag & 7)); }
        return found == null ? BigInteger.ZERO : found;
    }
}
