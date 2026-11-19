package io.anf.games;
import org.json.*;
final class Game {
    final JSONObject json;
    final String id, title, description, art, kind, website, packageName, hash, download, revision;
    final long bytes, expandedBytes;
    final int fileCount;
    Game(JSONObject j) throws JSONException {
        json=j; id=PackageStore.validId(j.getString("id")); title=j.getString("title"); description=j.getString("description");
        art=j.getString("art"); kind=j.getString("kind"); website=j.getString("website");
        packageName=j.optString("packageName"); hash=j.optString("sha256"); download=j.optString("download"); revision=j.optString("revision"); bytes=j.optLong("bytes"); expandedBytes=j.optLong("expandedBytes"); fileCount=j.optInt("fileCount");
        if(expandedBytes<0||expandedBytes>PackageStore.MAX_EXPANDED||fileCount<0||fileCount>100000)throw new JSONException("Invalid package inventory");
        if (!kind.equals("web") && !kind.equals("native")) throw new JSONException("Unknown game kind");
        if (!download.isEmpty() && (!download.startsWith("https://github.com/rnaud/anf/releases/download/") || !hash.matches("[a-f0-9]{64}") || bytes<=0 || bytes>PackageStore.MAX_DOWNLOAD)) throw new JSONException("Invalid package metadata");
    }
}
