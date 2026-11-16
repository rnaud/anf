package io.anf.games;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Versioned installs: verification and extraction finish before the current pointer changes. */
public final class PackageStore {
    public static final long MAX_DOWNLOAD = 1500L * 1024 * 1024;
    public static final long MAX_EXPANDED = 3L * 1024 * 1024 * 1024;
    private final File root;
    public PackageStore(File root) { this.root = root; }
    public static String validId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid game ID");
        return id;
    }
    private File game(String id) { return new File(root, validId(id)); }
    public File current(String id) {
        try {
            String version = new String(Files.readAllBytes(new File(game(id), "current").toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!version.matches("[a-f0-9]{64}")) return null;
            File dir = new File(game(id), version);
            return new File(dir, "index.html").isFile() ? dir : null;
        } catch (IOException e) { return null; }
    }
    public static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) != -1) { checkCancelled(); md.update(buf, 0, n); }
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled");
    }
    public void install(String id, File zip, String hash) throws Exception {
        if (!hash.matches("[a-f0-9]{64}") || !sha256(zip).equals(hash)) throw new IOException("Package checksum does not match");
        File dir = game(id); dir.mkdirs();
        File staging = new File(dir, "staging"); delete(staging); staging.mkdirs();
        try {
            extract(zip, staging, MAX_EXPANDED);
            if (!new File(staging, "index.html").isFile()) throw new IOException("Package is missing index.html");
            checkCancelled();
            File version = new File(dir, hash);
            if (!version.exists()) Files.move(staging.toPath(), version.toPath());
            else delete(staging);
            File pointer = new File(dir, "current.tmp");
            Files.write(pointer.toPath(), hash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.move(pointer.toPath(), new File(dir, "current").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) if (f.isDirectory() && !f.equals(version)) delete(f);
        } finally { delete(staging); }
    }
    static void extract(File zip, File target, long limit) throws IOException {
        String base = target.getCanonicalPath() + File.separator;
        long total = 0; int entries = 0;
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry; byte[] buf = new byte[65536];
            while ((entry = in.getNextEntry()) != null) {
                checkCancelled();
                if (++entries > 100000) throw new IOException("Too many package files");
                File file = new File(target, entry.getName());
                String path = file.getCanonicalPath();
                if (entry.getName().contains("\\") || entry.getName().startsWith("/") || !path.startsWith(base) || !names.add(path)) throw new IOException("Unsafe package path");
                if (entry.isDirectory()) { file.mkdirs(); continue; }
                file.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(file)) {
                    int n; while ((n = in.read(buf)) != -1) {
                        checkCancelled(); total += n;
                        if (total > limit) throw new IOException("Package expands beyond the storage limit");
                        out.write(buf, 0, n);
                    }
                }
            }
        }
    }
    public void remove(String id) throws IOException { delete(game(id)); }
    static void delete(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        Files.deleteIfExists(file.toPath());
    }
}
