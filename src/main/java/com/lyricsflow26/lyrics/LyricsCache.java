package com.lyricsflow26.lyrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Two-level cache: small in-memory LRU plus files on disk. "Not found" results expire after 24 hours. */
public final class LyricsCache {
    /** {@code lrc} is null when the song is known to have no synced lyrics. */
    public record Entry(String lrc) {}

    private static final long NEGATIVE_TTL_MS = Duration.ofHours(24).toMillis();
    private final Path dir;
    private final Map<String, Entry> mem = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> e) { return size() > 64; }
    });

    public LyricsCache(Path dir) {
        this.dir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
    }

    private static String safe(String key) { return key.replaceAll("[^A-Za-z0-9_-]", "_"); }

    public Entry get(String key) {
        Entry m = mem.get(key);
        if (m != null) return m;
        try {
            Path lrc = dir.resolve(safe(key) + ".lrc");
            if (Files.exists(lrc)) {
                Entry e = new Entry(Files.readString(lrc));
                mem.put(key, e);
                return e;
            }
            Path none = dir.resolve(safe(key) + ".none");
            if (Files.exists(none)) {
                if (System.currentTimeMillis() - Files.getLastModifiedTime(none).toMillis() < NEGATIVE_TTL_MS) {
                    Entry e = new Entry(null);
                    mem.put(key, e);
                    return e;
                }
                Files.deleteIfExists(none);
            }
        } catch (IOException ignored) {}
        return null;
    }

    public void put(String key, String lrcOrNull) {
        mem.put(key, new Entry(lrcOrNull));
        try {
            if (lrcOrNull != null) Files.writeString(dir.resolve(safe(key) + ".lrc"), lrcOrNull);
            else Files.writeString(dir.resolve(safe(key) + ".none"), "");
        } catch (IOException ignored) {}
    }
}
