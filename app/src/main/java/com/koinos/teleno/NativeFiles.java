package com.koinos.teleno;

final class NativeFiles {
    static { System.loadLibrary("teleno_keys"); }
    static native int lock(String path) throws java.io.IOException;
    static native void unlock(int descriptor);
}
