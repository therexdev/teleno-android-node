package com.koinos.teleno;

import java.util.Locale;

/** Exact review text shared by mobile-data download consent and its tests. */
final class DataUse {
    static String size(long bytes){return String.format(Locale.ROOT,"%.2f GB (%.2f GiB; %,d bytes)",bytes/1e9,bytes/1073741824.0,bytes);}
    static String warning(long total,long remaining) {
        if(total<0||remaining<0||remaining>total)throw new IllegalArgumentException("Invalid download size.");
        return "You are using mobile data or a metered connection. This is a very large download."
            +"\n\nFull chain snapshot: "+size(total)+"\nEstimated remaining: "+size(remaining)
            +"\n\nThis can use up your data allowance, trigger speed limits, or incur carrier/roaming charges. Unlimited plans can still have fair-use limits."
            +" Retries, metadata and file verification failures may increase transferred data; saved files that fail verification may need downloading again."
            +"\n\nAllowing this download does not enable ongoing mobile-data mining or node sync. That needs its own confirmation when starting the node.";
    }
}
