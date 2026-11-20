package com.koinos.teleno;

import android.content.Context;
import android.net.*;
import java.io.IOException;

final class NetworkPolicy {
    static boolean costly(NetworkCapabilities nc) {
        return nc!=null&&(!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            ||(!nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)));
    }
    static NetworkCapabilities capabilities(Context c) {
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);return cm.getNetworkCapabilities(cm.getActiveNetwork());
    }
    static boolean needsConsent(Context c){return costly(capabilities(c));}
    static Network downloadNetwork(Context c,boolean allowMobile)throws IOException {
        ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network network=cm.getActiveNetwork();
        NetworkCapabilities nc=cm.getNetworkCapabilities(network);
        if(network==null||nc==null||!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))throw new IOException("Download paused: no internet connection. Saved files are kept.");
        if(costly(nc)&&!allowMobile)throw new IOException("Download paused before using mobile or metered data. Resume and review the download size to allow data use, or reconnect Wi-Fi. Saved files are kept.");
        return network;
    }
}
