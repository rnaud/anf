package io.anf.games;

import java.io.*;
import java.net.*;

final class Network {
    interface Progress { void update(long bytes); }
    static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            if (!url.getProtocol().equals("https")) throw new IOException("HTTPS required");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "anf-games-android/0.1");
            int code = c.getResponseCode();
            if (code >= 300 && code <= 399) {
                String location = c.getHeaderField("Location"); c.disconnect();
                if (location == null) throw new IOException("Invalid redirect");
                url = new URL(url, location); continue;
            }
            if (code != 200) { c.disconnect(); throw new IOException("Server returned " + code); }
            return c;
        }
        throw new IOException("Too many redirects");
    }
    static void copy(String url, OutputStream out, long limit, Progress progress) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            long total = 0; byte[] buf = new byte[65536]; int n;
            if (c.getContentLengthLong() > limit) throw new IOException("Download is too large");
            while ((n = in.read(buf)) != -1) {
                PackageStore.checkCancelled(); total += n;
                if (total > limit) throw new IOException("Download exceeds expected size");
                out.write(buf, 0, n); progress.update(total);
            }
        } finally { c.disconnect(); }
    }
}
