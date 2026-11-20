package com.koinos.teleno;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Small, read-only RocksDB identity checks; never opens or changes the database. */
final class ChainData {
    static boolean usable(File db) {
        try {
            if(!Files.isDirectory(db.toPath(),LinkOption.NOFOLLOW_LINKS))return false;
            Path current=new File(db,"CURRENT").toPath();
            if(!Files.isRegularFile(current,LinkOption.NOFOLLOW_LINKS)||Files.size(current)>256)return false;
            String name=new String(Files.readAllBytes(current),StandardCharsets.US_ASCII);
            return name.matches("MANIFEST-[0-9]+\\n")&&Files.isRegularFile(new File(db,name.trim()).toPath(),LinkOption.NOFOLLOW_LINKS);
        }catch(Exception e){return false;}
    }
    static void protectActive(File active,File removal)throws IOException {
        Path current=active.getCanonicalFile().toPath(),old=removal.getCanonicalFile().toPath();
        if(current.startsWith(old)||old.startsWith(current))throw new IOException("Refusing to delete data used by the active chain.");
    }
}
