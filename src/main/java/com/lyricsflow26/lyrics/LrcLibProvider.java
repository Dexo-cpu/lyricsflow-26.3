package com.lyricsflow26.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lyricsflow26.core.Track;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/** LRCLIB (https://lrclib.net): free, keyless synced lyrics. Word timing only when the entry is enhanced LRC. */
public final class LrcLibProvider implements LyricsProvider {
    private static final Map<String, String> HEADERS = Map.of("User-Agent", "LyricsFlow26/1.0 (Minecraft Fabric mod)");

    @Override
    public Optional<String> fetchSyncedLrc(Track t) throws IOException {
        long durSec = Math.round(t.durationMs() / 1000.0);
        String url = "https://lrclib.net/api/get?track_name=" + HttpUtil.enc(t.title())
                + "&artist_name=" + HttpUtil.enc(t.artist())
                + (t.album() == null || t.album().isBlank() ? "" : "&album_name=" + HttpUtil.enc(t.album()))
                + "&duration=" + durSec;
        HttpUtil.Response r = HttpUtil.get(url, HEADERS);
        if (r.code() == 200) {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            String synced = str(o, "syncedLyrics");
            if (synced != null && !synced.isBlank()) return Optional.of(synced);
            if (o.has("instrumental") && !o.get("instrumental").isJsonNull() && o.get("instrumental").getAsBoolean()) {
                return Optional.empty();
            }
        } else if (r.code() != 404) {
            throw new IOException("LRCLIB HTTP " + r.code());
        }
        return search(t, durSec);
    }

    private Optional<String> search(Track t, long durSec) throws IOException {
        String url = "https://lrclib.net/api/search?track_name=" + HttpUtil.enc(cleanTitle(t.title()))
                + "&artist_name=" + HttpUtil.enc(t.artist());
        HttpUtil.Response r = HttpUtil.get(url, HEADERS);
        if (r.code() != 200) {
            if (r.code() == 404) return Optional.empty();
            throw new IOException("LRCLIB HTTP " + r.code());
        }
        JsonElement el = JsonParser.parseString(r.body());
        if (!el.isJsonArray()) return Optional.empty();
        JsonArray arr = el.getAsJsonArray();
        String best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String synced = str(o, "syncedLyrics");
            if (synced == null || synced.isBlank()) continue;
            double dur = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : -1;
            double diff = dur < 0 ? 3.9 : Math.abs(dur - durSec);
            if (diff <= 4.0 && diff < bestDiff) { bestDiff = diff; best = synced; }
        }
        return Optional.ofNullable(best); // only accept results whose length matches the track
    }

    private static String cleanTitle(String title) {
        String s = title.replaceAll("\\s*[-\u2013]\\s*(remaster(ed)?|live|mono|stereo|\\d{4}).*$", "");
        s = s.replaceAll("\\s*\\(.*?\\)", "").trim();
        return s.isEmpty() ? title : s;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
