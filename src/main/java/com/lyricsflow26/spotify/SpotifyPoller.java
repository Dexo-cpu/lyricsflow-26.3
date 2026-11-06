package com.lyricsflow26.spotify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.core.Sample;
import com.lyricsflow26.core.Status;
import com.lyricsflow26.core.Track;
import com.lyricsflow26.core.LyricsService;
import com.lyricsflow26.lyrics.HttpUtil;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls Spotify's "currently playing" endpoint on its own thread; the game thread never blocks on it. */
public final class SpotifyPoller {
    private static final String URL = "https://api.spotify.com/v1/me/player/currently-playing?additional_types=episode";

    private final LyricsConfig cfg;
    private final SpotifyAuth auth;
    private final LyricsService service;
    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lyricsflow-poller");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;

    public SpotifyPoller(LyricsConfig cfg, SpotifyAuth auth, LyricsService service) {
        this.cfg = cfg;
        this.auth = auth;
        this.service = service;
    }

    public void start() { running = true; ex.schedule(this::tick, 500, TimeUnit.MILLISECONDS); }
    public void stop() { running = false; ex.shutdownNow(); }

    private void tick() {
        long delay;
        try {
            delay = poll();
        } catch (Exception e) {
            service.onNothing(Status.ERROR, "Cannot reach Spotify");
            delay = 4000;
        }
        if (running) {
            try { ex.schedule(this::tick, delay, TimeUnit.MILLISECONDS); }
            catch (RejectedExecutionException ignored) {}
        }
    }

    private long poll() throws IOException {
        if (!cfg.enabled) return 2000; // overlay off: stay quiet
        if (cfg.spotifyClientId == null || cfg.spotifyClientId.isBlank()) {
            service.onNothing(Status.NO_CLIENT_ID, "Add your Spotify Client ID in LyricsFlow settings");
            return 2500;
        }
        String token = auth.validAccessToken();
        if (token == null) {
            service.onNothing(Status.NEEDS_LOGIN, "Press Connect in LyricsFlow settings > Spotify");
            return 2500;
        }
        long t0 = System.nanoTime();
        HttpUtil.Response r = HttpUtil.get(URL, Map.of("Authorization", "Bearer " + token));
        long t1 = System.nanoTime();
        switch (r.code()) {
            case 200:
                return handle(r.body(), t0 + (t1 - t0) / 2); // progress is valid around the request midpoint
            case 204:
                service.onNothing(Status.NOT_PLAYING, "Nothing is playing on Spotify");
                return 2500;
            case 401:
                auth.invalidateAccess();
                return 500;
            case 429: {
                long s = 5;
                try { if (r.retryAfter() != null) s = Long.parseLong(r.retryAfter().trim()); } catch (NumberFormatException ignored) {}
                return s * 1000 + 500;
            }
            default:
                service.onNothing(Status.ERROR, "Spotify error " + r.code());
                return 4000;
        }
    }

    private long handle(String body, long recvNanos) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        boolean playing = bool(o, "is_playing");
        String type = str(o, "currently_playing_type");
        JsonElement itemEl = o.get("item");
        if (itemEl == null || itemEl.isJsonNull() || !"track".equals(type)) {
            service.onNothing(Status.UNSUPPORTED, "Lyrics are only available for music tracks");
            return 2500;
        }
        JsonObject item = itemEl.getAsJsonObject();
        String id = str(item, "id");
        String title = str(item, "name");
        String artist = "";
        if (item.has("artists") && item.get("artists").isJsonArray() && item.getAsJsonArray("artists").size() > 0) {
            artist = str(item.getAsJsonArray("artists").get(0).getAsJsonObject(), "name");
        }
        String album = item.has("album") && item.get("album").isJsonObject() ? str(item.getAsJsonObject("album"), "name") : "";
        long duration = item.has("duration_ms") && !item.get("duration_ms").isJsonNull() ? item.get("duration_ms").getAsLong() : 0;
        long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        if (title == null) title = "";
        if (artist == null) artist = "";
        String key = id != null ? id : "local-" + Integer.toHexString((artist + "|" + title + "|" + duration).hashCode());
        service.onPlayback(new Sample(new Track(key, title, artist, album == null ? "" : album, duration),
                playing, progress, recvNanos));
        return playing ? cfg.pollIntervalMs : Math.max(2500, cfg.pollIntervalMs);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean();
    }
}
