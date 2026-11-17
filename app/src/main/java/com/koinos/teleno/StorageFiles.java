package com.koinos.teleno;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Copies only stopped database trees; never follows a link inside a database. */
final class StorageFiles {
    interface Progress { void update(long copied, long total, String stage) throws Exception; }
    private static List<Path> entries(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return result;
        collect(root, result, 0); return result;
    }
    private static void collect(Path p, List<Path> result, int depth) throws IOException {
        if (depth > 32 || Files.isSymbolicLink(p)) throw new IOException("Database contains an unsupported link or directory. Original data was preserved.");
        if (!Files.isDirectory(p) && !Files.isRegularFile(p)) throw new IOException("Unsupported database file.");
        result.add(p);
        if (Files.isDirectory(p)) try (DirectoryStream<Path> items = Files.newDirectoryStream(p)) { for (Path child : items) collect(child, result, depth + 1); }
    }
    static long size(File[] roots) throws IOException {
        long total = 0; for (File f : roots) for (Path p : entries(f.toPath())) if (Files.isRegularFile(p)) total = Math.addExact(total, Files.size(p)); return total;
    }
    static void copy(File[] sources, File[] targets, Progress progress) throws Exception {
        if (sources.length != targets.length) throw new IllegalArgumentException("Mismatched storage roots.");
        long total = size(sources), copied = 0; byte[] buffer = new byte[512 * 1024];
        for (int i = 0; i < sources.length; i++) {
            Path source = sources[i].toPath(), target = targets[i].toPath();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The new database destination must be empty.");
            Files.createDirectories(target);
            for (Path p : entries(source)) {
                progress.update(copied, total, "Copying chain data");
                Path dest = target.resolve(source.relativize(p));
                if (Files.isDirectory(p)) { Files.createDirectories(dest); continue; }
                long length = Files.size(p), modified = Files.getLastModifiedTime(p).toMillis();
                MessageDigest expected = MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(p); FileOutputStream out = new FileOutputStream(dest.toFile())) {
                    int n; while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); expected.update(buffer, 0, n); copied += n; progress.update(copied, total, "Copying chain data"); }
                    out.getFD().sync();
                }
                MessageDigest actual = MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(dest)) { int n; while ((n = in.read(buffer)) != -1) { actual.update(buffer, 0, n); progress.update(copied, total, "Verifying copied data"); } }
                if (length != Files.size(p) || modified != Files.getLastModifiedTime(p).toMillis() || length != Files.size(dest)
                    || !MessageDigest.isEqual(expected.digest(), actual.digest())) throw new IOException("A database file changed or failed verification. Original storage remains selected.");
            }
        }
        progress.update(copied, total, "Copy verified");
    }
    static void deleteTree(File root) throws IOException {
        List<Path> items = entries(root.toPath()); Collections.reverse(items); for (Path p : items) Files.delete(p);
    }
}
