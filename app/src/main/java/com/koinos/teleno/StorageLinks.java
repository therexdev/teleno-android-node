package com.koinos.teleno;

import java.io.*;
import java.nio.file.*;

/** Recoverable publication of already verified database copies; links live on private storage. */
final class StorageLinks {
    interface Step { void published(int index) throws Exception; }
    static void publish(File[] links,File[] targets,String generation,Step step) throws Exception {
        if(!generation.matches("[a-f0-9]{32}")||links.length!=targets.length)throw new IOException("Invalid storage switch record.");
        for(int i=0;i<links.length;i++) {
            if(!Files.isDirectory(targets[i].toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Verified storage destination is missing.");
            File link=links[i];link.getParentFile().mkdirs();Path path=link.toPath();
            if(Files.isSymbolicLink(path)&&Files.readSymbolicLink(path).equals(targets[i].toPath()))continue;
            if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(path)) {
                File retained=new File(link.getParentFile(),".previous-"+generation+"-"+link.getName());
                if(retained.exists())throw new IOException("Both old database copies exist. Storage recovery needs attention; data was preserved.");
                Files.move(path,retained.toPath(),StandardCopyOption.ATOMIC_MOVE);
            }
            Path temporary=new File(link.getParentFile(),".link-"+generation+"-"+link.getName()).toPath();
            if(Files.exists(temporary,LinkOption.NOFOLLOW_LINKS)) {
                if(!Files.isSymbolicLink(temporary)||!Files.readSymbolicLink(temporary).equals(targets[i].toPath()))throw new IOException("Unexpected storage switch file.");
            }else Files.createSymbolicLink(temporary,targets[i].toPath());
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);step.published(i);
        }
    }
}
