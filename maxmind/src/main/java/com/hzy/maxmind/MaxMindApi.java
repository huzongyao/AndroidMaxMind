package com.hzy.maxmind;


public class MaxMindApi {

    public static native int open(String dbPath);
    public static native void close();
    public static native String lookupIpString(String ipAddr);
    public static native String getLibVersion();
    public static native String getLib2PPVersion();
    public static native String getMetaData();

    static {
        System.loadLibrary("maxmind");
    }
}
