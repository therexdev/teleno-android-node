package com.koinos.teleno;
final class NativeKeys {
    static { System.loadLibrary("teleno_keys"); }
    static native byte[] publicKey(byte[] secret);
}
